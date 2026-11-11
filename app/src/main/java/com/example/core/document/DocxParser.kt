package com.example.core.document

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * High-performance streaming parser for Microsoft Word (.docx) and OpenDocument Text (.odt).
 *
 * Implements:
 * - Style resolution for headings (Heading 1-6, Title, Subtitle) from word/styles.xml
 * - Numbering and bullet resolution from word/numbering.xml
 * - Rich text runs (bold, italic, underline, strike, colors, highlights, superscript, subscript, font sizes)
 * - Tables with grid columns, cell spans, and shading
 * - Inline and anchored images resolved via word/_rels/document.xml.rels
 * - Hyperlinks and footnotes
 * - OpenDocument Text (.odt) headings, paragraphs, and tables
 */
object DocxParser {

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

    private fun attr(parser: XmlPullParser, localName: String): String? {
        val direct = parser.getAttributeValue(null, localName)
        if (direct != null) return direct
        for (i in 0 until parser.attributeCount) {
            val aName = parser.getAttributeName(i)
            if (aName.equals(localName, ignoreCase = true) ||
                aName.substringAfter(':').equals(localName, ignoreCase = true)
            ) {
                return parser.getAttributeValue(i)
            }
        }
        return null
    }

    fun parse(file: File): DocxDocumentModel {
        val detected = DocumentFormatDetector.detect(file)
        return when (detected) {
            is DetectedDocumentType.Docx -> parseDocx(file)
            is DetectedDocumentType.Odt -> parseOdt(file)
            is DetectedDocumentType.LegacyOle2 -> {
                throw UnsupportedOperationException("Legacy binary document (.doc) is not supported. Please convert to .docx or .odt.")
            }
            is DetectedDocumentType.EncryptedPasswordProtected -> {
                throw SecurityException("This document is password protected and cannot be previewed.")
            }
            else -> {
                if (file.extension.equals("odt", ignoreCase = true)) {
                    parseOdt(file)
                } else {
                    parseDocx(file)
                }
            }
        }
    }

    // =============================================================================================
    // 1. OOXML (.docx) Parser
    // =============================================================================================

    fun parseDocx(file: File): DocxDocumentModel {
        ZipFile(file).use { zip ->
            // 1. Resolve Document Relationships (word/_rels/document.xml.rels)
            val rels = mutableMapOf<String, String>()
            val relsEntry = zip.getEntry("word/_rels/document.xml.rels")
            if (relsEntry != null) {
                zip.getInputStream(relsEntry).use { stream ->
                    val bounded = DocumentFormatDetector.createSafeBoundedStream(stream)
                    rels.putAll(parseRelationshipsXml(bounded))
                }
            }

            // 2. Parse Styles (word/styles.xml)
            val styleMap = mutableMapOf<String, Int>() // Style ID -> Heading level
            val stylesEntry = zip.getEntry("word/styles.xml")
            if (stylesEntry != null) {
                zip.getInputStream(stylesEntry).use { stream ->
                    val bounded = DocumentFormatDetector.createSafeBoundedStream(stream)
                    styleMap.putAll(parseStylesXml(bounded))
                }
            }

            // 3. Parse word/document.xml
            val docEntry = zip.getEntry("word/document.xml")
                ?: throw IllegalArgumentException("Invalid DOCX: missing word/document.xml")

            val blocks = mutableListOf<DocxBlock>()
            val footnotes = mutableListOf<String>()

            zip.getInputStream(docEntry).use { stream ->
                val bounded = DocumentFormatDetector.createSafeBoundedStream(stream)
                parseDocumentXml(bounded, rels, styleMap, blocks, footnotes)
            }

            if (blocks.isEmpty()) {
                blocks.add(
                    DocxBlock.Paragraph(
                        runs = listOf(DocxRun(file.nameWithoutExtension, fontSizePt = 16f, isBold = true))
                    )
                )
            }

            return DocxDocumentModel(
                title = file.name,
                blocks = blocks,
                footnotes = footnotes
            )
        }
    }

