package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.example.core.document.CellHorizontalAlignment
import com.example.core.document.PresentationSlideModel
import com.example.core.document.ShapeFill
import com.example.core.document.ShapePreset
import com.example.core.document.SlideElement
import com.example.core.document.SlideParagraph
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.min

/**
 * Slide Image LRU Cache to prevent re-decoding compressed PNG/JPEG/WebP images on every frame.
 */
object SlideMediaCache {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
    private val cacheSize = maxMemory / 8 // 1/8th of available heap

    private val lruCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    fun getOrDecode(zipFile: File, entryPath: String, targetWidth: Int, targetHeight: Int): Bitmap? {
        val cacheKey = "${zipFile.absolutePath}::$entryPath"
        val cached = lruCache.get(cacheKey)
        if (cached != null && !cached.isRecycled) return cached

        return try {
            ZipFile(zipFile).use { zip ->
                val entry = zip.getEntry(entryPath) ?: return null
                zip.getInputStream(entry).use { stream ->
                    val bytes = stream.readBytes()

                    // Sample size calculation
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

                    var sample = 1
                    val reqW = targetWidth.coerceAtLeast(1)
                    val reqH = targetHeight.coerceAtLeast(1)
                    while (options.outWidth / (sample * 2) >= reqW && options.outHeight / (sample * 2) >= reqH) {
                        sample *= 2
                    }

                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
                    if (bitmap != null) {
                        lruCache.put(cacheKey, bitmap)
                    }
                    bitmap
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Custom Canvas renderer that draws a complete presentation slide with geometry, shapes,
 * rich text, tables, and images scaled to fit the viewport.
 */
@Composable
fun SlideCanvas(
    slide: PresentationSlideModel,
    slideWidthEmu: Long,
    slideHeightEmu: Long,
    presentationFile: File?,
    modifier: Modifier = Modifier
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary

    val shapeFillPaint = remember { Paint().apply { isAntiAlias = true; style = Paint.Style.FILL } }
    val shapeStrokePaint = remember { Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE } }
    val textPaint = remember { TextPaint().apply { isAntiAlias = true } }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewWidth = constraints.maxWidth.toFloat()
        val viewHeight = constraints.maxHeight.toFloat()

        if (viewWidth <= 0f || viewHeight <= 0f || slideWidthEmu <= 0L || slideHeightEmu <= 0L) return@BoxWithConstraints

        // Calculate aspect ratio scale to fit inside container
        val scaleX = viewWidth / slideWidthEmu.toFloat()
        val scaleY = viewHeight / slideHeightEmu.toFloat()
        val scale = min(scaleX, scaleY)

        val renderedSlideWidth = slideWidthEmu * scale
        val renderedSlideHeight = slideHeightEmu * scale
        val offsetX = (viewWidth - renderedSlideWidth) / 2f
        val offsetY = (viewHeight - renderedSlideHeight) / 2f

        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvas = drawContext.canvas.nativeCanvas

            // 1. Draw subtle slide drop shadow & slide paper surface
            drawRect(
                color = Color.Black.copy(alpha = 0.15f),
                topLeft = Offset(offsetX + 4f, offsetY + 6f),
                size = Size(renderedSlideWidth, renderedSlideHeight)
            )

            // Background Fill
            val bgFill = slide.backgroundFill
            val slideBgColor = if (bgFill is ShapeFill.Solid) Color(bgFill.colorRgb) else Color.White
            drawRect(
                color = slideBgColor,
                topLeft = Offset(offsetX, offsetY),
                size = Size(renderedSlideWidth, renderedSlideHeight)
            )

            // 2. Render all slide elements in document z-order
            drawIntoCanvas {
                canvas.save()
                canvas.translate(offsetX, offsetY)
                canvas.scale(scale, scale)

                // Clip to slide bounds
                canvas.clipRect(0f, 0f, slideWidthEmu.toFloat(), slideHeightEmu.toFloat())

                for (element in slide.elements) {
                    renderSlideElement(
                        canvas = canvas,
                        element = element,
                        presentationFile = presentationFile,
                        shapeFillPaint = shapeFillPaint,
                        shapeStrokePaint = shapeStrokePaint,
                        textPaint = textPaint,
                        onSurfaceColor = onSurface.toArgb()
                    )
                }

                canvas.restore()
            }
        }
    }
}

private fun renderSlideElement(
    canvas: Canvas,
    element: SlideElement,
    presentationFile: File?,
    shapeFillPaint: Paint,
    shapeStrokePaint: Paint,
    textPaint: TextPaint,
    onSurfaceColor: Int
) {
    val bounds = element.bounds
    if (!bounds.isValid) return

    val x = bounds.xEmu.toFloat()
    val y = bounds.yEmu.toFloat()
    val w = bounds.widthEmu.toFloat()
    val h = bounds.heightEmu.toFloat()

    canvas.save()

    // Apply rotation and flips around center
    if (bounds.rotationDegrees != 0f || bounds.flipH || bounds.flipV) {
        val cx = x + (w / 2f)
        val cy = y + (h / 2f)
        canvas.translate(cx, cy)
        if (bounds.rotationDegrees != 0f) canvas.rotate(bounds.rotationDegrees)
        val sx = if (bounds.flipH) -1f else 1f
        val sy = if (bounds.flipV) -1f else 1f
        canvas.scale(sx, sy)
        canvas.translate(-cx, -cy)
    }

    when (element) {
        is SlideElement.Shape -> {
            renderShape(canvas, element, x, y, w, h, shapeFillPaint, shapeStrokePaint, textPaint, onSurfaceColor)
        }
        is SlideElement.Picture -> {
            if (presentationFile != null) {
                val bitmap = SlideMediaCache.getOrDecode(
                    zipFile = presentationFile,
                    entryPath = element.mediaEntryPath,
                    targetWidth = (w / 12700f).toInt().coerceAtLeast(32),
                    targetHeight = (h / 12700f).toInt().coerceAtLeast(32)
                )
                if (bitmap != null && !bitmap.isRecycled) {
                    val destRect = RectF(x, y, x + w, y + h)
                    canvas.drawBitmap(bitmap, null, destRect, null)
                }
            }
        }
        is SlideElement.Table -> {
            renderTable(canvas, element, x, y, w, h, shapeStrokePaint, textPaint, onSurfaceColor)
        }
        is SlideElement.Group -> {
            for (child in element.children) {
                renderSlideElement(canvas, child, presentationFile, shapeFillPaint, shapeStrokePaint, textPaint, onSurfaceColor)
            }
        }
        else -> {}
    }

    canvas.restore()
}

private fun renderShape(
    canvas: Canvas,
    shape: SlideElement.Shape,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    fillPaint: Paint,
    strokePaint: Paint,
    textPaint: TextPaint,
    onSurfaceColor: Int
) {
    val path = buildPresetPath(shape.preset, x, y, w, h)

    // Fill
    val fill = shape.fill
    if (fill is ShapeFill.Solid) {
        fillPaint.color = fill.colorRgb
        canvas.drawPath(path, fillPaint)
    }

    // Line / Border
    val line = shape.line
    if (line != null) {
        strokePaint.color = line.colorRgb
        strokePaint.strokeWidth = line.widthEmu.toFloat().coerceAtLeast(10000f)
        canvas.drawPath(path, strokePaint)
    }

    // Text in shape
    if (shape.paragraphs.isNotEmpty()) {
        renderParagraphs(canvas, shape.paragraphs, x, y, w, h, textPaint, onSurfaceColor)
    }
}

private fun buildPresetPath(preset: ShapePreset, x: Float, y: Float, w: Float, h: Float): Path {
    val path = Path()
    when (preset) {
        ShapePreset.RECT, ShapePreset.UNKNOWN_FALLBACK -> {
            path.addRect(x, y, x + w, y + h, Path.Direction.CW)
        }
        ShapePreset.ROUND_RECT -> {
            val radius = min(w, h) * 0.15f
            path.addRoundRect(RectF(x, y, x + w, y + h), radius, radius, Path.Direction.CW)
        }
        ShapePreset.ELLIPSE -> {
            path.addOval(RectF(x, y, x + w, y + h), Path.Direction.CW)
        }
        ShapePreset.TRIANGLE -> {
            path.moveTo(x + (w / 2f), y)
            path.lineTo(x + w, y + h)
            path.lineTo(x, y + h)
            path.close()
        }
        ShapePreset.RT_TRIANGLE -> {
            path.moveTo(x, y)
            path.lineTo(x + w, y + h)
            path.lineTo(x, y + h)
            path.close()
        }
        ShapePreset.DIAMOND -> {
            path.moveTo(x + (w / 2f), y)
            path.lineTo(x + w, y + (h / 2f))
            path.lineTo(x + (w / 2f), y + h)
            path.lineTo(x, y + (h / 2f))
            path.close()
        }
        ShapePreset.PARALLELOGRAM -> {
            val skew = w * 0.2f
            path.moveTo(x + skew, y)
            path.lineTo(x + w, y)
            path.lineTo(x + w - skew, y + h)
            path.lineTo(x, y + h)
            path.close()
        }
        ShapePreset.RIGHT_ARROW -> {
            val arrowHead = w * 0.35f
            val shaftH = h * 0.4f
            val shaftY = y + (h - shaftH) / 2f
            path.moveTo(x, shaftY)
            path.lineTo(x + w - arrowHead, shaftY)
            path.lineTo(x + w - arrowHead, y)
            path.lineTo(x + w, y + (h / 2f))
            path.lineTo(x + w - arrowHead, y + h)
            path.lineTo(x + w - arrowHead, shaftY + shaftH)
            path.lineTo(x, shaftY + shaftH)
            path.close()
        }
        ShapePreset.STAR_5 -> {
            val cx = x + (w / 2f)
            val cy = y + (h / 2f)
            val outerR = min(w, h) / 2f
            val innerR = outerR * 0.45f
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) outerR else innerR
                val angle = Math.toRadians((i * 36 - 90).toDouble())
                val px = cx + (r * Math.cos(angle)).toFloat()
                val py = cy + (r * Math.sin(angle)).toFloat()
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
        }
        ShapePreset.HEXAGON -> {
            val q = w * 0.25f
            path.moveTo(x + q, y)
            path.lineTo(x + w - q, y)
            path.lineTo(x + w, y + (h / 2f))
            path.lineTo(x + w - q, y + h)
            path.lineTo(x + q, y + h)
            path.lineTo(x, y + (h / 2f))
            path.close()
        }
        else -> {
            path.addRect(x, y, x + w, y + h, Path.Direction.CW)
        }
    }
    return path
}

private fun renderParagraphs(
    canvas: Canvas,
    paragraphs: List<SlideParagraph>,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    textPaint: TextPaint,
    defaultColor: Int
) {
    var curY = y + (h * 0.05f)
    val padX = w * 0.05f
    val availWidth = (w - (padX * 2)).coerceAtLeast(1000f)

    for (p in paragraphs) {
        val bulletPrefix = if (p.bulletChar != null) "${p.bulletChar} " else ""
        val fullText = bulletPrefix + p.fullText
        if (fullText.isBlank()) continue

        val firstRun = p.runs.firstOrNull()
        val fontSizePt = firstRun?.fontSizePt ?: 18f
        // Convert pt to EMU dimensions (1 pt = 12700 EMU)
        textPaint.textSize = fontSizePt * 12700f * 0.85f
        textPaint.color = firstRun?.colorRgb ?: defaultColor
        val isBold = firstRun?.isBold == true
        val isItalic = firstRun?.isItalic == true
        textPaint.typeface = when {
            isBold && isItalic -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
            isBold -> Typeface.DEFAULT_BOLD
            isItalic -> Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            else -> Typeface.DEFAULT
        }

        val align = when (p.alignment) {
            CellHorizontalAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
            CellHorizontalAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_NORMAL
        }

        val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(fullText, 0, fullText.length, textPaint, availWidth.toInt())
                .setAlignment(align)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(fullText, textPaint, availWidth.toInt(), align, 1f, 0f, false)
        }

        canvas.save()
        canvas.translate(x + padX, curY)
        layout.draw(canvas)
        canvas.restore()

        curY += layout.height + (fontSizePt * 12700f * 0.35f)
        if (curY > y + h) break
    }
}

