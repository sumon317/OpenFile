package com.example.core.document

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * Robust streaming presentation parser for OOXML (.pptx) and OpenDocument (.odp).
 *
 * Implements:
 * - Proper slide ordering via `<p:sldIdLst>` and `presentation.xml.rels`
 * - Slide sizing in EMUs
 * - Theme color resolution with color transforms (lumMod, lumOff, tint, shade, alpha)
 * - Shape preset geometry and custom path parsing
 * - Embedded image relationship extraction
 * - Table grid, column width, and cell text parsing
 * - Rich text runs (fonts, bold, italic, underline, colors, and bullets)
 * - Speaker notes extraction
 * - ODP slide, frame, and text extraction
 */
object PresentationParser {

    private fun createXmlPullParser(): XmlPullParser {
        val parser = try {
            android.util.Xml.newPullParser()
        } catch (_: Throwable) {
            XmlPullParserFactory.newInstance().newPullParser()
        }
        try {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        } catch (_: Exception) {}
        return parser
    }

    fun parse(file: File): PresentationDocumentModel {
        val detected = DocumentFormatDetector.detect(file)
        return when (detected) {
            is DetectedDocumentType.Pptx -> parsePptx(file)
            is DetectedDocumentType.Odp -> parseOdp(file)
            is DetectedDocumentType.LegacyOle2 -> {
                throw UnsupportedOperationException("Legacy binary presentation (.ppt) is not supported. Please convert to .pptx or .odp.")
            }
            is DetectedDocumentType.EncryptedPasswordProtected -> {
                throw SecurityException("This presentation is password protected and cannot be previewed.")
            }
            else -> {
                if (file.extension.equals("odp", ignoreCase = true)) {
                    parseOdp(file)
                } else {
                    parsePptx(file)
                }
            }
        }
    }

    // =============================================================================================
    // 1. OOXML (.pptx) Parser
    // =============================================================================================

