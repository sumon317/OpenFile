package com.example.core.document

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * High-performance, streaming spreadsheet parser for OOXML (.xlsx), OpenDocument (.ods),
 * and Delimited text (.csv, .tsv).
 *
 * Implements zip safety checks, sparse storage (preventing dense matrix allocations),
 * full styles.xml resolution (fonts, colors, fills, borders, alignments, and number formats),
 * frozen pane detection, merged cell resolution, and formula preservation.
 */
object SpreadsheetParser {

    private const val MAX_SHEET_ROWS = 200_000
    private const val MAX_SHEET_COLS = 1_000
    private const val MAX_REPEATED_CELLS = 1_000

    private val STANDARD_THEME_COLORS = intArrayOf(
        0xFFFFFFFF.toInt(), // 0: lt1 (white)
        0xFF000000.toInt(), // 1: dk1 (black)
        0xFFE7E6E6.toInt(), // 2: lt2
        0xFF44546A.toInt(), // 3: dk2
        0xFF5B9BD5.toInt(), // 4: accent1
        0xFFED7D31.toInt(), // 5: accent2
        0xFFA5A5A5.toInt(), // 6: accent3
        0xFFFFC000.toInt(), // 7: accent4
        0xFF4472C4.toInt(), // 8: accent5
        0xFF70AD47.toInt()  // 9: accent6
    )

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

    // =============================================================================================
    // Entry Point: Parse any Spreadsheet File (XLSX, ODS, CSV, TSV)
    // =============================================================================================

    fun parse(file: File): SpreadsheetWorkbookModel {
        val detected = DocumentFormatDetector.detect(file)
        return when (detected) {
            is DetectedDocumentType.Xlsx -> parseXlsx(file)
            is DetectedDocumentType.Ods -> parseOds(file)
            is DetectedDocumentType.Csv -> parseDelimited(file, defaultDelimiter = ',')
            is DetectedDocumentType.Tsv -> parseDelimited(file, defaultDelimiter = '\t')
            is DetectedDocumentType.LegacyOle2 -> {
                throw UnsupportedOperationException("Legacy binary format (.xls) is not supported. Please convert to .xlsx or .ods.")
            }
            is DetectedDocumentType.EncryptedPasswordProtected -> {
                throw SecurityException("This spreadsheet is password protected and cannot be previewed.")
            }
            else -> {
                // Fallback attempt based on extension or CSV
                if (file.extension.equals("xlsx", ignoreCase = true)) {
                    parseXlsx(file)
                } else if (file.extension.equals("ods", ignoreCase = true)) {
                    parseOds(file)
                } else {
                    parseDelimited(file, defaultDelimiter = ',')
                }
            }
        }
    }

    // =============================================================================================
    // 1. OOXML (.xlsx) Parser
    // =============================================================================================