private fun renderTable(
    canvas: Canvas,
    table: SlideElement.Table,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    strokePaint: Paint,
    textPaint: TextPaint,
    onSurfaceColor: Int
) {
    val colWidths = table.colWidthsEmu.map { it.toFloat() }.ifEmpty { listOf(w) }
    var curY = y

    strokePaint.color = 0xFFCCCCCC.toInt()
    strokePaint.strokeWidth = 12700f

    for (row in table.rows) {
        val rowH = row.heightEmu.toFloat()
        var curX = x

        for ((colIdx, cell) in row.cells.withIndex()) {
            val cellW = colWidths.getOrElse(colIdx) { w / row.cells.size }
            // Draw cell border
            canvas.drawRect(curX, curY, curX + cellW, curY + rowH, strokePaint)

            // Draw cell text
            if (cell.text.isNotBlank()) {
                textPaint.textSize = 14f * 12700f
                textPaint.color = onSurfaceColor
                textPaint.typeface = Typeface.DEFAULT
                canvas.save()
                canvas.clipRect(curX + 10000f, curY + 10000f, curX + cellW - 10000f, curY + rowH - 10000f)
                canvas.drawText(cell.text, curX + 20000f, curY + (rowH / 2f) + (textPaint.textSize / 3f), textPaint)
                canvas.restore()
            }
            curX += cellW
        }
        curY += rowH
    }
}