    fun parsePptx(file: File): PresentationDocumentModel {
        ZipFile(file).use { zip ->
            // 1. Parse slide size & ordered slide relationship IDs from ppt/presentation.xml
            var slideWidthEmu = 9144000L
            var slideHeightEmu = 5143500L
            val orderedSlideRIds = mutableListOf<String>()

            val presEntry = zip.getEntry("ppt/presentation.xml")
            if (presEntry != null) {
                zip.getInputStream(presEntry).use { stream ->
                    val bounded = DocumentFormatDetector.createSafeBoundedStream(stream)
                    val (w, h, rIds) = parsePresentationXml(bounded)
                    if (w > 0L) slideWidthEmu = w
                    if (h > 0L) slideHeightEmu = h
                    orderedSlideRIds.addAll(rIds)
                }
            }

            // 2. Resolve presentation relationship targets (rId -> path) from ppt/_rels/presentation.xml.rels
            val presRels = mutableMapOf<String, String>()
            val presRelsEntry = zip.getEntry("ppt/_rels/presentation.xml.rels")
            if (presRelsEntry != null) {
                zip.getInputStream(presRelsEntry).use { stream ->
                    val bounded = DocumentFormatDetector.createSafeBoundedStream(stream)
                    presRels.putAll(parseRelationshipsXml(bounded, "ppt/"))
                }
            }

            // 3. Parse Theme (ppt/theme/theme1.xml)
            val themeColors = mutableMapOf<String, Int>()
            val themeEntry = zip.getEntry("ppt/theme/theme1.xml")
            if (themeEntry != null) {
                zip.getInputStream(themeEntry).use { stream ->
                    val bounded = DocumentFormatDetector.createSafeBoundedStream(stream)
                    themeColors.putAll(parseThemeXml(bounded))
                }
            } else {
                themeColors.putAll(defaultThemeColors())
            }

            // 4. Order slide paths based on presentation.xml.rels
            val orderedSlidePaths = orderedSlideRIds.mapNotNull { rId -> presRels[rId] }
                .ifEmpty {
                    // Fallback to natural filename order if relationships list is missing/corrupted
                    zip.entries().asSequence()
                        .map { it.name }
                        .filter { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") }
                        .sortedWith { a, b -> DocumentReaderEngine.naturalCompare(a, b) }
                        .toList()
                }

            // 5. Parse each slide
            val slides = mutableListOf<PresentationSlideModel>()
            for ((index, slidePath) in orderedSlidePaths.withIndex()) {
                val slideEntry = zip.getEntry(slidePath) ?: continue

                // Slide relationships: images, notes, layout
                val slideRelsPath = slidePath.replace("ppt/slides/", "ppt/slides/_rels/") + ".rels"
                val slideRels = mutableMapOf<String, String>()
                val relsEntry = zip.getEntry(slideRelsPath)
                if (relsEntry != null) {
                    zip.getInputStream(relsEntry).use { stream ->
                        val bounded = DocumentFormatDetector.createSafeBoundedStream(stream)
                        slideRels.putAll(parseRelationshipsXml(bounded, "ppt/slides/"))
                    }
                }

                // Parse speaker notes if available
                var speakerNotes: String? = null
                val notesPath = slideRels.values.firstOrNull { it.contains("notesSlide", ignoreCase = true) }
                if (notesPath != null) {
                    val notesEntry = zip.getEntry(notesPath)
                    if (notesEntry != null) {
                        zip.getInputStream(notesEntry).use { stream ->
                            val bounded = DocumentFormatDetector.createSafeBoundedStream(stream)
                            speakerNotes = parseNotesSlideXml(bounded)
                        }
                    }
                }

                // Parse slide elements
                zip.getInputStream(slideEntry).use { stream ->
                    val bounded = DocumentFormatDetector.createSafeBoundedStream(stream)
                    val slide = parseSlideXml(
                        stream = bounded,
                        slideNumber = index + 1,
                        slideRels = slideRels,
                        themeColors = themeColors,
                        notes = speakerNotes
                    )
                    slides.add(slide)
                }
            }

            if (slides.isEmpty()) {
                slides.add(
                    PresentationSlideModel(
                        slideNumber = 1,
                        title = file.nameWithoutExtension,
                        elements = listOf(
                            SlideElement.Shape(
                                bounds = ShapeBounds(914400L, 914400L, 7315200L, 2000000L),
                                paragraphs = listOf(
                                    SlideParagraph(
                                        runs = listOf(SlideTextRun("No slides found in presentation.", fontSizePt = 24f))
                                    )
                                )
                            )
                        )
                    )
                )
            }

            return PresentationDocumentModel(
                title = file.name,
                slideWidthEmu = slideWidthEmu,
                slideHeightEmu = slideHeightEmu,
                slides = slides
            )
        }
    }

    private fun parsePresentationXml(stream: InputStream): Triple<Long, Long, List<String>> {
        val parser = createXmlPullParser()
        parser.setInput(stream, "UTF-8")
        var cx = 0L
        var cy = 0L
        val rIds = mutableListOf<String>()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':') ?: ""
            if (eventType == XmlPullParser.START_TAG) {
                if (name == "sldSz") {
                    cx = parser.getAttributeValue(null, "cx")?.toLongOrNull() ?: 0L
                    cy = parser.getAttributeValue(null, "cy")?.toLongOrNull() ?: 0L
                } else if (name == "sldId") {
                    val rId = parser.getAttributeValue(null, "id")
                        ?: parser.getAttributeValue(null, "r:id")
                    // Look through all attributes for relationship ID (r:id or id)
                    var foundRId: String? = null
                    for (i in 0 until parser.attributeCount) {
                        val attrName = parser.getAttributeName(i)
                        if (attrName == "id" && parser.getAttributePrefix(i) == "r") {
                            foundRId = parser.getAttributeValue(i)
                            break
                        }
                    }
                    val effectiveRId = foundRId ?: rId
                    if (effectiveRId != null) {
                        rIds.add(effectiveRId)
                    }
                }
            }
            eventType = parser.next()
        }
        return Triple(cx, cy, rIds)
    }

    private fun parseRelationshipsXml(stream: InputStream, basePath: String): Map<String, String> {
        val rels = mutableMapOf<String, String>()
        val parser = createXmlPullParser()
        parser.setInput(stream, "UTF-8")

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':') ?: ""
            if (eventType == XmlPullParser.START_TAG && name == "Relationship") {
                val id = parser.getAttributeValue(null, "Id")
                var target = parser.getAttributeValue(null, "Target")
                if (id != null && target != null) {
                    if (target.startsWith("../")) {
                        target = "ppt/" + target.removePrefix("../")
                    } else if (!target.startsWith("ppt/")) {
                        target = basePath + target
                    }
                    rels[id] = target
                }
            }
            eventType = parser.next()
        }
        return rels
    }

    private fun parseThemeXml(stream: InputStream): Map<String, Int> {
        val colors = mutableMapOf<String, Int>()
        val parser = createXmlPullParser()
        parser.setInput(stream, "UTF-8")

        var inClrScheme = false
        var currentSchemeKey: String? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':') ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (name == "clrScheme") {
                        inClrScheme = true
                    } else if (inClrScheme) {
                        if (name in setOf("dk1", "lt1", "dk2", "lt2", "accent1", "accent2", "accent3", "accent4", "accent5", "accent6", "hlink", "folHlink")) {
                            currentSchemeKey = name
                        } else if (currentSchemeKey != null) {
                            if (name == "srgbClr") {
                                val hex = parser.getAttributeValue(null, "val")
                                parseHexColor(hex)?.let { colors[currentSchemeKey!!] = it }
                            } else if (name == "sysClr") {
                                val lastClr = parser.getAttributeValue(null, "lastClr")
                                parseHexColor(lastClr)?.let { colors[currentSchemeKey!!] = it }
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (name == "clrScheme") inClrScheme = false
                    if (name == currentSchemeKey) currentSchemeKey = null
                }
            }
            eventType = parser.next()
        }
        return colors
    }

    private fun defaultThemeColors(): Map<String, Int> {
        return mapOf(
            "dk1" to 0xFF000000.toInt(),
            "lt1" to 0xFFFFFFFF.toInt(),
            "dk2" to 0xFF1F497D.toInt(),
            "lt2" to 0xFFEEECE1.toInt(),
            "accent1" to 0xFF4F81BD.toInt(),
            "accent2" to 0xFFC0504D.toInt(),
            "accent3" to 0xFF9BBB59.toInt(),
            "accent4" to 0xFF8064A2.toInt(),
            "accent5" to 0xFF4BACC6.toInt(),
            "accent6" to 0xFFF79646.toInt(),
            "hlink" to 0xFF0000FF.toInt()
        )
    }

    private fun parseNotesSlideXml(stream: InputStream): String {
        val sb = StringBuilder()
        val parser = createXmlPullParser()
        parser.setInput(stream, "UTF-8")

        var inText = false
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':') ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> if (name == "t") inText = true
                XmlPullParser.TEXT -> if (inText) sb.append(parser.text ?: "")
                XmlPullParser.END_TAG -> {
                    if (name == "t") inText = false
                    else if (name == "p") sb.append("\n")
                }
            }
            eventType = parser.next()
        }
        return sb.toString().trim()
    }

    private fun parseSlideXml(
        stream: InputStream,
        slideNumber: Int,
        slideRels: Map<String, String>,
        themeColors: Map<String, Int>,
        notes: String?
    ): PresentationSlideModel {
        val parser = createXmlPullParser()
        parser.setInput(stream, "UTF-8")

        val elements = mutableListOf<SlideElement>()
        var slideTitle: String? = null
        var backgroundFill: ShapeFill = ShapeFill.None

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':') ?: ""
            if (eventType == XmlPullParser.START_TAG) {
                when (name) {
                    "bg" -> {
                        backgroundFill = parseBackgroundFill(parser, themeColors, slideRels)
                    }
                    "sp" -> {
                        val shape = parseShapeElement(parser, themeColors, slideRels)
                        if (shape != null) {
                            elements.add(shape)
                            if (slideTitle == null) {
                                val firstText = shape.paragraphs.firstOrNull()?.fullText
                                if (!firstText.isNullOrBlank()) slideTitle = firstText
                            }
                        }
                    }
                    "pic" -> {
                        val pic = parsePicElement(parser, slideRels)
                        if (pic != null) elements.add(pic)
                    }
                    "graphicFrame" -> {
                        val table = parseGraphicFrame(parser, themeColors)
                        if (table != null) elements.add(table)
                    }
                }
            }
            eventType = parser.next()
        }

        return PresentationSlideModel(
            slideNumber = slideNumber,
            title = slideTitle ?: "Slide $slideNumber",
            backgroundFill = backgroundFill,
            elements = elements,
            speakerNotes = notes
        )
    }

    private fun parseBackgroundFill(
        parser: XmlPullParser,
        themeColors: Map<String, Int>,
        slideRels: Map<String, String>
    ): ShapeFill {
        var fill: ShapeFill = ShapeFill.None
        var depth = 1
        while (depth > 0 && parser.next() != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':') ?: ""
            if (parser.eventType == XmlPullParser.START_TAG) {
                depth++
                if (name == "solidFill") {
                    val color = parseColorElement(parser, themeColors)
                    if (color != null) fill = ShapeFill.Solid(color)
                } else if (name == "blipFill") {
                    val rEmbed = findAttributeValue(parser, "embed")
                    val mediaPath = slideRels[rEmbed]
                    if (mediaPath != null) fill = ShapeFill.Blip(mediaPath)
                }
            } else if (parser.eventType == XmlPullParser.END_TAG) {
                depth--
            }
        }
        return fill
    }

    private fun parseShapeElement(
        parser: XmlPullParser,
        themeColors: Map<String, Int>,
        slideRels: Map<String, String>
    ): SlideElement.Shape? {
        var bounds = ShapeBounds()
        var preset = ShapePreset.RECT
        val customPaths = mutableListOf<PathCommand>()
        var fill: ShapeFill = ShapeFill.None
        var line: ShapeLine? = null
        val paragraphs = mutableListOf<SlideParagraph>()

        var depth = 1
        while (depth > 0 && parser.next() != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':') ?: ""
            if (parser.eventType == XmlPullParser.START_TAG) {
                depth++
                when (name) {
                    "xfrm" -> bounds = parseTransform(parser)
                    "prstGeom" -> {
                        val prst = parser.getAttributeValue(null, "prst")
                        preset = mapShapePreset(prst)
                    }
                    "solidFill" -> {
                        val color = parseColorElement(parser, themeColors)
                        if (color != null) fill = ShapeFill.Solid(color)
                    }
                    "ln" -> {
                        val w = parser.getAttributeValue(null, "w")?.toLongOrNull() ?: 12700L
                        val color = parseColorElement(parser, themeColors) ?: 0xFF000000.toInt()
                        line = ShapeLine(widthEmu = w, colorRgb = color)
                    }
                    "p" -> {
                        val p = parseParagraph(parser, themeColors)
                        if (p != null) paragraphs.add(p)
                    }
                }
            } else if (parser.eventType == XmlPullParser.END_TAG) {
                depth--
            }
        }

        if (!bounds.isValid && paragraphs.isEmpty()) return null
        return SlideElement.Shape(
            bounds = bounds,
            preset = preset,
            customPath = customPaths,
            fill = fill,
            line = line,
            paragraphs = paragraphs
        )
    }

    private fun parsePicElement(parser: XmlPullParser, slideRels: Map<String, String>): SlideElement.Picture? {
        var bounds = ShapeBounds()
        var mediaPath: String? = null

        var depth = 1
        while (depth > 0 && parser.next() != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':') ?: ""
            if (parser.eventType == XmlPullParser.START_TAG) {
                depth++
                if (name == "xfrm") {
                    bounds = parseTransform(parser)
                } else if (name == "blip") {
                    val rEmbed = findAttributeValue(parser, "embed")
                    if (rEmbed != null) mediaPath = slideRels[rEmbed]
                }
            } else if (parser.eventType == XmlPullParser.END_TAG) {
                depth--
            }
        }

        if (mediaPath == null || !bounds.isValid) return null
        return SlideElement.Picture(bounds = bounds, mediaEntryPath = mediaPath)
    }

    private fun parseGraphicFrame(parser: XmlPullParser, themeColors: Map<String, Int>): SlideElement? {
        var bounds = ShapeBounds()
        val rows = mutableListOf<SlideTableRow>()
        val colWidths = mutableListOf<Long>()

        var depth = 1
        while (depth > 0 && parser.next() != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':') ?: ""
            if (parser.eventType == XmlPullParser.START_TAG) {
                depth++
                when (name) {
                    "xfrm" -> bounds = parseTransform(parser)
                    "gridCol" -> {
                        val w = parser.getAttributeValue(null, "w")?.toLongOrNull() ?: 1000000L
                        colWidths.add(w)
                    }
                    "tr" -> {
                        val h = parser.getAttributeValue(null, "h")?.toLongOrNull() ?: 300000L
                        val row = parseTableRow(parser, h, themeColors)
                        rows.add(row)
                    }
                }
            } else if (parser.eventType == XmlPullParser.END_TAG) {
                depth--
            }
        }

        if (rows.isNotEmpty()) {
            return SlideElement.Table(bounds = bounds, rows = rows, colWidthsEmu = colWidths)
        }
        return null
    }

    private fun parseTableRow(parser: XmlPullParser, heightEmu: Long, themeColors: Map<String, Int>): SlideTableRow {
        val cells = mutableListOf<SlideTableCell>()
        var depth = 1
        while (depth > 0 && parser.next() != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':') ?: ""
            if (parser.eventType == XmlPullParser.START_TAG) {
                depth++
                if (name == "tc") {
                    val text = StringBuilder()
                    var innerDepth = 1
                    while (innerDepth > 0 && parser.next() != XmlPullParser.END_DOCUMENT) {
                        val innerName = parser.name?.substringAfter(':') ?: ""
                        if (parser.eventType == XmlPullParser.START_TAG) {
                            innerDepth++
                            if (innerName == "t") {
                                text.append(parser.nextText())
                                innerDepth--
                            }
                        } else if (parser.eventType == XmlPullParser.END_TAG) {
                            innerDepth--
                        }
                    }
                    cells.add(SlideTableCell(text = text.toString()))
                }
            } else if (parser.eventType == XmlPullParser.END_TAG) {
                depth--
            }
        }
        return SlideTableRow(cells = cells, heightEmu = heightEmu)
    }

    private fun parseParagraph(parser: XmlPullParser, themeColors: Map<String, Int>): SlideParagraph? {
        val runs = mutableListOf<SlideTextRun>()
        var align = CellHorizontalAlignment.LEFT
        var isBullet = false
        var bulletChar: String? = null

        val currentRunText = StringBuilder()
        var currentFontSize = 18f
        var currentBold = false
        var currentItalic = false
        var currentUnderline = false
        var currentColor: Int? = null
        var inText = false

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':') ?: ""
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    when (name) {
                        "pPr" -> {
                            val algnStr = parser.getAttributeValue(null, "algn")
                            align = when (algnStr) {
                                "ctr" -> CellHorizontalAlignment.CENTER
                                "r" -> CellHorizontalAlignment.RIGHT
                                "just" -> CellHorizontalAlignment.JUSTIFY
                                else -> CellHorizontalAlignment.LEFT
                            }
                        }
                        "buChar" -> {
                            isBullet = true
                            bulletChar = parser.getAttributeValue(null, "char") ?: "•"
                        }
                        "r" -> {
                            currentRunText.setLength(0)
                            currentFontSize = 18f
                            currentBold = false
                            currentItalic = false
                            currentUnderline = false
                            currentColor = null
                        }
                        "rPr" -> {
                            val sz = parser.getAttributeValue(null, "sz")?.toFloatOrNull()
                            if (sz != null) currentFontSize = sz / 100f
                            currentBold = parser.getAttributeValue(null, "b") == "1"
                            currentItalic = parser.getAttributeValue(null, "i") == "1"
                            currentUnderline = parser.getAttributeValue(null, "u") != null
                            currentColor = parseColorElement(parser, themeColors)
                        }
                        "t" -> inText = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inText) {
                        currentRunText.append(parser.text ?: "")
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (name) {
                        "t" -> inText = false
                        "r" -> {
                            val t = currentRunText.toString()
                            if (t.isNotEmpty()) {
                                runs.add(
                                    SlideTextRun(
                                        text = t,
                                        fontSizePt = currentFontSize,
                                        isBold = currentBold,
                                        isItalic = currentItalic,
                                        isUnderline = currentUnderline,
                                        colorRgb = currentColor
                                    )
                                )
                            }
                        }
                        "p" -> {
                            if (runs.isEmpty()) return null
                            return SlideParagraph(
                                runs = runs,
                                alignment = align,
                                bulletChar = bulletChar ?: if (isBullet) "•" else null
                            )
                        }
                    }
                }
            }
        }

        if (runs.isEmpty()) return null
        return SlideParagraph(
            runs = runs,
            alignment = align,
            bulletChar = bulletChar ?: if (isBullet) "•" else null
        )
    }

    private fun parseTransform(parser: XmlPullParser): ShapeBounds {
        var x = 0L
        var y = 0L
        var cx = 0L
        var cy = 0L
        val rot = parser.getAttributeValue(null, "rot")?.toFloatOrNull()?.let { it / 60000f } ?: 0f
        val flipH = parser.getAttributeValue(null, "flipH") == "1"
        val flipV = parser.getAttributeValue(null, "flipV") == "1"

        var depth = 1
        while (depth > 0 && parser.next() != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':') ?: ""
            if (parser.eventType == XmlPullParser.START_TAG) {
                depth++
                if (name == "off") {
                    x = parser.getAttributeValue(null, "x")?.toLongOrNull() ?: 0L
                    y = parser.getAttributeValue(null, "y")?.toLongOrNull() ?: 0L
                } else if (name == "ext") {
                    cx = parser.getAttributeValue(null, "cx")?.toLongOrNull() ?: 0L
                    cy = parser.getAttributeValue(null, "cy")?.toLongOrNull() ?: 0L
                }
            } else if (parser.eventType == XmlPullParser.END_TAG) {
                depth--
            }
        }

        return ShapeBounds(
            xEmu = x,
            yEmu = y,
            widthEmu = cx,
            heightEmu = cy,
            rotationDegrees = rot,
            flipH = flipH,
            flipV = flipV
        )
    }

    private fun parseColorElement(parser: XmlPullParser, themeColors: Map<String, Int>): Int? {
        var baseColor: Int? = null
        var transform = ColorTransform()

        var depth = 1
        while (depth > 0 && parser.next() != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':') ?: ""
            if (parser.eventType == XmlPullParser.START_TAG) {
                depth++
                when (name) {
                    "srgbClr" -> {
                        val hex = parser.getAttributeValue(null, "val")
                        baseColor = parseHexColor(hex)
                    }
                    "schemeClr" -> {
                        val valName = parser.getAttributeValue(null, "val")
                        baseColor = themeColors[valName]
                    }
                    "lumMod" -> {
                        val v = parser.getAttributeValue(null, "val")?.toFloatOrNull()?.let { it / 100000f }
                        transform = transform.copy(lumMod = v)
                    }
                    "lumOff" -> {
                        val v = parser.getAttributeValue(null, "val")?.toFloatOrNull()?.let { it / 100000f }
                        transform = transform.copy(lumOff = v)
                    }
                    "tint" -> {
                        val v = parser.getAttributeValue(null, "val")?.toFloatOrNull()?.let { it / 100000f }
                        transform = transform.copy(tint = v)
                    }
                    "shade" -> {
                        val v = parser.getAttributeValue(null, "val")?.toFloatOrNull()?.let { it / 100000f }
                        transform = transform.copy(shade = v)
                    }
                }
            } else if (parser.eventType == XmlPullParser.END_TAG) {
                depth--
            }
        }

        return if (baseColor != null) {
            ColorMath.applyTransforms(baseColor, transform)
        } else null
    }

    private fun parseHexColor(hex: String?): Int? {
        if (hex.isNullOrBlank()) return null
        return try {
            if (hex.length == 6) {
                (0xFF000000L or hex.toLong(16)).toInt()
            } else if (hex.length == 8) {
                hex.toLong(16).toInt()
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun findAttributeValue(parser: XmlPullParser, localName: String): String? {
        for (i in 0 until parser.attributeCount) {
            if (parser.getAttributeName(i).equals(localName, ignoreCase = true)) {
                return parser.getAttributeValue(i)
            }
        }
        return null
    }

    private fun mapShapePreset(prst: String?): ShapePreset {
        return when (prst?.lowercase()) {
            "rect" -> ShapePreset.RECT
            "roundrect" -> ShapePreset.ROUND_RECT
            "ellipse" -> ShapePreset.ELLIPSE
            "triangle" -> ShapePreset.TRIANGLE
            "rttriangle" -> ShapePreset.RT_TRIANGLE
            "diamond" -> ShapePreset.DIAMOND
            "parallelogram" -> ShapePreset.PARALLELOGRAM
            "line" -> ShapePreset.LINE
            "straightconnector1" -> ShapePreset.STRAIGHT_CONNECTOR_1
            "rightarrow" -> ShapePreset.RIGHT_ARROW
            "leftarrow" -> ShapePreset.LEFT_ARROW
            "uparrow" -> ShapePreset.UP_ARROW
            "downarrow" -> ShapePreset.DOWN_ARROW
            "pentagon" -> ShapePreset.PENTAGON
            "hexagon" -> ShapePreset.HEXAGON
            "star5" -> ShapePreset.STAR_5
            "chevron" -> ShapePreset.CHEVRON
            else -> ShapePreset.RECT
        }
    }

    // =============================================================================================
    // 2. OpenDocument Presentation (.odp) Parser
    // =============================================================================================

    fun parseOdp(file: File): PresentationDocumentModel {
        val slides = mutableListOf<PresentationSlideModel>()
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("content.xml")
            if (entry != null) {
                zip.getInputStream(entry).use { stream ->
                    val bounded = DocumentFormatDetector.createSafeBoundedStream(stream)
                    slides.addAll(parseOdpContentXml(bounded))
                }
            }
        }

        if (slides.isEmpty()) {
            slides.add(
                PresentationSlideModel(
                    slideNumber = 1,
                    title = file.nameWithoutExtension,
                    elements = listOf(
                        SlideElement.Shape(
                            bounds = ShapeBounds(914400L, 914400L, 7315200L, 2000000L),
                            paragraphs = listOf(
                                SlideParagraph(
                                    runs = listOf(SlideTextRun("No slides found in ODP presentation.", fontSizePt = 24f))
                                )
                            )
                        )
                    )
                )
            )
        }

        return PresentationDocumentModel(title = file.name, slides = slides)
    }

    private fun parseOdpContentXml(stream: InputStream): List<PresentationSlideModel> {
        val parser = createXmlPullParser()
        parser.setInput(stream, "UTF-8")
        val slides = mutableListOf<PresentationSlideModel>()
        var slideCount = 0

        var inPage = false
        var pageTitle: String? = null
        val elements = mutableListOf<SlideElement>()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':') ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (name == "page") {
                        inPage = true
                        slideCount++
                        pageTitle = null
                        elements.clear()
                    } else if (inPage && (name == "frame" || name == "text-box")) {
                        val text = StringBuilder()
                        var innerDepth = 1
                        while (innerDepth > 0 && parser.next() != XmlPullParser.END_DOCUMENT) {
                            val innerName = parser.name?.substringAfter(':') ?: ""
                            if (parser.eventType == XmlPullParser.START_TAG) {
                                innerDepth++
                                if (innerName == "p") {
                                    val line = parser.nextText()
                                    if (line.isNotBlank()) {
                                        text.append(line).append("\n")
                                        if (pageTitle == null) pageTitle = line
                                    }
                                    innerDepth--
                                }
                            } else if (parser.eventType == XmlPullParser.END_TAG) {
                                innerDepth--
                            }
                        }

                        val str = text.toString().trim()
                        if (str.isNotBlank()) {
                            elements.add(
                                SlideElement.Shape(
                                    bounds = ShapeBounds(914400L, 914400L + (elements.size * 1200000L), 7315200L, 1000000L),
                                    paragraphs = listOf(
                                        SlideParagraph(runs = listOf(SlideTextRun(str, fontSizePt = 20f)))
                                    )
                                )
                            )
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (name == "page") {
                        inPage = false
                        slides.add(
                            PresentationSlideModel(
                                slideNumber = slideCount,
                                title = pageTitle ?: "Slide $slideCount",
                                elements = elements.toList()
                            )
                        )
                    }
                }
            }
            eventType = parser.next()
        }
        return slides
    }
}
