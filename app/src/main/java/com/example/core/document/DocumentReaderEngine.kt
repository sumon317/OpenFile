package com.example.core.document

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

// ---------------------------------------------------------------------------------------------
// 1. Docx / ODT / Text Document Models
// ---------------------------------------------------------------------------------------------

data class DocRun(
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false
)

sealed interface DocBlock {
    data class Paragraph(
        val runs: List<DocRun>,
        val isHeading: Boolean = false,
        val headingLevel: Int = 0,
        val isBullet: Boolean = false
    ) : DocBlock {
        val fullText: String get() = runs.joinToString("") { it.text }
    }

    data class Table(val rows: List<List<String>>) : DocBlock
}

data class DocxDocument(
    val title: String,
    val blocks: List<DocBlock>
)

// ---------------------------------------------------------------------------------------------
// 2. PPTX / ODP Presentation Models
// ---------------------------------------------------------------------------------------------

data class PptSlide(
    val slideNumber: Int,
    val title: String?,
    val items: List<String>
)

data class PptxDocument(
    val title: String,
    val slides: List<PptSlide>
)

// ---------------------------------------------------------------------------------------------
// 3. XLSX / ODS / CSV Spreadsheet Models
// ---------------------------------------------------------------------------------------------

data class SpreadsheetSheet(
    val name: String,
    val rows: List<List<String>>,
    val columnCount: Int
)

data class SpreadsheetDocument(
    val title: String,
    val sheets: List<SpreadsheetSheet>
)

// ---------------------------------------------------------------------------------------------
// 4. CBZ / CBR Comic Book Models
// ---------------------------------------------------------------------------------------------

data class CbzPage(
    val pageIndex: Int,
    val entryName: String,
    val sizeBytes: Long
)

data class CbzDocument(
    val title: String,
    val pages: List<CbzPage>
)

// ---------------------------------------------------------------------------------------------
// 5. Markdown Models
// ---------------------------------------------------------------------------------------------

data class MarkdownInline(
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isCode: Boolean = false,
    val isStrikethrough: Boolean = false,
    val isUnderline: Boolean = false,
    val isHighlight: Boolean = false,
    val linkUrl: String? = null,
    val imageUrl: String? = null
)

sealed interface MarkdownNode {
    data class Header(
        val level: Int,
        val text: String,
        val inlines: List<MarkdownInline> = emptyList()
    ) : MarkdownNode

    data class Paragraph(val inlines: List<MarkdownInline>) : MarkdownNode

    data class CodeBlock(val language: String, val code: String) : MarkdownNode

    data class Blockquote(
        val text: String,
        val inlines: List<MarkdownInline> = emptyList()
    ) : MarkdownNode

    data class ListItem(
        val text: String,
        val isOrdered: Boolean,
        val orderNumber: Int = 1,
        val level: Int = 0,
        val inlines: List<MarkdownInline> = emptyList()
    ) : MarkdownNode

    data class ChecklistItem(
        val text: String,
        val isChecked: Boolean,
        val inlines: List<MarkdownInline> = emptyList()
    ) : MarkdownNode

    data class Table(
        val headers: List<String>,
        val rows: List<List<String>>,
        val headerInlines: List<List<MarkdownInline>> = emptyList(),
        val rowInlines: List<List<List<MarkdownInline>>> = emptyList()
    ) : MarkdownNode

    data class Image(val alt: String, val url: String) : MarkdownNode

    object HorizontalRule : MarkdownNode
}

data class MarkdownDocument(
    val title: String,
    val nodes: List<MarkdownNode>,
    val rawText: String
)

// ---------------------------------------------------------------------------------------------
// Document Reader Engine Implementation
// ---------------------------------------------------------------------------------------------

object DocumentReaderEngine {

    private val ImageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

    private val NaturalDigitRegex = Regex("(\\d+)|(\\D+)")

    fun naturalCompare(a: String, b: String): Int {
        val matcherA = NaturalDigitRegex.findAll(a).iterator()
        val matcherB = NaturalDigitRegex.findAll(b).iterator()
        while (matcherA.hasNext() && matcherB.hasNext()) {
            val tokenA = matcherA.next().value
            val tokenB = matcherB.next().value
            val numA = tokenA.toLongOrNull()
            val numB = tokenB.toLongOrNull()
            if (numA != null && numB != null) {
                val cmp = numA.compareTo(numB)
                if (cmp != 0) return cmp
            } else {
                val cmp = tokenA.compareTo(tokenB, ignoreCase = true)
                if (cmp != 0) return cmp
            }
        }
        return if (matcherA.hasNext()) 1 else if (matcherB.hasNext()) -1 else 0
    }

    private fun createXmlPullParser(): XmlPullParser {
        return try {
            android.util.Xml.newPullParser()
        } catch (e: Throwable) {
            XmlPullParserFactory.newInstance().newPullParser()
        }
    }

    // =============================================================================================
    // DOCX (Word Document) Reader
    // =============================================================================================