    fun parseXlsx(file: File): SpreadsheetWorkbookModel {
        var date1904 = false
        val sheets = mutableListOf<SpreadsheetSheetModel>()

        ZipFile(file).use { zip ->
            // 1. Parse Workbook metadata (xl/workbook.xml)
            val sheetInfoList = mutableListOf<Triple<String, String, Boolean>>() // Name, RelId/Path, isHidden
            val wbEntry = zip.getEntry("xl/workbook.xml")
            if (wbEntry != null) {
                zip.getInputStream(wbEntry).use { stream ->
                    val bounded = DocumentFormatDetector.createSafeBoundedStream(stream)
                    val (info, is1904) = parseWorkbookXml(bounded)
                    sheetInfoList.addAll(info)
                    date1904 = is1904
                }
            }

            // 2. Parse Shared Strings (xl/sharedStrings.xml)
            val sharedStrings = mutableListOf<String>()
            val sstEntry = zip.getEntry("xl/sharedStrings.xml")
            if (sstEntry != null) {
                zip.getInputStream(sstEntry).use { stream ->
                    val bounded = DocumentFormatDetector.createSafeBoundedStream(stream)
                    parseSharedStrings(bounded, sharedStrings)
                }
            }

            // 3. Parse Styles (xl/styles.xml)
            val styles = mutableListOf<CellStyle>()
            val stylesEntry = zip.getEntry("xl/styles.xml")
            if (stylesEntry != null) {
                zip.getInputStream(stylesEntry).use { stream ->
                    val bounded = DocumentFormatDetector.createSafeBoundedStream(stream)
                    parseStyles(bounded, styles)
                }
            }

            // 4. Discover Worksheet entries
            val worksheetEntries = zip.entries().asSequence()
                .filter { it.name.startsWith("xl/worksheets/sheet") && it.name.endsWith(".xml") }
                .sortedWith { a, b -> DocumentReaderEngine.naturalCompare(a.name, b.name) }
                .toList()

            worksheetEntries.forEachIndexed { index, entry ->
                val meta = sheetInfoList.getOrNull(index)
                val sheetName = meta?.first ?: "Sheet ${index + 1}"
                val isHidden = meta?.third ?: false

                zip.getInputStream(entry).use { stream ->
                    val bounded = DocumentFormatDetector.createSafeBoundedStream(stream)
                    val sheetModel = parseWorksheetXml(
                        stream = bounded,
                        sheetName = sheetName,
                        isHidden = isHidden,
                        sharedStrings = sharedStrings,
                        styles = styles,
                        date1904 = date1904
                    )
                    sheets.add(sheetModel)
                }
            }
        }

        if (sheets.isEmpty()) {
            sheets.add(
                SpreadsheetSheetModel(
                    name = "Sheet 1",
                    rowCount = 1,
                    colCount = 1,
                    rows = mapOf(0 to mapOf(0 to SpreadsheetCell(0, 0, displayValue = "Empty spreadsheet")))
                )
            )
        }

        return SpreadsheetWorkbookModel(
            title = file.name,
            sheets = sheets,
            date1904 = date1904
        )
    }

