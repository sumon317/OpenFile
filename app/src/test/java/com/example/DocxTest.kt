package com.example

import com.example.core.document.CellHorizontalAlignment
import com.example.core.document.DocxBlock
import com.example.core.document.DocxDocumentModel
import com.example.core.document.DocxParser
import com.example.core.document.DocxRun
import com.example.core.document.DocxTableCell
import com.example.core.document.DocxTableRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DocxTest {

    // ---------------------------------------------------------------------------------------------
    // Model & Plain Text Summary Tests
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testDocxDocumentModelPlainTextSummary() {
        val model = DocxDocumentModel(
            title = "Test Doc",
            blocks = listOf(
                DocxBlock.Paragraph(
                    runs = listOf(DocxRun("Introduction to Modern Android", fontSizePt = 22f, isBold = true)),
                    headingLevel = 1
                ),
                DocxBlock.Paragraph(
                    runs = listOf(
                        DocxRun("This is the first paragraph with "),
                        DocxRun("bold text", isBold = true),
                        DocxRun(" and a link.", linkUrl = "https://android.com")
                    )
                ),
                DocxBlock.Table(
                    rows = listOf(
                        DocxTableRow(
                            cells = listOf(
                                DocxTableCell(listOf(DocxBlock.Paragraph(listOf(DocxRun("Feature"))))),
                                DocxTableCell(listOf(DocxBlock.Paragraph(listOf(DocxRun("Status")))))
                            ),
                            isHeader = true
                        ),
                        DocxTableRow(
                            cells = listOf(
                                DocxTableCell(listOf(DocxBlock.Paragraph(listOf(DocxRun("Compose"))))),
                                DocxTableCell(listOf(DocxBlock.Paragraph(listOf(DocxRun("Supported")))))
                            )
                        )
                    )
                )
            ),
            footnotes = listOf("First footnote")
        )

        val summary = model.plainTextSummary
        assertTrue(summary.contains("Introduction to Modern Android"))
        assertTrue(summary.contains("This is the first paragraph with bold text and a link."))
        assertTrue(summary.contains("Feature | Status"))
        assertTrue(summary.contains("Compose | Supported"))
    }

    // ---------------------------------------------------------------------------------------------
    // Synthetic DOCX Parser Tests
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testParseSyntheticDocx() {
        val tempDocx = File.createTempFile("test_document", ".docx")
        tempDocx.deleteOnExit()

        val documentXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                <w:body>
                    <w:p>
                        <w:pPr>
                            <w:pStyle w:val="Heading1"/>
                        </w:pPr>
                        <w:r>
                            <w:t>Project Architecture</w:t>
                        </w:r>
                    </w:p>
                    <w:p>
                        <w:pPr>
                            <w:jc w:val="center"/>
                        </w:pPr>
                        <w:r>
                            <w:rPr>
                                <w:b/>
                                <w:sz w:val="28"/>
                                <w:color w:val="003366"/>
                            </w:rPr>
                            <w:t>Overview of the system design</w:t>
                        </w:r>
                    </w:p>
                    <w:tbl>
                        <w:tr>
                            <w:tc>
                                <w:p>
                                    <w:r><w:t>Component</w:t></w:r>
                                </w:p>
                            </w:tc>
                            <w:tc>
                                <w:p>
                                    <w:r><w:t>Technology</w:t></w:r>
                                </w:p>
                            </w:tc>
                        </w:tr>
                        <w:tr>
                            <w:tc>
                                <w:p>
                                    <w:r><w:t>UI</w:t></w:r>
                                </w:p>
                            </w:tc>
                            <w:tc>
                                <w:p>
                                    <w:r><w:t>Jetpack Compose</w:t></w:r>
                                </w:p>
                            </w:tc>
                        </w:tr>
                    </w:tbl>
                    <w:p>
                        <w:r>
                            <w:br w:type="page"/>
                        </w:r>
                    </w:p>
                </w:body>
            </w:document>
        """.trimIndent()

        val stylesXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:style w:type="paragraph" w:styleId="Heading1">
                    <w:name w:val="heading 1"/>
                </w:style>
            </w:styles>
        """.trimIndent()

        val relsXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            </Relationships>
        """.trimIndent()

        createZipFile(
            tempDocx,
            mapOf(
                "word/document.xml" to documentXml.toByteArray(Charsets.UTF_8),
                "word/styles.xml" to stylesXml.toByteArray(Charsets.UTF_8),
                "word/_rels/document.xml.rels" to relsXml.toByteArray(Charsets.UTF_8),
                "[Content_Types].xml" to """<?xml version="1.0"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"></Types>""".toByteArray(Charsets.UTF_8)
            )
        )

        val doc = DocxParser.parse(tempDocx)
        assertNotNull(doc)
        assertTrue(doc.blocks.isNotEmpty())

        val heading1 = doc.blocks[0] as DocxBlock.Paragraph
        assertEquals("Project Architecture", heading1.fullText)
        assertEquals(1, heading1.headingLevel)

        val centerParagraph = doc.blocks[1] as DocxBlock.Paragraph
        assertEquals("Overview of the system design", centerParagraph.fullText)
        assertEquals(CellHorizontalAlignment.CENTER, centerParagraph.alignment)
        assertTrue(centerParagraph.runs[0].isBold)
        assertEquals(14f, centerParagraph.runs[0].fontSizePt, 0.1f) // 28 half-points = 14pt
        assertEquals(0xFF003366.toInt(), centerParagraph.runs[0].colorRgb)

        val table = doc.blocks[2] as DocxBlock.Table
        assertEquals(2, table.rows.size)
        assertEquals("Component", table.rows[0].cells[0].fullText)
        assertEquals("Technology", table.rows[0].cells[1].fullText)
        assertEquals("UI", table.rows[1].cells[0].fullText)
        assertEquals("Jetpack Compose", table.rows[1].cells[1].fullText)

        val pageBreak = doc.blocks[3]
        assertTrue(pageBreak is DocxBlock.PageBreak)
    }

    // ---------------------------------------------------------------------------------------------
    // Synthetic ODT Parser Tests
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testParseSyntheticOdt() {
        val tempOdt = File.createTempFile("test_document", ".odt")
        tempOdt.deleteOnExit()

        val contentXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                                     xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0">
                <office:body>
                    <office:text>
                        <text:h text:outline-level="1">OpenDocument Heading</text:h>
                        <text:p>This is a paragraph inside an OpenDocument Text archive.</text:p>
                    </office:text>
                </office:body>
            </office:document-content>
        """.trimIndent()

        createZipFile(
            tempOdt,
            mapOf(
                "content.xml" to contentXml.toByteArray(Charsets.UTF_8),
                "mimetype" to "application/vnd.oasis.opendocument.text".toByteArray(Charsets.UTF_8)
            )
        )

        val doc = DocxParser.parse(tempOdt)
        assertNotNull(doc)
        assertEquals(2, doc.blocks.size)

        val h1 = doc.blocks[0] as DocxBlock.Paragraph
        assertEquals("OpenDocument Heading", h1.fullText)
        assertEquals(1, h1.headingLevel)

        val p = doc.blocks[1] as DocxBlock.Paragraph
        assertEquals("This is a paragraph inside an OpenDocument Text archive.", p.fullText)
        assertEquals(0, p.headingLevel)
    }

    // ---------------------------------------------------------------------------------------------
    // Legacy .doc and Password Detection Tests
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testLegacyDocRejection() {
        val tempLegacy = File.createTempFile("test", ".doc")
        tempLegacy.deleteOnExit()
        tempLegacy.writeBytes(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte()))

        try {
            DocxParser.parse(tempLegacy)
            fail("Expected UnsupportedOperationException for legacy .doc binary format")
        } catch (e: UnsupportedOperationException) {
            assertTrue(e.message!!.contains("Legacy binary document"))
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Horizontal Pagination Tests
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testPaginateDocxBlocksWithPageBreaks() {
        val blocks = listOf(
            DocxBlock.Paragraph(listOf(DocxRun("Title")), headingLevel = 1),
            DocxBlock.Paragraph(listOf(DocxRun("Introduction text."))),
            DocxBlock.PageBreak,
            DocxBlock.Paragraph(listOf(DocxRun("Page 2 Content")))
        )

        val pages = com.example.ui.components.paginateDocxBlocks(blocks)
        assertEquals(2, pages.size)
        assertEquals(0, pages[0].pageIndex)
        assertEquals(2, pages[0].blocks.size)
        assertEquals(1, pages[1].pageIndex)
        assertEquals(1, pages[1].blocks.size)
        assertEquals("Page 2 Content", (pages[1].blocks[0].second as DocxBlock.Paragraph).fullText)
    }

    @Test
    fun testPaginateDocxBlocksEmptyFallback() {
        val pages = com.example.ui.components.paginateDocxBlocks(emptyList())
        assertEquals(1, pages.size)
        assertEquals(0, pages[0].pageIndex)
        assertTrue(pages[0].blocks.isEmpty())
    }

    // ---------------------------------------------------------------------------------------------
    // Helper: ZIP Writer
    // ---------------------------------------------------------------------------------------------

    private fun createZipFile(targetFile: File, entries: Map<String, ByteArray>) {
        ZipOutputStream(targetFile.outputStream()).use { zos ->
            for ((name, data) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(data)
                zos.closeEntry()
            }
        }
    }
}
