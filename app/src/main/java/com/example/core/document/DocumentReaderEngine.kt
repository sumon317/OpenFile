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
    val linkUrl: String? = null
)

sealed interface MarkdownNode {
    data class Header(val level: Int, val text: String) : MarkdownNode
    data class Paragraph(val inlines: List<MarkdownInline>) : MarkdownNode
    data class CodeBlock(val language: String, val code: String) : MarkdownNode
    data class Blockquote(val text: String) : MarkdownNode
    data class ListItem(val text: String, val isOrdered: Boolean, val orderNumber: Int = 1) : MarkdownNode
    data class ChecklistItem(val text: String, val isChecked: Boolean) : MarkdownNode
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownNode
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

            // 1. Code Block
            if (trimmed.startsWith("```")) {
                val lang = trimmed.removePrefix("```").trim()
                val codeBuilder = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    codeBuilder.append(lines[i]).append("\n")
                    i++
                }
                nodes.add(MarkdownNode.CodeBlock(lang, codeBuilder.toString().trimEnd()))
                i++
                continue
            }

            // 2. Horizontal Rule
            if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
                nodes.add(MarkdownNode.HorizontalRule)
                i++
                continue
            }

            // 3. Header (# to ######)
            if (trimmed.startsWith("#")) {
                val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 6)
                val headerText = trimmed.drop(level).trim()
                nodes.add(MarkdownNode.Header(level, headerText))
                i++
                continue
            }

            // 4. Blockquote (> )
            if (trimmed.startsWith(">")) {
                val quoteText = trimmed.removePrefix(">").trim()
                nodes.add(MarkdownNode.Blockquote(quoteText))
                i++
                continue
            }

            // 5. Checklist (- [ ] or - [x])
            if (trimmed.startsWith("- [ ] ") || trimmed.startsWith("* [ ] ")) {
                nodes.add(MarkdownNode.ChecklistItem(trimmed.drop(6).trim(), isChecked = false))
                i++
                continue
            }
            if (trimmed.startsWith("- [x] ") || trimmed.startsWith("* [x] ") ||
                trimmed.startsWith("- [X] ") || trimmed.startsWith("* [X] ")) {
                nodes.add(MarkdownNode.ChecklistItem(trimmed.drop(6).trim(), isChecked = true))
                i++
                continue
            }

            // 6. Bullet List (- or *)
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                nodes.add(MarkdownNode.ListItem(trimmed.drop(2).trim(), isOrdered = false))
                i++
                continue
            }

            // 7. Numbered List (1. )
            val numberMatch = Regex("^(\\d+)\\.\\s+(.*)").find(trimmed)
            if (numberMatch != null) {
                val num = numberMatch.groupValues[1].toIntOrNull() ?: 1
                val content = numberMatch.groupValues[2]
                nodes.add(MarkdownNode.ListItem(content, isOrdered = true, orderNumber = num))
                i++
                continue
            }

            // 8. Markdown Table (| Col 1 | Col 2 |)
            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                val headers = trimmed.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                val tableRows = mutableListOf<List<String>>()
                i++
                // Skip separator row (|---|---|)
                if (i < lines.size && lines[i].trim().startsWith("|") && lines[i].contains("---")) {
                    i++
                }
                while (i < lines.size && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                    val rowCells = lines[i].trim().split("|").map { it.trim() }.filter { it.isNotEmpty() }
                    tableRows.add(rowCells)
                    i++
                }
                nodes.add(MarkdownNode.Table(headers, tableRows))
                continue
            }

            // 9. Standard Paragraph (with inline formatting)
            if (trimmed.isNotBlank()) {
                val inlines = parseMarkdownInlines(trimmed)
                nodes.add(MarkdownNode.Paragraph(inlines))
            }

            i++
        }

        return MarkdownDocument(title = title, nodes = nodes, rawText = text)
    }

    private fun parseMarkdownInlines(text: String): List<MarkdownInline> {
        val inlines = mutableListOf<MarkdownInline>()
        var remaining = text

        while (remaining.isNotEmpty()) {
            // Bold & Italic (***text***)
            if (remaining.startsWith("***")) {
                val end = remaining.indexOf("***", 3)
                if (end != -1) {
                    val content = remaining.substring(3, end)
                    inlines.add(MarkdownInline(content, isBold = true, isItalic = true))
                    remaining = remaining.substring(end + 3)
                    continue
                }
            }

            // Bold (**text** or __text__)
            if (remaining.startsWith("**") || remaining.startsWith("__")) {
                val delim = remaining.substring(0, 2)
                val end = remaining.indexOf(delim, 2)
                if (end != -1) {
                    val content = remaining.substring(2, end)
                    inlines.add(MarkdownInline(content, isBold = true))
                    remaining = remaining.substring(end + 2)
                    continue
                }
            }

            // Strikethrough (~~text~~)
            if (remaining.startsWith("~~")) {
                val end = remaining.indexOf("~~", 2)
                if (end != -1) {
                    val content = remaining.substring(2, end)
                    inlines.add(MarkdownInline(content, isStrikethrough = true))
                    remaining = remaining.substring(end + 2)
                    continue
                }
            }

            // Inline Code (`code`)
            if (remaining.startsWith("`")) {
                val end = remaining.indexOf("`", 1)
                if (end != -1) {
                    val content = remaining.substring(1, end)
                    inlines.add(MarkdownInline(content, isCode = true))
                    remaining = remaining.substring(end + 1)
                    continue
                }
            }

            // Link ([title](url))
            if (remaining.startsWith("[")) {
                val closeBracket = remaining.indexOf("](")
                if (closeBracket != -1) {
                    val closeParen = remaining.indexOf(")", closeBracket + 2)
                    if (closeParen != -1) {
                        val linkTitle = remaining.substring(1, closeBracket)
                        val linkUrl = remaining.substring(closeBracket + 2, closeParen)
                        inlines.add(MarkdownInline(linkTitle, linkUrl = linkUrl))
                        remaining = remaining.substring(closeParen + 1)
                        continue
                    }
                }
            }

            // Italic (*text* or _text_)
            if (remaining.startsWith("*") || remaining.startsWith("_")) {
                val delim = remaining.substring(0, 1)
                val end = remaining.indexOf(delim, 1)
                if (end != -1 && end > 1) {
                    val content = remaining.substring(1, end)
                    inlines.add(MarkdownInline(content, isItalic = true))
                    remaining = remaining.substring(end + 1)
                    continue
                }
            }

            // Plain text run until next special character
            val nextSpecial = remaining.indexOfAny(listOf("**", "__", "*", "_", "~~", "`", "[", "***"))
            if (nextSpecial == -1) {
                inlines.add(MarkdownInline(remaining))
                break
            } else if (nextSpecial == 0) {
                inlines.add(MarkdownInline(remaining.substring(0, 1)))
                remaining = remaining.substring(1)
            } else {
                inlines.add(MarkdownInline(remaining.substring(0, nextSpecial)))
                remaining = remaining.substring(nextSpecial)
            }
        }

        return inlines
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
