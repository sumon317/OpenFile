package com.example.core.document

/**
 * Clean, immutable spreadsheet data models without any Android UI dependencies.
 * Pure Kotlin data structures suitable for JVM unit testing and high-performance Canvas rendering.
 */

enum class CellType {
    BLANK,
    STRING,
    NUMBER,
    DATE,
    BOOLEAN,
    ERROR,
    FORMULA_STRING
}

enum class CellHorizontalAlignment {
    GENERAL,
    LEFT,
    CENTER,
    RIGHT,
    JUSTIFY
}

enum class CellVerticalAlignment {
    TOP,
    CENTER,
    BOTTOM
}

enum class BorderStyle {
    NONE,
    THIN,
    MEDIUM,
    THICK,
    DASHED,
    DOTTED,
    DOUBLE
}

data class CellBorder(
    val style: BorderStyle = BorderStyle.NONE,
    val colorRgb: Int? = null
)

data class CellBorders(
    val left: CellBorder = CellBorder(),
    val right: CellBorder = CellBorder(),
    val top: CellBorder = CellBorder(),
    val bottom: CellBorder = CellBorder()
)

data class CellFont(
    val name: String? = null,
    val sizePt: Float = 11f,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isStrike: Boolean = false,
    val colorRgb: Int? = null
)

data class CellFill(
    val fgColorRgb: Int? = null,
    val bgColorRgb: Int? = null,
    val pattern: String? = null
)

data class CellStyle(
    val id: Int = 0,
    val numFmtId: Int = 0,
    val formatCode: String? = null,
    val font: CellFont = CellFont(),
    val fill: CellFill = CellFill(),
    val borders: CellBorders = CellBorders(),
    val horizontalAlign: CellHorizontalAlignment = CellHorizontalAlignment.GENERAL,
    val verticalAlign: CellVerticalAlignment = CellVerticalAlignment.CENTER,
    val wrapText: Boolean = false,
    val indent: Int = 0
)

data class SpreadsheetCell(
    val rowIndex: Int,
    val colIndex: Int,
    val rawValue: String? = null,
    val displayValue: String = "",
    val formula: String? = null,
    val cellType: CellType = CellType.BLANK,
    val style: CellStyle = CellStyle()
) {
    /** Whether this cell has meaningful content or formatting to render. */
    val isEmpty: Boolean get() = displayValue.isBlank() && rawValue.isNullOrBlank() && formula.isNullOrBlank()
}

/**
 * Represents an inclusive 0-indexed rectangular block of cells: (firstRow..lastRow, firstCol..lastCol).
 */
data class CellRange(
    val firstRow: Int,
    val lastRow: Int,
    val firstCol: Int,
    val lastCol: Int
) {
    fun contains(row: Int, col: Int): Boolean {
        return row in firstRow..lastRow && col in firstCol..lastCol
    }

    val rowSpan: Int get() = lastRow - firstRow + 1
    val colSpan: Int get() = lastCol - firstCol + 1
}

data class ColumnPr(
    val colIndex: Int,
    val widthChars: Float? = null,
    val isHidden: Boolean = false
)

data class RowPr(
    val rowIndex: Int,
    val heightPt: Float? = null,
    val isHidden: Boolean = false
)

data class SheetViewPr(
    val frozenRows: Int = 0,
    val frozenCols: Int = 0,
    val showGridLines: Boolean = true,
    val tabColorRgb: Int? = null,
    val isHidden: Boolean = false
)

data class SpreadsheetSheetModel(
    val name: String,
    val rowCount: Int,
    val colCount: Int,
    /** Sparse row storage: map of row index -> (col index -> cell) */
    val rows: Map<Int, Map<Int, SpreadsheetCell>>,
    val mergedRanges: List<CellRange> = emptyList(),
    val columns: Map<Int, ColumnPr> = emptyMap(),
    val rowsMeta: Map<Int, RowPr> = emptyMap(),
    val view: SheetViewPr = SheetViewPr(),
    val defaultColWidthChars: Float = 9.5f,
    val defaultRowHeightPt: Float = 15f
) {
    private val mergedRangeMap: Map<Pair<Int, Int>, CellRange> by lazy {
        val map = mutableMapOf<Pair<Int, Int>, CellRange>()
        for (range in mergedRanges) {
            for (r in range.firstRow..range.lastRow) {
                for (c in range.firstCol..range.lastCol) {
                    map[r to c] = range
                }
            }
        }
        map
    }

    fun getCell(row: Int, col: Int): SpreadsheetCell? {
        return rows[row]?.get(col)
    }

    fun getMergedRangeFor(row: Int, col: Int): CellRange? {
        return mergedRangeMap[row to col]
    }

    fun isCoveredMergedCell(row: Int, col: Int): Boolean {
        val range = getMergedRangeFor(row, col) ?: return false
        return !(range.firstRow == row && range.firstCol == col)
    }
}

data class SpreadsheetWorkbookModel(
    val title: String,
    val sheets: List<SpreadsheetSheetModel>,
    val date1904: Boolean = false
)

data class SelectionStats(
    val totalCount: Int,
    val numericCount: Int,
    val sum: Double?,
    val average: Double?,
    val min: Double?,
    val max: Double?
)