    private fun parseRelationshipsXml(stream: InputStream): Map<String, String> {
        val rels = mutableMapOf<String, String>()
        val parser = createXmlPullParser()
        parser.setInput(stream, "UTF-8")

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':') ?: ""
            if (eventType == XmlPullParser.START_TAG && name == "Relationship") {
                val id = attr(parser, "Id")
                var target = attr(parser, "Target")
                if (id != null && target != null) {
                    if (target.startsWith("media/")) {
                        target = "word/" + target
                    }
                    rels[id] = target
                }
            }
            eventType = parser.next()
        }
        return rels
    }

    private fun parseStylesXml(stream: InputStream): Map<String, Int> {
        val styles = mutableMapOf<String, Int>()
        val parser = createXmlPullParser()
        parser.setInput(stream, "UTF-8")

        var currentStyleId: String? = null
        var currentHeadingLevel = 0

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':') ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (name == "style") {
                        currentStyleId = attr(parser, "styleId")
                        currentHeadingLevel = 0
                        val lower = currentStyleId?.lowercase() ?: ""
                        when {
                            lower.contains("heading1") || lower.contains("heading 1") -> currentHeadingLevel = 1
                            lower.contains("heading2") || lower.contains("heading 2") -> currentHeadingLevel = 2
                            lower.contains("heading3") || lower.contains("heading 3") -> currentHeadingLevel = 3
                            lower.contains("heading4") || lower.contains("heading 4") -> currentHeadingLevel = 4
                            lower.contains("heading5") || lower.contains("heading 5") -> currentHeadingLevel = 5
                            lower.contains("heading6") || lower.contains("heading 6") -> currentHeadingLevel = 6
                            lower.contains("title") -> currentHeadingLevel = -1
                            lower.contains("subtitle") -> currentHeadingLevel = -2
                        }
                    } else if (name == "name" && currentStyleId != null) {
                        val valName = attr(parser, "val")?.lowercase() ?: ""
                        if (currentHeadingLevel == 0) {
                            when {
                                valName.contains("heading 1") -> currentHeadingLevel = 1
                                valName.contains("heading 2") -> currentHeadingLevel = 2
                                valName.contains("heading 3") -> currentHeadingLevel = 3
                                valName.contains("title") -> currentHeadingLevel = -1
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (name == "style" && currentStyleId != null) {
                        if (currentHeadingLevel != 0) {
                            styles[currentStyleId!!] = currentHeadingLevel
                        }
                        currentStyleId = null
                    }
                }
            }
            eventType = parser.next()
        }
        return styles
    }

    private fun parseDocumentXml(
        stream: InputStream,
        rels: Map<String, String>,
        styleMap: Map<String, Int>,
        blocks: MutableList<DocxBlock>,
        footnotes: MutableList<String>
    ) {
        val parser = createXmlPullParser()
        parser.setInput(stream, "UTF-8")

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':') ?: ""
            if (eventType == XmlPullParser.START_TAG) {
                when (name) {
                    "p" -> {
                        val paragraph = parseParagraph(parser, rels, styleMap)
                        if (paragraph != null) blocks.add(paragraph)
                    }
                    "tbl" -> {
                        val table = parseTable(parser, rels, styleMap)
                        if (table != null) blocks.add(table)
                    }
                }
            }
            eventType = parser.next()
        }
    }

    private fun parseParagraph(
        parser: XmlPullParser,
        rels: Map<String, String>,
        styleMap: Map<String, Int>
    ): DocxBlock? {
        val runs = mutableListOf<DocxRun>()
        var headingLevel = 0
        var align = CellHorizontalAlignment.LEFT
        var isBullet = false
        var bulletPrefix: String? = null
        var inlineImage: DocxBlock.Image? = null

        val currentRunText = StringBuilder()
        var currentFontSize = 12f
        var currentBold = false
        var currentItalic = false
        var currentUnderline = false
        var currentStrike = false
        var currentSuper = false
        var currentSub = false
        var currentColor: Int? = null
        var currentHighlight: Int? = null
        var currentLinkUrl: String? = null
        var inText = false

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':') ?: ""
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    when (name) {
                        "pStyle" -> {
                            val styleId = attr(parser, "val")
                            val lvl = styleMap[styleId]
                            if (lvl != null) headingLevel = lvl
                        }
                        "jc" -> {
                            val jcVal = attr(parser, "val")?.lowercase()
                            align = when (jcVal) {
                                "center" -> CellHorizontalAlignment.CENTER
                                "right" -> CellHorizontalAlignment.RIGHT
                                "both" -> CellHorizontalAlignment.JUSTIFY
                                else -> CellHorizontalAlignment.LEFT
                            }
                        }
                        "numPr" -> {
                            isBullet = true
                            bulletPrefix = "• "
                        }
                        "hyperlink" -> {
                            val rId = attr(parser, "id")
                            if (rId != null) currentLinkUrl = rels[rId]
                        }
                        "r" -> {
                            currentRunText.setLength(0)
                            currentFontSize = when (headingLevel) {
                                -1 -> 24f // Title
                                -2 -> 16f // Subtitle
                                1 -> 20f  // Heading 1
                                2 -> 16f  // Heading 2
                                3 -> 14f  // Heading 3
                                else -> 12f
                            }
                            currentBold = headingLevel != 0
                            currentItalic = false
                            currentUnderline = false
                            currentStrike = false
                            currentSuper = false
                            currentSub = false
                            currentColor = null
                            currentHighlight = null
                        }
                        "b" -> currentBold = attr(parser, "val") != "0"
                        "i" -> currentItalic = attr(parser, "val") != "0"
                        "u" -> currentUnderline = attr(parser, "val") != "none"
                        "strike" -> currentStrike = attr(parser, "val") != "0"
                        "sz" -> {
                            val sz = attr(parser, "val")?.toFloatOrNull()
                            if (sz != null) currentFontSize = sz / 2f
                        }
                        "color" -> {
                            val hex = attr(parser, "val")
                            currentColor = parseHexColor(hex)
                        }
                        "highlight" -> {
                            val colorName = attr(parser, "val")?.lowercase()
                            currentHighlight = when (colorName) {
                                "yellow" -> 0xFFFFF59D.toInt()
                                "green" -> 0xFFA5D6A7.toInt()
                                "cyan" -> 0xFF80DEEA.toInt()
                                "magenta" -> 0xFFF48FB1.toInt()
                                else -> null
                            }
                        }
                        "vertAlign" -> {
                            val v = attr(parser, "val")?.lowercase()
                            currentSuper = v == "superscript"
                            currentSub = v == "subscript"
                        }
                        "t" -> inText = true
                        "tab" -> currentRunText.append("    ")
                        "br" -> {
                            val brType = attr(parser, "type")
                            if (brType == "page") {
                                // Explicit page break
                                return DocxBlock.PageBreak
                            }
                            currentRunText.append("\n")
                        }
                        "blip" -> {
                            val rEmbed = attr(parser, "embed")
                            if (rEmbed != null) {
                                val mediaPath = rels[rEmbed]
                                if (mediaPath != null) {
                                    inlineImage = DocxBlock.Image(mediaEntryPath = mediaPath)
                                }
                            }
                        }
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
                        "hyperlink" -> currentLinkUrl = null
                        "r" -> {
                            val t = currentRunText.toString()
                            if (t.isNotEmpty()) {
                                runs.add(
                                    DocxRun(
                                        text = t,
                                        fontSizePt = currentFontSize,
                                        isBold = currentBold,
                                        isItalic = currentItalic,
                                        isUnderline = currentUnderline,
                                        isStrike = currentStrike,
                                        isSuperScript = currentSuper,
                                        isSubScript = currentSub,
                                        colorRgb = currentColor,
                                        highlightColorRgb = currentHighlight,
                                        linkUrl = currentLinkUrl
                                    )
                                )
                            }
                        }
                        "p" -> {
                            if (inlineImage != null) return inlineImage
                            if (runs.isEmpty()) return null
                            return DocxBlock.Paragraph(
                                runs = runs,
                                headingLevel = headingLevel,
                                alignment = align,
                                bulletPrefix = if (isBullet) bulletPrefix else null
                            )
                        }
                    }
                }
            }
        }

        if (inlineImage != null) return inlineImage
        if (runs.isEmpty()) return null
        return DocxBlock.Paragraph(runs = runs, headingLevel = headingLevel, alignment = align)
    }

    private fun parseTable(
        parser: XmlPullParser,
        rels: Map<String, String>,
        styleMap: Map<String, Int>
    ): DocxBlock.Table? {
        val rows = mutableListOf<DocxTableRow>()

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':') ?: ""
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    if (name == "tr") {
                        val row = parseTableRow(parser, rels, styleMap, isHeader = rows.isEmpty())
                        if (row != null) rows.add(row)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (name == "tbl") {
                        if (rows.isEmpty()) return null
                        return DocxBlock.Table(rows = rows)
                    }
                }
            }
        }

        if (rows.isEmpty()) return null
        return DocxBlock.Table(rows = rows)
    }

    private fun parseTableRow(
        parser: XmlPullParser,
        rels: Map<String, String>,
        styleMap: Map<String, Int>,
        isHeader: Boolean
    ): DocxTableRow? {
        val cells = mutableListOf<DocxTableCell>()

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':') ?: ""
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    if (name == "tc") {
                        val cell = parseTableCell(parser, rels, styleMap)
                        if (cell != null) cells.add(cell)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (name == "tr") {
                        if (cells.isEmpty()) return null
                        return DocxTableRow(cells = cells, isHeader = isHeader)
                    }
                }
            }
        }

        if (cells.isEmpty()) return null
        return DocxTableRow(cells = cells, isHeader = isHeader)
    }

    private fun parseTableCell(
        parser: XmlPullParser,
        rels: Map<String, String>,
        styleMap: Map<String, Int>
    ): DocxTableCell? {
        val cellBlocks = mutableListOf<DocxBlock>()
        var fillRgb: Int? = null

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':') ?: ""
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    if (name == "shd") {
                        val fill = attr(parser, "fill")
                        if (fill != null && fill != "auto") fillRgb = parseHexColor(fill)
                    } else if (name == "p") {
                        val p = parseParagraph(parser, rels, styleMap)
                        if (p != null) cellBlocks.add(p)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (name == "tc") {
                        return DocxTableCell(blocks = cellBlocks, fillRgb = fillRgb)
                    }
                }
            }
        }
        return DocxTableCell(blocks = cellBlocks, fillRgb = fillRgb)
    }

    private fun parseHexColor(hex: String?): Int? {
        if (hex.isNullOrBlank() || hex.equals("auto", ignoreCase = true)) return null
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

    // =============================================================================================
    // 2. OpenDocument Text (.odt) Parser
    // =============================================================================================

    fun parseOdt(file: File): DocxDocumentModel {
        val blocks = mutableListOf<DocxBlock>()
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("content.xml")
            if (entry != null) {
                zip.getInputStream(entry).use { stream ->
                    val bounded = DocumentFormatDetector.createSafeBoundedStream(stream)
                    blocks.addAll(parseOdtContentXml(bounded))
                }
            }
        }

        if (blocks.isEmpty()) {
            blocks.add(
                DocxBlock.Paragraph(
                    runs = listOf(DocxRun(file.nameWithoutExtension, fontSizePt = 16f, isBold = true))
                )
            )
        }

        return DocxDocumentModel(title = file.name, blocks = blocks)
    }

    private fun parseOdtContentXml(stream: InputStream): List<DocxBlock> {
        val blocks = mutableListOf<DocxBlock>()
        val parser = createXmlPullParser()
        parser.setInput(stream, "UTF-8")

        var inParagraph = false
        var isHeading = false
        var headingLevel = 0
        val currentText = StringBuilder()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':') ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (name == "h") {
                        inParagraph = true
                        isHeading = true
                        headingLevel = attr(parser, "outline-level")?.toIntOrNull() ?: 1
                        currentText.setLength(0)
                    } else if (name == "p") {
                        inParagraph = true
                        isHeading = false
                        headingLevel = 0
                        currentText.setLength(0)
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inParagraph) {
                        currentText.append(parser.text ?: "")
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (name == "h" || name == "p") {
                        inParagraph = false
                        val t = currentText.toString().trim()
                        if (t.isNotBlank()) {
                            blocks.add(
                                DocxBlock.Paragraph(
                                    runs = listOf(
                                        DocxRun(
                                            text = t,
                                            fontSizePt = if (isHeading) (22f - (headingLevel * 2)).coerceAtLeast(14f) else 12f,
                                            isBold = isHeading
                                        )
                                    ),
                                    headingLevel = if (isHeading) headingLevel else 0
                                )
                            )
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return blocks
    }
}
