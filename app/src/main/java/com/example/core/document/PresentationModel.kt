package com.example.core.document

/**
 * Clean, immutable presentation data models without Android UI dependencies.
 * Pure Kotlin data structures suitable for JVM unit testing and Canvas rendering.
 * All spatial coordinates are stored in standard OpenXML EMUs (English Metric Units).
 * 914,400 EMUs = 1 inch, 36,000 EMUs = 1 mm, 12,700 EMUs = 1 pt.
 */

// ---------------------------------------------------------------------------------------------
// 1. Color Transforms & Theme Resolution
// ---------------------------------------------------------------------------------------------

data class ColorTransform(
    val lumMod: Float? = null,   // Percentage 0.0 .. 1.0 (luminance modulation)
    val lumOff: Float? = null,   // Percentage 0.0 .. 1.0 (luminance offset)
    val tint: Float? = null,     // Percentage 0.0 .. 1.0
    val shade: Float? = null,    // Percentage 0.0 .. 1.0
    val alpha: Float? = null     // Percentage 0.0 .. 1.0
)

object ColorMath {
    /**
     * Applies OpenXML color transformations (lumMod, lumOff, tint, shade, alpha) to an sRGB color.
     */
    fun applyTransforms(baseArgb: Int, transform: ColorTransform): Int {
        var a = (baseArgb ushr 24) and 0xFF
        var r = (baseArgb ushr 16) and 0xFF
        var g = (baseArgb ushr 8) and 0xFF
        var b = baseArgb and 0xFF

        // Alpha transform
        if (transform.alpha != null) {
            a = (a * transform.alpha.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
        }

        // Tint / Shade
        if (transform.tint != null) {
            val t = transform.tint.coerceIn(0f, 1f)
            r = (r * t + 255 * (1f - t)).toInt().coerceIn(0, 255)
            g = (g * t + 255 * (1f - t)).toInt().coerceIn(0, 255)
            b = (b * t + 255 * (1f - t)).toInt().coerceIn(0, 255)
        }
        if (transform.shade != null) {
            val s = transform.shade.coerceIn(0f, 1f)
            r = (r * s).toInt().coerceIn(0, 255)
            g = (g * s).toInt().coerceIn(0, 255)
            b = (b * s).toInt().coerceIn(0, 255)
        }

        // HSL Luminance modulation / offset
        if (transform.lumMod != null || transform.lumOff != null) {
            val hsl = rgbToHsl(r, g, b)
            var l = hsl[2]
            if (transform.lumMod != null) {
                l *= transform.lumMod.coerceIn(0f, 1f)
            }
            if (transform.lumOff != null) {
                l += transform.lumOff.coerceIn(0f, 1f)
            }
            hsl[2] = l.coerceIn(0f, 1f)
            val rgb = hslToRgb(hsl[0], hsl[1], hsl[2])
            r = rgb[0]
            g = rgb[1]
            b = rgb[2]
        }

        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun rgbToHsl(r: Int, g: Int, b: Int): FloatArray {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val max = maxOf(rf, gf, bf)
        val min = minOf(rf, gf, bf)
        var h: Float
        val s: Float
        val l = (max + min) / 2f

        if (max == min) {
            h = 0f
            s = 0f
        } else {
            val d = max - min
            s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
            h = when (max) {
                rf -> (gf - bf) / d + (if (gf < bf) 6f else 0f)
                gf -> (bf - rf) / d + 2f
                else -> (rf - gf) / d + 4f
            }
            h /= 6f
        }
        return floatArrayOf(h, s, l)
    }

    private fun hslToRgb(h: Float, s: Float, l: Float): IntArray {
        val r: Float
        val g: Float
        val b: Float
        if (s == 0f) {
            r = l
            g = l
            b = l
        } else {
            val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
            val p = 2f * l - q
            r = hueToRgb(p, q, h + 1f / 3f)
            g = hueToRgb(p, q, h)
            b = hueToRgb(p, q, h - 1f / 3f)
        }
        return intArrayOf((r * 255f).toInt().coerceIn(0, 255), (g * 255f).toInt().coerceIn(0, 255), (b * 255f).toInt().coerceIn(0, 255))
    }

    private fun hueToRgb(p: Float, q: Float, tInput: Float): Float {
        var t = tInput
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 1f / 2f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 2. Geometry & Transforms
// ---------------------------------------------------------------------------------------------

data class ShapeBounds(
    val xEmu: Long = 0L,
    val yEmu: Long = 0L,
    val widthEmu: Long = 0L,
    val heightEmu: Long = 0L,
    val rotationDegrees: Float = 0f,
    val flipH: Boolean = false,
    val flipV: Boolean = false
) {
    val isValid: Boolean get() = widthEmu > 0L && heightEmu > 0L
}

enum class ShapePreset {
    RECT,
    ROUND_RECT,
    ELLIPSE,
    TRIANGLE,
    RT_TRIANGLE,
    DIAMOND,
    PARALLELOGRAM,
    LINE,
    STRAIGHT_CONNECTOR_1,
    RIGHT_ARROW,
    LEFT_ARROW,
    UP_ARROW,
    DOWN_ARROW,
    PENTAGON,
    HEXAGON,
    STAR_5,
    CHEVRON,
    CUSTOM_PATH,
    UNKNOWN_FALLBACK
}

sealed interface PathCommand {
    data class MoveTo(val x: Float, val y: Float) : PathCommand
    data class LineTo(val x: Float, val y: Float) : PathCommand
    data class CubicBezierTo(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val x3: Float, val y3: Float) : PathCommand
    data class QuadBezierTo(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : PathCommand
    object Close : PathCommand
}

// ---------------------------------------------------------------------------------------------
// 3. Fills, Lines & Backgrounds
// ---------------------------------------------------------------------------------------------

data class GradientStop(
    val position: Float, // 0.0 .. 1.0
    val colorRgb: Int
)

sealed interface ShapeFill {
    object None : ShapeFill
    data class Solid(val colorRgb: Int) : ShapeFill
    data class Gradient(
        val stops: List<GradientStop>,
        val angleDegrees: Float = 90f,
        val isRadial: Boolean = false
    ) : ShapeFill
    data class Blip(val entryPath: String) : ShapeFill
}

data class ShapeLine(
    val widthEmu: Long = 12700L, // Default ~1 pt
    val colorRgb: Int = 0xFF000000.toInt(),
    val isDashed: Boolean = false
)

// ---------------------------------------------------------------------------------------------
// 4. Rich Text & Paragraphs
// ---------------------------------------------------------------------------------------------

data class SlideTextRun(
    val text: String,
    val fontSizePt: Float = 18f,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isStrike: Boolean = false,
    val colorRgb: Int? = null,
    val typefaceName: String? = null,
    val linkUrl: String? = null
)

data class SlideParagraph(
    val runs: List<SlideTextRun>,
    val alignment: CellHorizontalAlignment = CellHorizontalAlignment.LEFT,
    val indentLevel: Int = 0,
    val bulletChar: String? = null,
    val isNumberedBullet: Boolean = false,
    val bulletNumber: Int = 1,
    val lineSpacingMultiplier: Float = 1.0f
) {
    val fullText: String get() = runs.joinToString("") { it.text }
}

// ---------------------------------------------------------------------------------------------
// 5. Tables & Placeholders
// ---------------------------------------------------------------------------------------------

data class SlideTableCell(
    val text: String,
    val runs: List<SlideTextRun> = emptyList(),
    val fillRgb: Int? = null,
    val colSpan: Int = 1,
    val rowSpan: Int = 1
)

data class SlideTableRow(
    val cells: List<SlideTableCell>,
    val heightEmu: Long
)

// ---------------------------------------------------------------------------------------------
// 6. Presentation Shapes & Slides
// ---------------------------------------------------------------------------------------------

sealed interface SlideElement {
    val bounds: ShapeBounds

    data class Shape(
        override val bounds: ShapeBounds,
        val preset: ShapePreset = ShapePreset.RECT,
        val customPath: List<PathCommand> = emptyList(),
        val fill: ShapeFill = ShapeFill.None,
        val line: ShapeLine? = null,
        val paragraphs: List<SlideParagraph> = emptyList()
    ) : SlideElement

    data class Picture(
        override val bounds: ShapeBounds,
        val mediaEntryPath: String,
        val cropRect: FloatArray? = null // left, top, right, bottom (0.0 .. 1.0)
    ) : SlideElement

    data class Table(
        override val bounds: ShapeBounds,
        val rows: List<SlideTableRow>,
        val colWidthsEmu: List<Long>
    ) : SlideElement

    data class Placeholder(
        override val bounds: ShapeBounds,
        val placeholderType: String,
        val label: String
    ) : SlideElement

    data class Group(
        override val bounds: ShapeBounds,
        val children: List<SlideElement>
    ) : SlideElement
}

data class PresentationSlideModel(
    val slideNumber: Int,
    val title: String?,
    val backgroundFill: ShapeFill = ShapeFill.None,
    val elements: List<SlideElement>,
    val speakerNotes: String? = null,
    val isHidden: Boolean = false
) {
    /** Combined plain text representation of all text runs on the slide for search & TalkBack */
    val plainTextSummary: String by lazy {
        val sb = StringBuilder()
        if (!title.isNullOrBlank()) {
            sb.append(title).append("\n")
        }
        for (el in elements) {
            extractText(el, sb)
        }
        sb.toString().trim()
    }

    private fun extractText(el: SlideElement, sb: StringBuilder) {
        when (el) {
            is SlideElement.Shape -> {
                for (p in el.paragraphs) {
                    val pText = p.fullText
                    if (pText.isNotBlank()) sb.append(pText).append("\n")
                }
            }
            is SlideElement.Table -> {
                for (row in el.rows) {
                    for (cell in row.cells) {
                        if (cell.text.isNotBlank()) sb.append(cell.text).append(" ")
                    }
                    sb.append("\n")
                }
            }
            is SlideElement.Group -> {
                for (child in el.children) extractText(child, sb)
            }
            else -> {}
        }
    }
}

data class PresentationDocumentModel(
    val title: String,
    val slideWidthEmu: Long = 9144000L,  // 10 inches default (16:9 or 4:3)
    val slideHeightEmu: Long = 5143500L, // 5.625 inches (16:9 default: 9144000 x 5143500)
    val slides: List<PresentationSlideModel>
)
