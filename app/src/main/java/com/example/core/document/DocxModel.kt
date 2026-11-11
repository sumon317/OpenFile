package com.example.core.document

/**
 * Clean, immutable Word Document (.docx) and OpenDocument Text (.odt) models
 * without Android UI dependencies. Pure Kotlin data structures suitable for JVM unit testing.
 */

// ---------------------------------------------------------------------------------------------
// 1. Text Runs & Formatting
// ---------------------------------------------------------------------------------------------

data class DocxRun(
    val text: String,
    val fontSizePt: Float = 12f,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isStrike: Boolean = false,
    val isSuperScript: Boolean = false,
    val isSubScript: Boolean = false,
    val colorRgb: Int? = null,
    val highlightColorRgb: Int? = null,
    val linkUrl: String? = null
)

// ---------------------------------------------------------------------------------------------
// 2. Document Blocks (Paragraphs, Tables, Images, Page Breaks)
// ---------------------------------------------------------------------------------------------

sealed interface DocxBlock {

    data class Paragraph(
        val runs: List<DocxRun>,
        val headingLevel: Int = 0, // 0 = normal body text, 1-6 = Heading 1-6, -1 = Title, -2 = Subtitle
        val alignment: CellHorizontalAlignment = CellHorizontalAlignment.LEFT,
        val indentLevel: Int = 0,
        val bulletPrefix: String? = null,
        val spaceBeforePt: Float = 0f,
        val spaceAfterPt: Float = 4f,
        val lineSpacing: Float = 1.15f
    ) : DocxBlock {
        val fullText: String get() = runs.joinToString("") { it.text }
    }

    data class Table(
        val rows: List<DocxTableRow>,
        val colWidthsPercent: List<Float> = emptyList()
    ) : DocxBlock

    data class Image(
        val mediaEntryPath: String,
        val widthEmu: Long = 4572000L,  // 5 inches default
        val heightEmu: Long = 3200400L, // 3.5 inches default
        val caption: String? = null
    ) : DocxBlock

    object PageBreak : DocxBlock

    data class Placeholder(
        val label: String,
        val rawSnippet: String? = null
    ) : DocxBlock
}

// ---------------------------------------------------------------------------------------------
// 3. Table Rows and Cells
// ---------------------------------------------------------------------------------------------

data class DocxTableCell(
    val blocks: List<DocxBlock>,
    val fillRgb: Int? = null,
    val colSpan: Int = 1,
    val rowSpan: Int = 1
) {
    val fullText: String get() = blocks.filterIsInstance<DocxBlock.Paragraph>().joinToString(" ") { it.fullText }
}

data class DocxTableRow(
    val cells: List<DocxTableCell>,
    val isHeader: Boolean = false
)

// ---------------------------------------------------------------------------------------------
// 4. Complete Document Model
// ---------------------------------------------------------------------------------------------

data class DocxDocumentModel(
    val title: String,
    val blocks: List<DocxBlock>,
    val footnotes: List<String> = emptyList()
) {
    /** Plain text representation of the entire document for search and screen readers */
    val plainTextSummary: String by lazy {
        val sb = StringBuilder()
        for (block in blocks) {
            when (block) {
                is DocxBlock.Paragraph -> {
                    val t = block.fullText
                    if (t.isNotBlank()) sb.append(t).append("\n\n")
                }
                is DocxBlock.Table -> {
                    for (row in block.rows) {
                        sb.append(row.cells.joinToString(" | ") { it.fullText }).append("\n")
                    }
                    sb.append("\n")
                }
                else -> {}
            }
        }
        sb.toString().trim()
    }
}