    private fun parseWorkbookXml(stream: InputStream): Pair<List<Triple<String, String, Boolean>>, Boolean> {
        val parser = createXmlPullParser()
        parser.setInput(stream, "UTF-8")
        val sheets = mutableListOf<Triple<String, String, Boolean>>()
        var date1904 = false

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':') ?: ""
            if (eventType == XmlPullParser.START_TAG) {
                if (name == "workbookPr") {
                    val d1904 = parser.getAttributeValue(null, "date1904")
                    date1904 = d1904 == "1" || d1904.equals("true", ignoreCase = true)
                } else if (name == "sheet") {
                    val sName = parser.getAttributeValue(null, "name") ?: "Sheet"
                    val state = parser.getAttributeValue(null, "state")
                    val isHidden = state.equals("hidden", ignoreCase = true) || state.equals("veryHidden", ignoreCase = true)
                    sheets.add(Triple(sName, "", isHidden))
                }
            }
            eventType = parser.next()
        }
        return sheets to date1904
    }

    private fun parseSharedStrings(stream: InputStream, sink: MutableList<String>) {
        val parser = createXmlPullParser()
        parser.setInput(stream, "UTF-8")
        val currentText = StringBuilder()
        var inText = false
        var inPhonetic = false

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':') ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (name == "si") {
                        currentText.setLength(0)
                    } else if (name == "rPh") {
                        inPhonetic = true
                    } else if (name == "t" && !inPhonetic) {
                        inText = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inText && !inPhonetic) {
                        currentText.append(parser.text ?: "")
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (name == "t") {
                        inText = false
                    } else if (name == "rPh") {
                        inPhonetic = false
                    } else if (name == "si") {
                        sink.add(currentText.toString())
                    }
                }
            }
            eventType = parser.next()
        }
    }

    private fun parseStyles(stream: InputStream, sink: MutableList<CellStyle>) {
        val parser = createXmlPullParser()
        parser.setInput(stream, "UTF-8")

        val numFmts = mutableMapOf<Int, String>()
        val fonts = mutableListOf<CellFont>()
        val fills = mutableListOf<CellFill>()
        val borders = mutableListOf<CellBorders>()

        var inFonts = false
        var inFills = false
        var inBorders = false
        var inCellXfs = false

        var curFont = CellFont()
        var curFill = CellFill()
        var curBorders = CellBorders()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':') ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (name) {
                        "numFmt" -> {
                            val id = parser.getAttributeValue(null, "numFmtId")?.toIntOrNull()
                            val code = parser.getAttributeValue(null, "formatCode")
                            if (id != null && code != null) {
                                numFmts[id] = code
                            }
                        }
                        "fonts" -> inFonts = true
                        "font" -> if (inFonts) curFont = CellFont()
                        "b" -> if (inFonts) curFont = curFont.copy(isBold = true)
                        "i" -> if (inFonts) curFont = curFont.copy(isItalic = true)
                        "u" -> if (inFonts) curFont = curFont.copy(isUnderline = true)
                        "strike" -> if (inFonts) curFont = curFont.copy(isStrike = true)
                        "sz" -> {
                            if (inFonts) {
                                val sz = parser.getAttributeValue(null, "val")?.toFloatOrNull() ?: 11f
                                curFont = curFont.copy(sizePt = sz)
                            }
                        }
                        "name" -> {
                            if (inFonts) {
                                val fontName = parser.getAttributeValue(null, "val")
                                curFont = curFont.copy(name = fontName)
                            }
                        }
                        "color" -> {
                            if (inFonts) {
                                val color = parseColor(parser)
                                if (color != null) curFont = curFont.copy(colorRgb = color)
                            }
                        }
                        "fills" -> inFills = true
                        "fill" -> if (inFills) curFill = CellFill()
                        "patternFill" -> {
                            if (inFills) {
                                val pattern = parser.getAttributeValue(null, "patternType")
                                curFill = curFill.copy(pattern = pattern)
                            }
                        }
                        "fgColor" -> {
                            if (inFills) {
                                val color = parseColor(parser)
                                if (color != null) curFill = curFill.copy(fgColorRgb = color)
                            }
                        }
                        "bgColor" -> {
                            if (inFills) {
                                val color = parseColor(parser)
                                if (color != null) curFill = curFill.copy(bgColorRgb = color)
                            }
                        }
                        "borders" -> inBorders = true
                        "border" -> if (inBorders) curBorders = CellBorders()
                        "left" -> if (inBorders) curBorders = curBorders.copy(left = parseBorder(parser))
                        "right" -> if (inBorders) curBorders = curBorders.copy(right = parseBorder(parser))
                        "top" -> if (inBorders) curBorders = curBorders.copy(top = parseBorder(parser))
                        "bottom" -> if (inBorders) curBorders = curBorders.copy(bottom = parseBorder(parser))
                        "cellXfs" -> inCellXfs = true
                        "xf" -> {
                            if (inCellXfs) {
                                val numFmtId = parser.getAttributeValue(null, "numFmtId")?.toIntOrNull() ?: 0
                                val fontId = parser.getAttributeValue(null, "fontId")?.toIntOrNull() ?: 0
                                val fillId = parser.getAttributeValue(null, "fillId")?.toIntOrNull() ?: 0
                                val borderId = parser.getAttributeValue(null, "borderId")?.toIntOrNull() ?: 0

                                val font = fonts.getOrNull(fontId) ?: CellFont()
                                val fill = fills.getOrNull(fillId) ?: CellFill()
                                val border = borders.getOrNull(borderId) ?: CellBorders()
                                val formatCode = numFmts[numFmtId]

                                sink.add(
                                    CellStyle(
                                        id = sink.size,
                                        numFmtId = numFmtId,
                                        formatCode = formatCode,
                                        font = font,
                                        fill = fill,
                                        borders = border
                                    )
                                )
                            }
                        }
                        "alignment" -> {
                            if (inCellXfs && sink.isNotEmpty()) {
                                val lastIdx = sink.size - 1
                                val hAlignStr = parser.getAttributeValue(null, "horizontal")
                                val vAlignStr = parser.getAttributeValue(null, "vertical")
                                val wrap = parser.getAttributeValue(null, "wrapText")
                                val indentStr = parser.getAttributeValue(null, "indent")

                                val hAlign = when (hAlignStr?.lowercase()) {
                                    "left" -> CellHorizontalAlignment.LEFT
                                    "center" -> CellHorizontalAlignment.CENTER
                                    "right" -> CellHorizontalAlignment.RIGHT
                                    "justify" -> CellHorizontalAlignment.JUSTIFY
                                    else -> CellHorizontalAlignment.GENERAL
                                }
                                val vAlign = when (vAlignStr?.lowercase()) {
                                    "top" -> CellVerticalAlignment.TOP
                                    "bottom" -> CellVerticalAlignment.BOTTOM
                                    else -> CellVerticalAlignment.CENTER
                                }

                                sink[lastIdx] = sink[lastIdx].copy(
                                    horizontalAlign = hAlign,
                                    verticalAlign = vAlign,
                                    wrapText = wrap == "1" || wrap.equals("true", ignoreCase = true),
                                    indent = indentStr?.toIntOrNull() ?: 0
                                )
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (name) {
                        "fonts" -> inFonts = false
                        "font" -> if (inFonts) fonts.add(curFont)
                        "fills" -> inFills = false
                        "fill" -> if (inFills) fills.add(curFill)
                        "borders" -> inBorders = false
                        "border" -> if (inBorders) borders.add(curBorders)
                        "cellXfs" -> inCellXfs = false
                    }
                }
            }
            eventType = parser.next()
        }

        if (sink.isEmpty()) {
            sink.add(CellStyle())
        }
    }

    private fun parseColor(parser: XmlPullParser): Int? {
        val rgb = parser.getAttributeValue(null, "rgb")
        if (!rgb.isNullOrBlank()) {
            return try {
                if (rgb.length == 8) {
                    rgb.toLong(16).toInt()
                } else if (rgb.length == 6) {
                    (0xFF000000L or rgb.toLong(16)).toInt()
                } else null
            } catch (_: Exception) {
                null
            }
        }
        val theme = parser.getAttributeValue(null, "theme")?.toIntOrNull()
        if (theme != null && theme in STANDARD_THEME_COLORS.indices) {
            return STANDARD_THEME_COLORS[theme]
        }
        return null
    }

    private fun parseBorder(parser: XmlPullParser): CellBorder {
        val styleStr = parser.getAttributeValue(null, "style")
        val style = when (styleStr?.lowercase()) {
            "thin" -> BorderStyle.THIN
            "medium" -> BorderStyle.MEDIUM
            "thick" -> BorderStyle.THICK
            "dashed" -> BorderStyle.DASHED
            "dotted" -> BorderStyle.DOTTED
            "double" -> BorderStyle.DOUBLE
            else -> BorderStyle.NONE
        }
        return CellBorder(style = style)
    }

    private fun parseWorksheetXml(
        stream: InputStream,
        sheetName: String,
        isHidden: Boolean,
        sharedStrings: List<String>,
        styles: List<CellStyle>,
        date1904: Boolean
    ): SpreadsheetSheetModel {
        val parser = createXmlPullParser()
        parser.setInput(stream, "UTF-8")

        val sparseRows = mutableMapOf<Int, MutableMap<Int, SpreadsheetCell>>()
        val columns = mutableMapOf<Int, ColumnPr>()
        val rowsMeta = mutableMapOf<Int, RowPr>()
        val mergedRanges = mutableListOf<CellRange>()

        var frozenRows = 0
        var frozenCols = 0
        var showGridLines = true
        var tabColor: Int? = null

        var currentRowIdx = 0
        var currentColIdx = 0
        var currentCellType: String? = null
        var currentCellStyleIdx = 0
        var inValue = false
        var inFormula = false
        var inInlineStr = false
        val cellValText = StringBuilder()
        val formulaText = StringBuilder()

        var maxColIndexEncountered = 0
        var maxRowIndexEncountered = 0

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':') ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (name) {
                        "sheetView" -> {
                            val gridLines = parser.getAttributeValue(null, "showGridLines")
                            if (gridLines != null) {
                                showGridLines = gridLines == "1" || gridLines.equals("true", ignoreCase = true)
                            }
                        }
                        "pane" -> {
                            val xSplit = parser.getAttributeValue(null, "xSplit")?.toIntOrNull() ?: 0
                            val ySplit = parser.getAttributeValue(null, "ySplit")?.toIntOrNull() ?: 0
                            val state = parser.getAttributeValue(null, "state")
                            if (state.equals("frozen", ignoreCase = true) || state == null) {
                                frozenCols = xSplit
                                frozenRows = ySplit
                            }
                        }
                        "tabColor" -> {
                            tabColor = parseColor(parser)
                        }
                        "col" -> {
                            val min = parser.getAttributeValue(null, "min")?.toIntOrNull() ?: 1
                            val max = parser.getAttributeValue(null, "max")?.toIntOrNull() ?: min
                            val width = parser.getAttributeValue(null, "width")?.toFloatOrNull()
                            val colHidden = parser.getAttributeValue(null, "hidden") == "1"
                            for (c in (min - 1)..(max - 1)) {
                                if (c < MAX_SHEET_COLS) {
                                    columns[c] = ColumnPr(c, widthChars = width, isHidden = colHidden)
                                }
                            }
                        }
                        "row" -> {
                            val r = parser.getAttributeValue(null, "r")?.toIntOrNull()
                            currentRowIdx = if (r != null) r - 1 else currentRowIdx + 1
                            val ht = parser.getAttributeValue(null, "ht")?.toFloatOrNull()
                            val rowHidden = parser.getAttributeValue(null, "hidden") == "1"
                            if (currentRowIdx < MAX_SHEET_ROWS) {
                                rowsMeta[currentRowIdx] = RowPr(currentRowIdx, heightPt = ht, isHidden = rowHidden)
                                maxRowIndexEncountered = maxOf(maxRowIndexEncountered, currentRowIdx)
                            }
                        }
                        "c" -> {
                            val ref = parser.getAttributeValue(null, "r")
                            if (ref != null) {
                                val (cIdx, rIdx) = parseCellRef(ref)
                                if (rIdx != null) currentRowIdx = rIdx
                                if (cIdx != null) currentColIdx = cIdx
                            }
                            currentCellType = parser.getAttributeValue(null, "t")
                            currentCellStyleIdx = parser.getAttributeValue(null, "s")?.toIntOrNull() ?: 0
                            cellValText.setLength(0)
                            formulaText.setLength(0)
                        }
                        "v" -> inValue = true
                        "f" -> inFormula = true
                        "is" -> inInlineStr = true
                        "t" -> if (inInlineStr) inValue = true
                        "mergeCell" -> {
                            val ref = parser.getAttributeValue(null, "ref")
                            if (ref != null) {
                                val range = parseRangeRef(ref)
                                if (range != null) mergedRanges.add(range)
                            }
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inValue) {
                        cellValText.append(parser.text ?: "")
                    } else if (inFormula) {
                        formulaText.append(parser.text ?: "")
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (name) {
                        "v", "t" -> inValue = false
                        "f" -> inFormula = false
                        "is" -> inInlineStr = false
                        "c" -> {
                            if (currentRowIdx < MAX_SHEET_ROWS && currentColIdx < MAX_SHEET_COLS) {
                                val raw = cellValText.toString().trim()
                                val formula = formulaText.toString().takeIf { it.isNotBlank() }
                                val style = styles.getOrNull(currentCellStyleIdx) ?: CellStyle()

                                val cellTypeEnum = when (currentCellType) {
                                    "s" -> CellType.STRING
                                    "b" -> CellType.BOOLEAN
                                    "e" -> CellType.ERROR
                                    "str" -> CellType.FORMULA_STRING
                                    "d" -> CellType.DATE
                                    else -> CellType.NUMBER
                                }

                                val resolvedRaw = if (currentCellType == "s") {
                                    val idx = raw.toIntOrNull() ?: -1
                                    if (idx in sharedStrings.indices) sharedStrings[idx] else raw
                                } else {
                                    raw
                                }

                                val formatResult = CellFormatter.format(
                                    rawValue = resolvedRaw,
                                    numFmtId = style.numFmtId,
                                    explicitCode = style.formatCode,
                                    date1904 = date1904,
                                    cellType = cellTypeEnum
                                )

                                val cell = SpreadsheetCell(
                                    rowIndex = currentRowIdx,
                                    colIndex = currentColIdx,
                                    rawValue = resolvedRaw,
                                    displayValue = formatResult.text,
                                    formula = formula,
                                    cellType = if (formatResult.isDate) CellType.DATE else cellTypeEnum,
                                    style = if (formatResult.textColorRgb != null) {
                                        style.copy(font = style.font.copy(colorRgb = formatResult.textColorRgb))
                                    } else {
                                        style
                                    }
                                )

                                val rowMap = sparseRows.getOrPut(currentRowIdx) { mutableMapOf() }
                                rowMap[currentColIdx] = cell
                                maxColIndexEncountered = maxOf(maxColIndexEncountered, currentColIdx)
                            }
                            currentColIdx++
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return SpreadsheetSheetModel(
            name = sheetName,
            rowCount = (maxRowIndexEncountered + 1).coerceAtLeast(1),
            colCount = (maxColIndexEncountered + 1).coerceAtLeast(1),
            rows = sparseRows,
            mergedRanges = mergedRanges,
            columns = columns,
            rowsMeta = rowsMeta,
            view = SheetViewPr(
                frozenRows = frozenRows,
                frozenCols = frozenCols,
                showGridLines = showGridLines,
                tabColorRgb = tabColor,
                isHidden = isHidden
            )
        )
    }

    /**
     * Converts coordinate reference like "B14" to Pair(colIndex=1, rowIndex=13).
     */
    fun parseCellRef(ref: String): Pair<Int?, Int?> {
        var colPart = ""
        var rowPart = ""
        for (ch in ref) {
            if (ch.isLetter()) colPart += ch
            else if (ch.isDigit()) rowPart += ch
        }
        val col = if (colPart.isNotEmpty()) columnLetterToIndex(colPart) else null
        val row = rowPart.toIntOrNull()?.let { it - 1 }
        return col to row
    }

    /**
     * Converts a range reference like "A1:C5" into a CellRange.
     */
    fun parseRangeRef(ref: String): CellRange? {
        val parts = ref.split(':')
        if (parts.isEmpty()) return null
        val (firstCol, firstRow) = parseCellRef(parts[0])
        if (firstCol == null || firstRow == null) return null
        if (parts.size == 1) {
            return CellRange(firstRow, firstRow, firstCol, firstCol)
        }
        val (lastCol, lastRow) = parseCellRef(parts[1])
        return CellRange(
            firstRow = firstRow,
            lastRow = lastRow ?: firstRow,
            firstCol = firstCol,
            lastCol = lastCol ?: firstCol
        )
    }

    fun columnLetterToIndex(colStr: String): Int {
        var index = 0
        for (ch in colStr.uppercase()) {
            if (ch in 'A'..'Z') {
                index = index * 26 + (ch - 'A' + 1)
            }
        }
        return (index - 1).coerceAtLeast(0)
    }

    fun indexToColumnLetter(index: Int): String {
        var num = index + 1
        val sb = StringBuilder()
        while (num > 0) {
            val rem = (num - 1) % 26
            sb.append(('A'.toInt() + rem).toChar())
            num = (num - 1) / 26
        }
        return sb.reverse().toString()
    }

    // =============================================================================================
    // 2. OpenDocument Spreadsheet (.ods) Parser
    // =============================================================================================

    fun parseOds(file: File): SpreadsheetWorkbookModel {
        val sheets = mutableListOf<SpreadsheetSheetModel>()
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("content.xml")
            if (entry != null) {
                zip.getInputStream(entry).use { stream ->
                    val bounded = DocumentFormatDetector.createSafeBoundedStream(stream)
                    sheets.addAll(parseOdsContentXml(bounded))
                }
            }
        }

        if (sheets.isEmpty()) {
            sheets.add(
                SpreadsheetSheetModel(
                    name = "Sheet 1",
                    rowCount = 1,
                    colCount = 1,
                    rows = mapOf(0 to mapOf(0 to SpreadsheetCell(0, 0, displayValue = "Empty ODS spreadsheet")))
                )
            )
        }

        return SpreadsheetWorkbookModel(title = file.name, sheets = sheets)
    }

    private fun parseOdsContentXml(stream: InputStream): List<SpreadsheetSheetModel> {
        val parser = createXmlPullParser()
        parser.setInput(stream, "UTF-8")

        val sheets = mutableListOf<SpreadsheetSheetModel>()
        var currentSheetName = "Sheet 1"
        var sparseRows = mutableMapOf<Int, MutableMap<Int, SpreadsheetCell>>()
        var mergedRanges = mutableListOf<CellRange>()
        var currentRowIdx = 0
        var currentColIdx = 0
        var maxCols = 0

        var inCell = false
        var cellType: String? = null
        var cellValue: String? = null
        var formula: String? = null
        var colsSpanned = 1
        var rowsSpanned = 1
        val cellText = StringBuilder()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.substringAfter(':') ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (name) {
                        "table" -> {
                            currentSheetName = parser.getAttributeValue(null, "name") ?: "Sheet ${sheets.size + 1}"
                            sparseRows = mutableMapOf()
                            mergedRanges = mutableListOf()
                            currentRowIdx = 0
                            maxCols = 0
                        }
                        "table-row" -> {
                            currentColIdx = 0
                        }
                        "table-cell", "covered-table-cell" -> {
                            inCell = true
                            cellType = parser.getAttributeValue(null, "value-type")
                            cellValue = parser.getAttributeValue(null, "value")
                                ?: parser.getAttributeValue(null, "date-value")
                                ?: parser.getAttributeValue(null, "time-value")
                            formula = parser.getAttributeValue(null, "formula")
                            colsSpanned = parser.getAttributeValue(null, "number-columns-spanned")?.toIntOrNull() ?: 1
                            rowsSpanned = parser.getAttributeValue(null, "number-rows-spanned")?.toIntOrNull() ?: 1
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
                        "table-cell", "covered-table-cell" -> {
                            inCell = false
                            val repeatedCols = (parser.getAttributeValue(null, "number-columns-repeated")?.toIntOrNull() ?: 1)
                                .coerceIn(1, MAX_REPEATED_CELLS)

                            val displayText = cellText.toString().trim().ifBlank { cellValue ?: "" }

                            if (displayText.isNotBlank() || formula != null) {
                                val cell = SpreadsheetCell(
                                    rowIndex = currentRowIdx,
                                    colIndex = currentColIdx,
                                    rawValue = cellValue ?: displayText,
                                    displayValue = displayText,
                                    formula = formula,
                                    cellType = when (cellType?.lowercase()) {
                                        "float", "percentage", "currency" -> CellType.NUMBER
                                        "date", "time" -> CellType.DATE
                                        "boolean" -> CellType.BOOLEAN
                                        else -> CellType.STRING
                                    }
                                )
                                val rMap = sparseRows.getOrPut(currentRowIdx) { mutableMapOf() }
                                rMap[currentColIdx] = cell

                                if (colsSpanned > 1 || rowsSpanned > 1) {
                                    mergedRanges.add(
                                        CellRange(
                                            firstRow = currentRowIdx,
                                            lastRow = currentRowIdx + rowsSpanned - 1,
                                            firstCol = currentColIdx,
                                            lastCol = currentColIdx + colsSpanned - 1
                                        )
                                    )
                                }
                            }
                            currentColIdx += repeatedCols
                            maxCols = maxOf(maxCols, currentColIdx)
                        }
                        "table-row" -> {
                            val repeatedRows = (parser.getAttributeValue(null, "number-rows-repeated")?.toIntOrNull() ?: 1)
                                .coerceIn(1, MAX_REPEATED_CELLS)
                            currentRowIdx += repeatedRows
                        }
                        "table" -> {
                            if (sparseRows.isNotEmpty()) {
                                sheets.add(
                                    SpreadsheetSheetModel(
                                        name = currentSheetName,
                                        rowCount = currentRowIdx.coerceAtLeast(1),
                                        colCount = maxCols.coerceAtLeast(1),
                                        rows = sparseRows,
                                        mergedRanges = mergedRanges
                                    )
                                )
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return sheets
    }

    // =============================================================================================
    // 3. Delimited Text (.csv, .tsv, semicolon, pipe) Parser
    // =============================================================================================

    fun parseDelimited(file: File, defaultDelimiter: Char = ','): SpreadsheetWorkbookModel {
        val reader = detectEncodingAndCreateReader(file)
        val delimiter = autoDetectDelimiter(file, defaultDelimiter)
        val sparseRows = mutableMapOf<Int, MutableMap<Int, SpreadsheetCell>>()
        var rowCount = 0
        var maxCols = 0

        reader.use { br ->
            val rows = parseCsvRows(br, delimiter)
            for ((rIdx, row) in rows.withIndex()) {
                if (rIdx >= MAX_SHEET_ROWS) break
                val rowMap = mutableMapOf<Int, SpreadsheetCell>()
                for ((cIdx, cellVal) in row.withIndex()) {
                    if (cIdx >= MAX_SHEET_COLS) break
                    rowMap[cIdx] = SpreadsheetCell(
                        rowIndex = rIdx,
                        colIndex = cIdx,
                        rawValue = cellVal,
                        displayValue = cellVal,
                        cellType = CellType.STRING
                    )
                }
                sparseRows[rIdx] = rowMap
                maxCols = maxOf(maxCols, row.size)
                rowCount++
            }
        }

        val sheet = SpreadsheetSheetModel(
            name = file.nameWithoutExtension.ifBlank { "Sheet 1" },
            rowCount = rowCount.coerceAtLeast(1),
            colCount = maxCols.coerceAtLeast(1),
            rows = sparseRows
        )

        return SpreadsheetWorkbookModel(title = file.name, sheets = listOf(sheet))
    }

    private fun autoDetectDelimiter(file: File, defaultDelimiter: Char): Char {
        return try {
            file.bufferedReader().use { br ->
                val sampleLines = (0 until 10).mapNotNull { br.readLine() }.filter { it.isNotBlank() }
                if (sampleLines.isEmpty()) return defaultDelimiter

                val delimiters = listOf(',', '\t', ';', '|')
                val scores = delimiters.associateWith { delim ->
                    val counts = sampleLines.map { line -> countCharOutsideQuotes(line, delim) }
                    val firstCount = counts.firstOrNull() ?: 0
                    val isConsistent = firstCount > 0 && counts.all { it == firstCount }
                    if (isConsistent) firstCount * 10 else firstCount
                }
                scores.maxByOrNull { it.value }?.key ?: defaultDelimiter
            }
        } catch (_: Exception) {
            defaultDelimiter
        }
    }

    private fun countCharOutsideQuotes(line: String, target: Char): Int {
        var count = 0
        var inQuotes = false
        for (ch in line) {
            if (ch == '"') inQuotes = !inQuotes
            else if (!inQuotes && ch == target) count++
        }
        return count
    }

    private fun detectEncodingAndCreateReader(file: File): BufferedReader {
        val fis = file.inputStream()
        val bis = DocumentFormatDetector.createSafeBoundedStream(fis)

        // Inspect BOM
        bis.mark(4)
        val bom = ByteArray(4)
        val read = bis.read(bom)
        bis.reset()

        val charset = if (read >= 3 && bom[0] == 0xEF.toByte() && bom[1] == 0xBB.toByte() && bom[2] == 0xBF.toByte()) {
            bis.skip(3)
            Charsets.UTF_8
        } else if (read >= 2 && bom[0] == 0xFE.toByte() && bom[1] == 0xFF.toByte()) {
            bis.skip(2)
            Charsets.UTF_16BE
        } else if (read >= 2 && bom[0] == 0xFF.toByte() && bom[1] == 0xFE.toByte()) {
            bis.skip(2)
            Charsets.UTF_16LE
        } else {
            Charsets.UTF_8
        }

        return BufferedReader(InputStreamReader(bis, charset))
    }

    /**
     * RFC 4180 compliant CSV state machine supporting multiline quoted strings and escaped quotes ("").
     */
    private fun parseCsvRows(reader: BufferedReader, delimiter: Char): List<List<String>> {
        val result = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        val currentField = StringBuilder()
        var inQuotes = false

        var intChar = reader.read()
        while (intChar != -1) {
            val ch = intChar.toChar()

            if (ch == '"') {
                if (inQuotes) {
                    reader.mark(1)
                    val nextChar = reader.read()
                    if (nextChar != -1 && nextChar.toChar() == '"') {
                        // Escaped quote "" -> single quote
                        currentField.append('"')
                    } else {
                        reader.reset()
                        inQuotes = false
                    }
                } else {
                    inQuotes = true
                }
            } else if (ch == delimiter && !inQuotes) {
                currentRow.add(currentField.toString())
                currentField.setLength(0)
            } else if ((ch == '\r' || ch == '\n') && !inQuotes) {
                if (ch == '\r') {
                    reader.mark(1)
                    val nextChar = reader.read()
                    if (nextChar != -1 && nextChar.toChar() != '\n') {
                        reader.reset()
                    }
                }
                currentRow.add(currentField.toString())
                currentField.setLength(0)
                result.add(currentRow)
                currentRow = mutableListOf()
            } else {
                currentField.append(ch)
            }

            intChar = reader.read()
        }

        if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(currentField.toString())
            result.add(currentRow)
        }

        return result
    }
}