    fun readDocx(file: File): DocxDocument {
        val blocks = mutableListOf<DocBlock>()
        try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("word/document.xml")
                if (entry != null) {
                    zip.getInputStream(entry).use { stream ->
                        parseDocxXml(stream, blocks)
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback for corrupt or non-standard docx / doc files
            val rawText = extractPrintableText(file)
            val fallbackParas = rawText.lines()
                .filter { it.isNotBlank() }
                .map { DocBlock.Paragraph(runs = listOf(DocRun(it))) }
            blocks.addAll(fallbackParas)
        }

        if (blocks.isEmpty()) {
            blocks.add(DocBlock.Paragraph(runs = listOf(DocRun("Empty document or unable to extract contents."))))
        }
        return DocxDocument(title = file.name, blocks = blocks)
    }

    private fun parseDocxXml(stream: InputStream, blocks: MutableList<DocBlock>) {
        val parser = createXmlPullParser()
        parser.setInput(stream, "UTF-8")

        var inParagraph = false
        var inRun = false
        var inTable = false
        var inTableRow = false
        var inTableCell = false

        var isBold = false
        var isItalic = false
        var isUnderline = false
        var isHeading = false
        var headingLevel = 0
        var isBullet = false

        val currentRuns = mutableListOf<DocRun>()
        val currentTableRows = mutableListOf<MutableList<String>>()
        val currentTableRow = mutableListOf<String>()
        val currentCellText = StringBuilder()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':') ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (name) {
                        "tbl" -> {
                            inTable = true
                            currentTableRows.clear()
                        }
                        "tr" -> {
                            inTableRow = true
                            currentTableRow.clear()
                        }
                        "tc" -> {
                            inTableCell = true
                            currentCellText.setLength(0)
                        }
                        "p" -> {
                            inParagraph = true
                            currentRuns.clear()
                            isHeading = false
                            headingLevel = 0
                            isBullet = false
                        }
                        "pStyle" -> {
                            val styleVal = parser.getAttributeValue(null, "val") ?: ""
                            if (styleVal.startsWith("Heading", ignoreCase = true) || styleVal.startsWith("Title", ignoreCase = true)) {
                                isHeading = true
                                headingLevel = styleVal.filter { it.isDigit() }.toIntOrNull() ?: 1
                            }
                        }
                        "numPr" -> isBullet = true
                        "r" -> {
                            inRun = true
                            isBold = false
                            isItalic = false
                            isUnderline = false
                        }
                        "b" -> isBold = true
                        "i" -> isItalic = true
                        "u" -> isUnderline = true
                        "t" -> {
                            val text = parser.nextText()
                            if (inTableCell) {
                                currentCellText.append(text)
                            } else if (inRun) {
                                currentRuns.add(
                                    DocRun(
                                        text = text,
                                        isBold = isBold,
                                        isItalic = isItalic,
                                        isUnderline = isUnderline
                                    )
                                )
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (name) {
                        "p" -> {
                            inParagraph = false
                            if (!inTableCell && currentRuns.isNotEmpty()) {
                                blocks.add(
                                    DocBlock.Paragraph(
                                        runs = currentRuns.toList(),
                                        isHeading = isHeading,
                                        headingLevel = headingLevel,
                                        isBullet = isBullet
                                    )
                                )
                            }
                        }
                        "r" -> inRun = false
                        "tc" -> {
                            inTableCell = false
                            currentTableRow.add(currentCellText.toString().trim())
                        }
                        "tr" -> {
                            inTableRow = false
                            if (currentTableRow.isNotEmpty()) {
                                currentTableRows.add(currentTableRow.toMutableList())
                            }
                        }
                        "tbl" -> {
                            inTable = false
                            if (currentTableRows.isNotEmpty()) {
                                blocks.add(DocBlock.Table(currentTableRows.map { it.toList() }))
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }
    }

    // =============================================================================================
    // ODT (OpenDocument Text) Reader
    // =============================================================================================

    fun readOdt(file: File): DocxDocument {
        val blocks = mutableListOf<DocBlock>()
        try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("content.xml")
                if (entry != null) {
                    zip.getInputStream(entry).use { stream ->
                        parseOdtXml(stream, blocks)
                    }
                }
            }
        } catch (e: Exception) {
            val rawText = extractPrintableText(file)
            val fallbackParas = rawText.lines()
                .filter { it.isNotBlank() }
                .map { DocBlock.Paragraph(runs = listOf(DocRun(it))) }
            blocks.addAll(fallbackParas)
        }

        if (blocks.isEmpty()) {
            blocks.add(DocBlock.Paragraph(runs = listOf(DocRun("Empty ODF text document."))))
        }
        return DocxDocument(title = file.name, blocks = blocks)
    }

    private fun parseOdtXml(stream: InputStream, blocks: MutableList<DocBlock>) {
        val parser = createXmlPullParser()
        parser.setInput(stream, "UTF-8")

        var inParagraph = false
        var inHeading = false
        var headingLevel = 1
        var inTable = false
        var inTableRow = false
        var inTableCell = false

        val currentRuns = mutableListOf<DocRun>()
        val currentTableRows = mutableListOf<MutableList<String>>()
        val currentTableRow = mutableListOf<String>()
        val currentCellText = StringBuilder()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':') ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (name) {
                        "table" -> {
                            inTable = true
                            currentTableRows.clear()
                        }
                        "table-row" -> {
                            inTableRow = true
                            currentTableRow.clear()
                        }
                        "table-cell" -> {
                            inTableCell = true
                            currentCellText.setLength(0)
                        }
                        "h" -> {
                            inHeading = true
                            headingLevel = parser.getAttributeValue(null, "outline-level")?.toIntOrNull() ?: 1
                            currentRuns.clear()
                        }
                        "p" -> {
                            inParagraph = true
                            currentRuns.clear()
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text ?: ""
                    if (inTableCell) {
                        currentCellText.append(text)
                    } else if (inParagraph || inHeading) {
                        currentRuns.add(DocRun(text = text))
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (name) {
                        "h" -> {
                            inHeading = false
                            if (currentRuns.isNotEmpty()) {
                                blocks.add(
                                    DocBlock.Paragraph(
                                        runs = currentRuns.toList(),
                                        isHeading = true,
                                        headingLevel = headingLevel
                                    )
                                )
                            }
                        }
                        "p" -> {
                            inParagraph = false
                            if (!inTableCell && currentRuns.isNotEmpty()) {
                                blocks.add(
                                    DocBlock.Paragraph(
                                        runs = currentRuns.toList(),
                                        isHeading = false
                                    )
                                )
                            }
                        }
                        "table-cell" -> {
                            inTableCell = false
                            currentTableRow.add(currentCellText.toString().trim())
                        }
                        "table-row" -> {
                            inTableRow = false
                            if (currentTableRow.isNotEmpty()) {
                                currentTableRows.add(currentTableRow.toMutableList())
                            }
                        }
                        "table" -> {
                            inTable = false
                            if (currentTableRows.isNotEmpty()) {
                                blocks.add(DocBlock.Table(currentTableRows.map { it.toList() }))
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }
    }

    // =============================================================================================
    // PPTX & ODP (PowerPoint / OpenDocument Presentation) Reader
    // =============================================================================================

    fun readPptx(file: File): PptxDocument {
        val slides = mutableListOf<PptSlide>()
        try {
            ZipFile(file).use { zip ->
                val slideEntries = zip.entries().asSequence()
                    .filter { it.name.startsWith("ppt/slides/slide") && it.name.endsWith(".xml") }
                    .sortedWith { a, b -> naturalCompare(a.name, b.name) }
                    .toList()

                slideEntries.forEachIndexed { index, entry ->
                    zip.getInputStream(entry).use { stream ->
                        val paragraphs = parsePptSlideXml(stream)
                        val title = paragraphs.firstOrNull()
                        val items = if (paragraphs.size > 1) paragraphs.drop(1) else emptyList()
                        slides.add(
                            PptSlide(
                                slideNumber = index + 1,
                                title = title,
                                items = items
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            val raw = extractPrintableText(file)
            val rawLines = raw.lines().filter { it.isNotBlank() }
            if (rawLines.isNotEmpty()) {
                slides.add(PptSlide(1, rawLines.firstOrNull(), rawLines.drop(1)))
            }
        }

        if (slides.isEmpty()) {
            slides.add(PptSlide(1, file.nameWithoutExtension, listOf("No slides found in presentation.")))
        }
        return PptxDocument(title = file.name, slides = slides)
    }

    fun readOdp(file: File): PptxDocument {
        val slides = mutableListOf<PptSlide>()
        try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("content.xml")
                if (entry != null) {
                    zip.getInputStream(entry).use { stream ->
                        parseOdpXml(stream, slides)
                    }
                }
            }
        } catch (e: Exception) {
            val raw = extractPrintableText(file)
            val rawLines = raw.lines().filter { it.isNotBlank() }
            if (rawLines.isNotEmpty()) {
                slides.add(PptSlide(1, rawLines.firstOrNull(), rawLines.drop(1)))
            }
        }

        if (slides.isEmpty()) {
            slides.add(PptSlide(1, file.nameWithoutExtension, listOf("No slides found in presentation.")))
        }
        return PptxDocument(title = file.name, slides = slides)
    }

    private fun parsePptSlideXml(stream: InputStream): List<String> {
        val result = mutableListOf<String>()
        val parser = createXmlPullParser()
        parser.setInput(stream, "UTF-8")

        var inParagraph = false
        val currentText = StringBuilder()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':') ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (name == "p") {
                        inParagraph = true
                        currentText.setLength(0)
                    } else if (name == "t" && inParagraph) {
                        currentText.append(parser.nextText())
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (name == "p") {
                        inParagraph = false
                        val str = currentText.toString().trim()
                        if (str.isNotBlank()) {
                            result.add(str)
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return result
    }

    private fun parseOdpXml(stream: InputStream, slides: MutableList<PptSlide>) {
        val parser = createXmlPullParser()
        parser.setInput(stream, "UTF-8")

        var inPage = false
        var pageCount = 0
        val pageTextItems = mutableListOf<String>()
        val currentText = StringBuilder()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':') ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (name == "page") {
                        inPage = true
                        pageCount++
                        pageTextItems.clear()
                    } else if (name == "p" && inPage) {
                        currentText.setLength(0)
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inPage) {
                        currentText.append(parser.text ?: "")
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (name == "p" && inPage) {
                        val str = currentText.toString().trim()
                        if (str.isNotBlank()) {
                            pageTextItems.add(str)
                        }
                    } else if (name == "page") {
                        inPage = false
                        val title = pageTextItems.firstOrNull()
                        val items = if (pageTextItems.size > 1) pageTextItems.drop(1) else emptyList()
                        slides.add(
                            PptSlide(
                                slideNumber = pageCount,
                                title = title ?: "Slide $pageCount",
                                items = items
                            )
                        )
                    }
                }
            }
            eventType = parser.next()
        }
    }

    // =============================================================================================
    // XLSX & ODS & CSV (Spreadsheet) Reader
    // =============================================================================================

    fun readSpreadsheet(file: File): SpreadsheetDocument {
        val ext = file.extension.lowercase()
        return when (ext) {
            "xlsx", "xlsm" -> readXlsx(file)
            "ods" -> readOds(file)
            "tsv" -> readDelimited(file, '\t')
            else -> readDelimited(file, ',')
        }
    }

    private fun readXlsx(file: File): SpreadsheetDocument {
        val sheets = mutableListOf<SpreadsheetSheet>()
        try {
            ZipFile(file).use { zip ->
                // 1. Shared Strings Table
                val sharedStrings = mutableListOf<String>()
                val sstEntry = zip.getEntry("xl/sharedStrings.xml")
                if (sstEntry != null) {
                    zip.getInputStream(sstEntry).use { stream ->
                        parseSharedStrings(stream, sharedStrings)
                    }
                }

                // 2. Discover Sheet Names
                val sheetInfoList = mutableListOf<Pair<String, String>>() // Name to RelPath
                val workbookEntry = zip.getEntry("xl/workbook.xml")
                if (workbookEntry != null) {
                    zip.getInputStream(workbookEntry).use { stream ->
                        parseWorkbook(stream, sheetInfoList)
                    }
                }

                // 3. Parse each sheet
                val sheetEntries = zip.entries().asSequence()
                    .filter { it.name.startsWith("xl/worksheets/sheet") && it.name.endsWith(".xml") }
                    .sortedWith { a, b -> naturalCompare(a.name, b.name) }
                    .toList()

                sheetEntries.forEachIndexed { index, entry ->
                    val sheetName = sheetInfoList.getOrNull(index)?.first ?: "Sheet ${index + 1}"
                    zip.getInputStream(entry).use { stream ->
                        val sheet = parseWorksheet(stream, sheetName, sharedStrings)
                        sheets.add(sheet)
                    }
                }
            }
        } catch (e: Exception) {
            sheets.add(SpreadsheetSheet("Sheet 1", listOf(listOf("Error reading XLSX: ${e.message}")), 1))
        }

        if (sheets.isEmpty()) {
            sheets.add(SpreadsheetSheet("Sheet 1", listOf(listOf("Empty spreadsheet")), 1))
        }
        return SpreadsheetDocument(title = file.name, sheets = sheets)
    }

    private fun parseSharedStrings(stream: InputStream, sink: MutableList<String>) {
        val parser = createXmlPullParser()
        parser.setInput(stream, "UTF-8")
        var currentText = StringBuilder()
        var inText = false

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':') ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (name == "si") {
                        currentText.setLength(0)
                    } else if (name == "t") {
                        inText = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inText) {
                        currentText.append(parser.text ?: "")
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (name == "t") {
                        inText = false
                    } else if (name == "si") {
                        sink.add(currentText.toString())
                    }
                }
            }
            eventType = parser.next()
        }
    }

    private fun parseWorkbook(stream: InputStream, sink: MutableList<Pair<String, String>>) {
        val parser = createXmlPullParser()
        parser.setInput(stream, "UTF-8")
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':') ?: ""
            if (eventType == XmlPullParser.START_TAG && name == "sheet") {
                val sheetName = parser.getAttributeValue(null, "name") ?: "Sheet"
                sink.add(sheetName to "")
            }
            eventType = parser.next()
        }
    }

    private fun parseWorksheet(stream: InputStream, sheetName: String, sharedStrings: List<String>): SpreadsheetSheet {
        val parser = createXmlPullParser()
        parser.setInput(stream, "UTF-8")

        val rows = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        var cellType: String? = null
        var cellRef: String? = null
        var inValue = false
        var inInlineStr = false
        val cellText = StringBuilder()
        var maxCols = 0

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':') ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (name) {
                        "row" -> {
                            currentRow = mutableListOf()
                        }
                        "c" -> {
                            cellType = parser.getAttributeValue(null, "t")
                            cellRef = parser.getAttributeValue(null, "r")
                            cellText.setLength(0)
                        }
                        "v" -> inValue = true
                        "is" -> inInlineStr = true
                        "t" -> if (inInlineStr) inValue = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inValue) {
                        cellText.append(parser.text ?: "")
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (name) {
                        "v", "t" -> inValue = false
                        "is" -> inInlineStr = false
                        "c" -> {
                            val raw = cellText.toString().trim()
                            val value = if (cellType == "s") {
                                val idx = raw.toIntOrNull() ?: -1
                                if (idx in sharedStrings.indices) sharedStrings[idx] else raw
                            } else {
                                raw
                            }

                            // Align with column coordinate (e.g. A, B, C) if available
                            val colIndex = cellRef?.filter { it.isLetter() }?.let { columnLetterToIndex(it) }
                            if (colIndex != null) {
                                while (currentRow.size < colIndex) {
                                    currentRow.add("")
                                }
                            }
                            currentRow.add(value)
                        }
                        "row" -> {
                            if (currentRow.isNotEmpty()) {
                                maxCols = maxOf(maxCols, currentRow.size)
                                rows.add(currentRow.toList())
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return SpreadsheetSheet(sheetName, rows, maxCols.coerceAtLeast(1))
    }

    private fun columnLetterToIndex(colStr: String): Int {
        var index = 0
        for (ch in colStr.uppercase()) {
            index = index * 26 + (ch - 'A' + 1)
        }
        return (index - 1).coerceAtLeast(0)
    }

    private fun readOds(file: File): SpreadsheetDocument {
        val sheets = mutableListOf<SpreadsheetSheet>()
        try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("content.xml")
                if (entry != null) {
                    zip.getInputStream(entry).use { stream ->
                        parseOdsXml(stream, sheets)
                    }
                }
            }
        } catch (e: Exception) {
            sheets.add(SpreadsheetSheet("Sheet 1", listOf(listOf("Error reading ODS: ${e.message}")), 1))
        }

        if (sheets.isEmpty()) {
            sheets.add(SpreadsheetSheet("Sheet 1", listOf(listOf("Empty ODS spreadsheet")), 1))
        }
        return SpreadsheetDocument(title = file.name, sheets = sheets)
    }

    private fun parseOdsXml(stream: InputStream, sheets: MutableList<SpreadsheetSheet>) {
        val parser = createXmlPullParser()
        parser.setInput(stream, "UTF-8")

        var currentSheetName = "Sheet 1"
        var currentRows = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        var cellText = StringBuilder()
        var inCell = false
        var maxCols = 0

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':') ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (name) {
                        "table" -> {
                            currentSheetName = parser.getAttributeValue(null, "name") ?: "Sheet ${sheets.size + 1}"
                            currentRows = mutableListOf()
                            maxCols = 0
                        }
                        "table-row" -> {
                            currentRow = mutableListOf()
                        }
                        "table-cell" -> {
                            inCell = true
                            cellText.setLength(0)
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inCell) {
                        cellText.append(parser.text ?: "")
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (name) {
                        "table-cell" -> {
                            inCell = false
                            currentRow.add(cellText.toString().trim())
                        }
                        "table-row" -> {
                            if (currentRow.isNotEmpty() && currentRow.any { it.isNotBlank() }) {
                                maxCols = maxOf(maxCols, currentRow.size)
                                currentRows.add(currentRow.toList())
                            }
                        }
                        "table" -> {
                            if (currentRows.isNotEmpty()) {
                                sheets.add(SpreadsheetSheet(currentSheetName, currentRows.toList(), maxCols.coerceAtLeast(1)))
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }
    }

    private fun readDelimited(file: File, delimiter: Char): SpreadsheetDocument {
        val rows = mutableListOf<List<String>>()
        var maxCols = 0
        file.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val cells = parseDelimitedLine(line, delimiter)
                maxCols = maxOf(maxCols, cells.size)
                rows.add(cells)
            }
        }
        val sheet = SpreadsheetSheet(
            name = file.nameWithoutExtension,
            rows = rows,
            columnCount = maxCols.coerceAtLeast(1)
        )
        return SpreadsheetDocument(title = file.name, sheets = listOf(sheet))
    }

    private fun parseDelimitedLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    current.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == delimiter && !inQuotes) {
                result.add(current.toString().trim())
                current.setLength(0)
            } else {
                current.append(c)
            }
            i++
        }
        result.add(current.toString().trim())
        return result
    }

    // =============================================================================================
    // CBZ & CBR (Comic Book Archive) Reader
    // =============================================================================================

    fun readCbz(file: File): CbzDocument {
        val pages = mutableListOf<CbzPage>()
        try {
            ZipFile(file).use { zip ->
                val imageEntries = zip.entries().asSequence()
                    .filter { !it.isDirectory }
                    .filter { entry ->
                        val ext = entry.name.substringAfterLast('.', "").lowercase()
                        ext in ImageExtensions
                    }
                    .sortedWith { a, b -> naturalCompare(a.name, b.name) }
                    .toList()

                imageEntries.forEachIndexed { index, entry ->
                    pages.add(
                        CbzPage(
                            pageIndex = index,
                            entryName = entry.name,
                            sizeBytes = entry.size
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Non-zip CBR or corrupt archive
        }
        return CbzDocument(title = file.name, pages = pages)
    }

    fun extractCbzPage(zipFile: File, page: CbzPage, cacheDir: File): File? {
        return try {
            val destFile = File(cacheDir, "cbz_${zipFile.name.hashCode()}_page_${page.pageIndex}.jpg")
            if (destFile.exists() && destFile.length() > 0L) {
                return destFile
            }
            ZipFile(zipFile).use { zip ->
                val entry = zip.getEntry(page.entryName) ?: return null
                zip.getInputStream(entry).use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            destFile
        } catch (e: Exception) {
            null
        }
    }

    // =============================================================================================
    // Markdown Parser & Model
    // =============================================================================================

    fun parseMarkdown(text: String, title: String): MarkdownDocument {
        val nodes = mutableListOf<MarkdownNode>()
        val lines = text.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            // Skip blank lines
            if (trimmed.isEmpty()) {
                i++
                continue
            }

            // 1. Fenced Code Block (``` or ~~~)
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                val fence = if (trimmed.startsWith("```")) "```" else "~~~"
                val lang = trimmed.removePrefix(fence).trim()
                val codeBuilder = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith(fence)) {
                    codeBuilder.append(lines[i]).append("\n")
                    i++
                }
                nodes.add(MarkdownNode.CodeBlock(lang, codeBuilder.toString().trimEnd()))
                if (i < lines.size) i++ // skip closing fence
                continue
            }

            // 2. Horizontal Rule (---, ***, ___, - - -, * * *)
            if (isDelimiterRule(trimmed)) {
                nodes.add(MarkdownNode.HorizontalRule)
                i++
                continue
            }

            // 3. Setext Headers (line followed by === or ---)
            if (i + 1 < lines.size) {
                val nextTrimmed = lines[i + 1].trim()
                if (nextTrimmed.isNotEmpty() && nextTrimmed.all { it == '=' } && nextTrimmed.length >= 2) {
                    val headerInlines = parseMarkdownInlines(trimmed)
                    nodes.add(MarkdownNode.Header(1, trimmed, headerInlines))
                    i += 2
                    continue
                } else if (nextTrimmed.isNotEmpty() && nextTrimmed.all { it == '-' } && nextTrimmed.length >= 2 && !trimmed.startsWith("-") && !trimmed.startsWith("*") && !trimmed.startsWith("+")) {
                    val headerInlines = parseMarkdownInlines(trimmed)
                    nodes.add(MarkdownNode.Header(2, trimmed, headerInlines))
                    i += 2
                    continue
                }
            }

            // 4. ATX Header (# to ######)
            if (trimmed.startsWith("#")) {
                val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 6)
                if (trimmed.length == level || trimmed[level] == ' ' || trimmed[level] == '\t') {
                    val headerText = trimmed.drop(level).trim().trimEnd { it == '#' }.trim()
                    val headerInlines = parseMarkdownInlines(headerText)
                    nodes.add(MarkdownNode.Header(level, headerText, headerInlines))
                    i++
                    continue
                }
            }

            // 5. Blockquote (> )
            if (trimmed.startsWith(">")) {
                val quoteLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    quoteLines.add(lines[i].trim().removePrefix(">").trim())
                    i++
                }
                val combined = quoteLines.joinToString(" ")
                val inlines = parseMarkdownInlines(combined)
                nodes.add(MarkdownNode.Blockquote(combined, inlines))
                continue
            }

            // 6. Checklist (- [ ] or - [x] or * [ ] or + [ ])
            val checklistMatch = Regex("^[-*+]\\s+\\[([ xX])\\]\\s+(.*)").find(trimmed)
            if (checklistMatch != null) {
                val isChecked = checklistMatch.groupValues[1].equals("x", ignoreCase = true)
                val content = checklistMatch.groupValues[2]
                val inlines = parseMarkdownInlines(content)
                nodes.add(MarkdownNode.ChecklistItem(content, isChecked = isChecked, inlines = inlines))
                i++
                continue
            }

            // 7. Bullet List (- , * , + )
            val bulletMatch = Regex("^(\\s*)([-*+])\\s+(.*)").find(line)
            if (bulletMatch != null && !isDelimiterRule(trimmed)) {
                val indentSpaces = bulletMatch.groupValues[1].length
                val level = (indentSpaces / 2).coerceIn(0, 5)
                val content = bulletMatch.groupValues[3].trim()
                val inlines = parseMarkdownInlines(content)
                nodes.add(MarkdownNode.ListItem(content, isOrdered = false, level = level, inlines = inlines))
                i++
                continue
            }

            // 8. Numbered List (1. or 1) )
            val numberMatch = Regex("^(\\s*)(\\d+)[.)]\\s+(.*)").find(line)
            if (numberMatch != null) {
                val indentSpaces = numberMatch.groupValues[1].length
                val level = (indentSpaces / 2).coerceIn(0, 5)
                val num = numberMatch.groupValues[2].toIntOrNull() ?: 1
                val content = numberMatch.groupValues[3].trim()
                val inlines = parseMarkdownInlines(content)
                nodes.add(MarkdownNode.ListItem(content, isOrdered = true, orderNumber = num, level = level, inlines = inlines))
                i++
                continue
            }

            // 9. Markdown Table (| Col 1 | Col 2 |)
            if (trimmed.startsWith("|") || (trimmed.contains("|") && i + 1 < lines.size && lines[i + 1].trim().contains("---"))) {
                val headers = trimmed.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                val headerInlines = headers.map { parseMarkdownInlines(it) }
                val tableRows = mutableListOf<List<String>>()
                val rowInlines = mutableListOf<List<List<MarkdownInline>>>()
                i++
                // Skip separator row (|---|---|)
                if (i < lines.size && lines[i].contains("---") && lines[i].contains("|")) {
                    i++
                }
                while (i < lines.size && lines[i].trim().contains("|") && !lines[i].trim().startsWith("```")) {
                    val rowCells = lines[i].trim().split("|").map { it.trim() }.filter { it.isNotEmpty() }
                    if (rowCells.isNotEmpty()) {
                        tableRows.add(rowCells)
                        rowInlines.add(rowCells.map { parseMarkdownInlines(it) })
                    }
                    i++
                }
                nodes.add(MarkdownNode.Table(headers, tableRows, headerInlines, rowInlines))
                continue
            }

            // 10. Standalone Image: ![alt](url)
            val standaloneImgMatch = Regex("^!\\[(.*?)\\]\\((.*?)\\)$").find(trimmed)
            if (standaloneImgMatch != null) {
                val alt = standaloneImgMatch.groupValues[1]
                val url = standaloneImgMatch.groupValues[2].trim()
                nodes.add(MarkdownNode.Image(alt = alt.ifEmpty { "Image" }, url = url))
                i++
                continue
            }

            // 11. Multi-line Paragraph
            val paraLines = mutableListOf<String>()
            while (i < lines.size) {
                val curLine = lines[i]
                val curTrim = curLine.trim()
                if (curTrim.isEmpty()) break
                if (curTrim.startsWith("```") || curTrim.startsWith("~~~") || isDelimiterRule(curTrim)) break
                if (curTrim.startsWith("#") && curTrim.takeWhile { it == '#' }.length in 1..6) {
                    val hLen = curTrim.takeWhile { it == '#' }.length
                    if (curTrim.length == hLen || curTrim[hLen] == ' ' || curTrim[hLen] == '\t') break
                }
                if (curTrim.startsWith(">")) break
                if (Regex("^[-*+]\\s+\\[[ xX]\\]").containsMatchIn(curTrim)) break
                if (Regex("^(\\s*)([-*+])\\s+").containsMatchIn(curLine) && !isDelimiterRule(curTrim)) break
                if (Regex("^(\\s*)(\\d+)[.)]\\s+").containsMatchIn(curLine)) break
                if (curTrim.startsWith("|") && curTrim.endsWith("|")) break

                paraLines.add(curLine)
                i++
            }

            if (paraLines.isNotEmpty()) {
                val joinedText = buildString {
                    for (pIdx in paraLines.indices) {
                        val pLine = paraLines[pIdx]
                        if (pIdx > 0) {
                            val prev = paraLines[pIdx - 1]
                            if (prev.endsWith("  ") || prev.endsWith("\\")) {
                                append("\n")
                            } else {
                                append(" ")
                            }
                        }
                        append(pLine.trim())
                    }
                }
                val inlines = parseMarkdownInlines(joinedText)
                nodes.add(MarkdownNode.Paragraph(inlines))
            }
        }

        return MarkdownDocument(title = title, nodes = nodes, rawText = text)
    }

    private fun isDelimiterRule(trimmed: String): Boolean {
        val clean = trimmed.replace(" ", "")
        return clean.length >= 3 && (clean.all { it == '-' } || clean.all { it == '*' } || clean.all { it == '_' })
    }

    fun parseMarkdownInlines(
        text: String,
        baseBold: Boolean = false,
        baseItalic: Boolean = false,
        baseStrikethrough: Boolean = false,
        baseUnderline: Boolean = false,
        baseHighlight: Boolean = false,
        baseLinkUrl: String? = null
    ): List<MarkdownInline> {
        if (text.isEmpty()) return emptyList()

        val inlines = mutableListOf<MarkdownInline>()
        val plainBuilder = StringBuilder()

        fun flushPlain() {
            if (plainBuilder.isNotEmpty()) {
                inlines.add(
                    MarkdownInline(
                        text = plainBuilder.toString(),
                        isBold = baseBold,
                        isItalic = baseItalic,
                        isStrikethrough = baseStrikethrough,
                        isUnderline = baseUnderline,
                        isHighlight = baseHighlight,
                        linkUrl = baseLinkUrl
                    )
                )
                plainBuilder.clear()
            }
        }

        var i = 0
        val len = text.length

        while (i < len) {
            val ch = text[i]

            // 1. Escaped characters: \*, \_, \`, \~, \[, etc.
            if (ch == '\\' && i + 1 < len) {
                val next = text[i + 1]
                if ("*`_~[]()#+-=!<>\\".contains(next)) {
                    plainBuilder.append(next)
                    i += 2
                    continue
                }
            }

            // 2. Inline Code: `code` or ``code``
            if (ch == '`') {
                val backticks = text.substring(i).takeWhile { it == '`' }
                val count = backticks.length
                val closeIndex = text.indexOf(backticks, i + count)
                if (closeIndex != -1) {
                    flushPlain()
                    val codeContent = text.substring(i + count, closeIndex)
                    inlines.add(
                        MarkdownInline(
                            text = codeContent,
                            isCode = true,
                            linkUrl = baseLinkUrl
                        )
                    )
                    i = closeIndex + count
                    continue
                }
            }

            // 3. HTML tags: <b>, <strong>, <i>, <em>, <code>, <del>, <s>, <u>, <mark>, <br>
            if (ch == '<') {
                val remaining = text.substring(i)
                when {
                    remaining.startsWith("<br>", ignoreCase = true) -> {
                        plainBuilder.append("\n")
                        i += 4
                        continue
                    }
                    remaining.startsWith("<br/>", ignoreCase = true) || remaining.startsWith("<br />", ignoreCase = true) -> {
                        plainBuilder.append("\n")
                        i += if (remaining.startsWith("<br/>", ignoreCase = true)) 5 else 6
                        continue
                    }
                    remaining.startsWith("<b>", ignoreCase = true) || remaining.startsWith("<strong>", ignoreCase = true) -> {
                        val tagLen = if (remaining.startsWith("<b>", ignoreCase = true)) 3 else 8
                        val closeTag = if (tagLen == 3) "</b>" else "</strong>"
                        val closeIdx = remaining.indexOf(closeTag, ignoreCase = true)
                        if (closeIdx != -1) {
                            flushPlain()
                            val inner = remaining.substring(tagLen, closeIdx)
                            inlines.addAll(parseMarkdownInlines(inner, baseBold = true, baseItalic = baseItalic, baseStrikethrough = baseStrikethrough, baseUnderline = baseUnderline, baseHighlight = baseHighlight, baseLinkUrl = baseLinkUrl))
                            i += closeIdx + closeTag.length
                            continue
                        }
                    }
                    remaining.startsWith("<i>", ignoreCase = true) || remaining.startsWith("<em>", ignoreCase = true) -> {
                        val tagLen = if (remaining.startsWith("<i>", ignoreCase = true)) 3 else 4
                        val closeTag = if (tagLen == 3) "</i>" else "</em>"
                        val closeIdx = remaining.indexOf(closeTag, ignoreCase = true)
                        if (closeIdx != -1) {
                            flushPlain()
                            val inner = remaining.substring(tagLen, closeIdx)
                            inlines.addAll(parseMarkdownInlines(inner, baseBold = baseBold, baseItalic = true, baseStrikethrough = baseStrikethrough, baseUnderline = baseUnderline, baseHighlight = baseHighlight, baseLinkUrl = baseLinkUrl))
                            i += closeIdx + closeTag.length
                            continue
                        }
                    }
                    remaining.startsWith("<u>", ignoreCase = true) || remaining.startsWith("<ins>", ignoreCase = true) -> {
                        val tagLen = if (remaining.startsWith("<u>", ignoreCase = true)) 3 else 5
                        val closeTag = if (tagLen == 3) "</u>" else "</ins>"
                        val closeIdx = remaining.indexOf(closeTag, ignoreCase = true)
                        if (closeIdx != -1) {
                            flushPlain()
                            val inner = remaining.substring(tagLen, closeIdx)
                            inlines.addAll(parseMarkdownInlines(inner, baseBold = baseBold, baseItalic = baseItalic, baseStrikethrough = baseStrikethrough, baseUnderline = true, baseHighlight = baseHighlight, baseLinkUrl = baseLinkUrl))
                            i += closeIdx + closeTag.length
                            continue
                        }
                    }
                    remaining.startsWith("<del>", ignoreCase = true) || remaining.startsWith("<s>", ignoreCase = true) -> {
                        val tagLen = if (remaining.startsWith("<del>", ignoreCase = true)) 5 else 3
                        val closeTag = if (tagLen == 5) "</del>" else "</s>"
                        val closeIdx = remaining.indexOf(closeTag, ignoreCase = true)
                        if (closeIdx != -1) {
                            flushPlain()
                            val inner = remaining.substring(tagLen, closeIdx)
                            inlines.addAll(parseMarkdownInlines(inner, baseBold = baseBold, baseItalic = baseItalic, baseStrikethrough = true, baseUnderline = baseUnderline, baseHighlight = baseHighlight, baseLinkUrl = baseLinkUrl))
                            i += closeIdx + closeTag.length
                            continue
                        }
                    }
                    remaining.startsWith("<mark>", ignoreCase = true) -> {
                        val closeIdx = remaining.indexOf("</mark>", ignoreCase = true)
                        if (closeIdx != -1) {
                            flushPlain()
                            val inner = remaining.substring(6, closeIdx)
                            inlines.addAll(parseMarkdownInlines(inner, baseBold = baseBold, baseItalic = baseItalic, baseStrikethrough = baseStrikethrough, baseUnderline = baseUnderline, baseHighlight = true, baseLinkUrl = baseLinkUrl))
                            i += closeIdx + 7
                            continue
                        }
                    }
                    remaining.startsWith("<code>", ignoreCase = true) -> {
                        val closeIdx = remaining.indexOf("</code>", ignoreCase = true)
                        if (closeIdx != -1) {
                            flushPlain()
                            val inner = remaining.substring(6, closeIdx)
                            inlines.add(MarkdownInline(text = inner, isCode = true, linkUrl = baseLinkUrl))
                            i += closeIdx + 7
                            continue
                        }
                    }
                    remaining.startsWith("<http://") || remaining.startsWith("<https://") || remaining.startsWith("<mailto:") -> {
                        val closeIdx = remaining.indexOf(">")
                        if (closeIdx != -1) {
                            flushPlain()
                            val url = remaining.substring(1, closeIdx)
                            inlines.add(MarkdownInline(text = url, linkUrl = url))
                            i += closeIdx + 1
                            continue
                        }
                    }
                }
            }

            // 4. Image: ![alt](url)
            if (ch == '!' && i + 1 < len && text[i + 1] == '[') {
                val closeBracket = text.indexOf("](", i + 2)
                if (closeBracket != -1) {
                    val closeParen = text.indexOf(")", closeBracket + 2)
                    if (closeParen != -1) {
                        flushPlain()
                        val altText = text.substring(i + 2, closeBracket)
                        val imgUrl = text.substring(closeBracket + 2, closeParen).trim()
                        inlines.add(MarkdownInline(text = altText.ifEmpty { "Image" }, imageUrl = imgUrl))
                        i = closeParen + 1
                        continue
                    }
                }
            }

            // 5. Link: [label](url)
            if (ch == '[') {
                val closeBracket = text.indexOf("](", i + 1)
                if (closeBracket != -1) {
                    val closeParen = text.indexOf(")", closeBracket + 2)
                    if (closeParen != -1) {
                        flushPlain()
                        val label = text.substring(i + 1, closeBracket)
                        val url = text.substring(closeBracket + 2, closeParen).trim()
                        val linkInlines = parseMarkdownInlines(label, baseBold = baseBold, baseItalic = baseItalic, baseStrikethrough = baseStrikethrough, baseUnderline = baseUnderline, baseHighlight = baseHighlight, baseLinkUrl = url)
                        inlines.addAll(linkInlines)
                        i = closeParen + 1
                        continue
                    }
                }
            }

            // 6. Bold + Italic: ***text*** or ___text___
            if (text.startsWith("***", i) || text.startsWith("___", i)) {
                val delim = text.substring(i, i + 3)
                val closeIdx = findClosingDelimiter(text, delim, i + 3)
                if (closeIdx != -1) {
                    flushPlain()
                    val inner = text.substring(i + 3, closeIdx)
                    inlines.addAll(parseMarkdownInlines(inner, baseBold = true, baseItalic = true, baseStrikethrough = baseStrikethrough, baseUnderline = baseUnderline, baseHighlight = baseHighlight, baseLinkUrl = baseLinkUrl))
                    i = closeIdx + 3
                    continue
                }
            }

            // 7. Bold: **text** or __text__
            if (text.startsWith("**", i) || text.startsWith("__", i)) {
                val delim = text.substring(i, i + 2)
                val isUnderscore = delim == "__"
                val isIntraWord = isUnderscore && i > 0 && text[i - 1].isLetterOrDigit()
                if (!isIntraWord) {
                    val closeIdx = findClosingDelimiter(text, delim, i + 2)
                    if (closeIdx != -1) {
                        flushPlain()
                        val inner = text.substring(i + 2, closeIdx)
                        inlines.addAll(parseMarkdownInlines(inner, baseBold = true, baseItalic = baseItalic, baseStrikethrough = baseStrikethrough, baseUnderline = baseUnderline, baseHighlight = baseHighlight, baseLinkUrl = baseLinkUrl))
                        i = closeIdx + 2
                        continue
                    }
                }
            }

            // 8. Strikethrough: ~~text~~
            if (text.startsWith("~~", i)) {
                val closeIdx = findClosingDelimiter(text, "~~", i + 2)
                if (closeIdx != -1) {
                    flushPlain()
                    val inner = text.substring(i + 2, closeIdx)
                    inlines.addAll(parseMarkdownInlines(inner, baseBold = baseBold, baseItalic = baseItalic, baseStrikethrough = true, baseUnderline = baseUnderline, baseHighlight = baseHighlight, baseLinkUrl = baseLinkUrl))
                    i = closeIdx + 2
                    continue
                }
            }

            // 9. Highlight: ==text==
            if (text.startsWith("==", i)) {
                val closeIdx = findClosingDelimiter(text, "==", i + 2)
                if (closeIdx != -1) {
                    flushPlain()
                    val inner = text.substring(i + 2, closeIdx)
                    inlines.addAll(parseMarkdownInlines(inner, baseBold = baseBold, baseItalic = baseItalic, baseStrikethrough = baseStrikethrough, baseUnderline = baseUnderline, baseHighlight = true, baseLinkUrl = baseLinkUrl))
                    i = closeIdx + 2
                    continue
                }
            }

            // 10. Italic: *text* or _text_
            if (ch == '*' || ch == '_') {
                val delim = ch.toString()
                val isUnderscore = ch == '_'
                val isIntraWord = isUnderscore && i > 0 && text[i - 1].isLetterOrDigit()
                val nextChar = if (i + 1 < len) text[i + 1] else ' '
                if (!isIntraWord && nextChar != ' ' && nextChar != '\t') {
                    val closeIdx = findClosingDelimiter(text, delim, i + 1)
                    if (closeIdx != -1) {
                        flushPlain()
                        val inner = text.substring(i + 1, closeIdx)
                        inlines.addAll(parseMarkdownInlines(inner, baseBold = baseBold, baseItalic = true, baseStrikethrough = baseStrikethrough, baseUnderline = baseUnderline, baseHighlight = baseHighlight, baseLinkUrl = baseLinkUrl))
                        i = closeIdx + 1
                        continue
                    }
                }
            }

            // 11. Bare URLs: https://... or http://...
            if ((ch == 'h' || ch == 'H') && (text.startsWith("http://", i, ignoreCase = true) || text.startsWith("https://", i, ignoreCase = true))) {
                val urlEnd = findUrlEnd(text, i)
                if (urlEnd > i) {
                    flushPlain()
                    val url = text.substring(i, urlEnd)
                    inlines.add(MarkdownInline(text = url, linkUrl = url))
                    i = urlEnd
                    continue
                }
            }

            plainBuilder.append(ch)
            i++
        }

        flushPlain()
        return collapseAdjacentInlines(inlines)
    }

    private fun findClosingDelimiter(text: String, delim: String, startIndex: Int): Int {
        var pos = startIndex
        val dLen = delim.length
        while (pos <= text.length - dLen) {
            if (text.startsWith(delim, pos)) {
                var backslashCount = 0
                var b = pos - 1
                while (b >= 0 && text[b] == '\\') {
                    backslashCount++
                    b--
                }
                if (backslashCount % 2 == 0) {
                    val prevChar = text[pos - 1]
                    if (!prevChar.isWhitespace()) {
                        if (delim == "_" && pos + 1 < text.length && text[pos + 1].isLetterOrDigit()) {
                            pos++
                            continue
                        }
                        return pos
                    }
                }
            }
            pos++
        }
        return -1
    }

    private fun findUrlEnd(text: String, startIndex: Int): Int {
        var end = startIndex
        while (end < text.length && !text[end].isWhitespace()) {
            end++
        }
        while (end > startIndex && ".,!?:;)\">]".contains(text[end - 1])) {
            if (text[end - 1] == ')') {
                val openCount = text.substring(startIndex, end).count { it == '(' }
                val closeCount = text.substring(startIndex, end).count { it == ')' }
                if (closeCount <= openCount) break
            }
            end--
        }
        return end
    }

    private fun collapseAdjacentInlines(inlines: List<MarkdownInline>): List<MarkdownInline> {
        if (inlines.size <= 1) return inlines
        val result = mutableListOf<MarkdownInline>()
        var current = inlines[0]

        for (j in 1 until inlines.size) {
            val next = inlines[j]
            if (current.isBold == next.isBold &&
                current.isItalic == next.isItalic &&
                current.isCode == next.isCode &&
                current.isStrikethrough == next.isStrikethrough &&
                current.isUnderline == next.isUnderline &&
                current.isHighlight == next.isHighlight &&
                current.linkUrl == next.linkUrl &&
                current.imageUrl == null && next.imageUrl == null
            ) {
                current = current.copy(text = current.text + next.text)
            } else {
                result.add(current)
                current = next
            }
        }
        result.add(current)
        return result
    }

    private fun extractPrintableText(file: File): String {
        return try {
            val sb = StringBuilder()
            file.inputStream().use { input ->
                val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
                var line = reader.readLine()
                var count = 0
                while (line != null && count < 2000) {
                    val clean = line.filter { it.isLetterOrDigit() || it.isWhitespace() || it in ".,!?:;-_()[]{}/\\'\"#@%&*+=<>" }
                    if (clean.length > 3) {
                        sb.append(clean).append("\n")
                    }
                    line = reader.readLine()
                    count++
                }
            }
            sb.toString()
        } catch (e: Exception) {
            ""
        }
    }
}
