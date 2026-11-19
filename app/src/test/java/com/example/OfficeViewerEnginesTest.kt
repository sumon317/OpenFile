package com.example

import com.example.core.document.DocxBlock
import com.example.core.document.DocxParser
import com.example.core.document.Ole2DocumentReader
import com.example.core.document.PresentationParser
import com.example.core.document.SpreadsheetParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OfficeViewerEnginesTest {

    private fun createZipFile(outFile: File, entries: Map<String, ByteArray>) {
        ZipOutputStream(outFile.outputStream().buffered()).use { zos ->
            for ((path, data) in entries) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(data)
                zos.closeEntry()
            }
        }
    }

    // =============================================================================================
    // 1. Ole2DocumentReader Tests
    // =============================================================================================

    @Test
    fun testOle2DetectionAndExtraction() {
        val nonOleFile = File.createTempFile("plain_text", ".txt")
        nonOleFile.deleteOnExit()
        nonOleFile.writeText("This is definitely not an OLE2 file.")

        assertFalse(Ole2DocumentReader.isOle2File(nonOleFile))
        val stream = Ole2DocumentReader.extractStream(nonOleFile, "WordDocument")
        assertEquals(null, stream)

        // Test readable text extractor on UTF-16LE bytes
        val utf16Text = "Hello OpenFile Document Engine!\nSecond line of content."
        val utf16Bytes = utf16Text.toByteArray(Charsets.UTF_16LE)
        val extractedUtf16 = Ole2DocumentReader.extractReadableText(utf16Bytes)
        assertTrue(extractedUtf16.any { it.contains("Hello OpenFile") })

        // Test readable text extractor on ASCII bytes
        val asciiText = "Quarterly Financial Overview 2026\nRevenue: $1,250,000"
        val asciiBytes = asciiText.toByteArray(Charsets.ISO_8859_1)
        val extractedAscii = Ole2DocumentReader.extractReadableText(asciiBytes)
        assertTrue(extractedAscii.any { it.contains("Quarterly Financial Overview") })
    }

    // =============================================================================================
    // 2. Enhanced DocxParser & ODT Format Tests
    // =============================================================================================

    @Test
    fun testParseRichOdtDocument() {
        val tempOdt = File.createTempFile("rich_odt", ".odt")
        tempOdt.deleteOnExit()

        val contentXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                                     xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"
                                     xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0"
                                     xmlns:xlink="http://www.w3.org/1999/xlink">
                <office:body>
                    <office:text>
                        <text:h text:outline-level="1">OpenDocument Specification Report</text:h>
                        <text:p>Welcome to <text:span text:style-name="Bold">ODF 1.3</text:span> standard.</text:p>
                        <text:p>Visit <text:a xlink:href="https://oasis-open.org">OASIS Open</text:a> for details.</text:p>
                        <table:table table:name="Table1">
                            <table:table-row>
                                <table:table-cell><text:p>Document</text:p></table:table-cell>
                                <table:table-cell><text:p>Format</text:p></table:table-cell>
                            </table:table-row>
                            <table:table-row>
                                <table:table-cell><text:p>Writer</text:p></table:table-cell>
                                <table:table-cell><text:p>ODT</text:p></table:table-cell>
                            </table:table-row>
                        </table:table>
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
        assertTrue("Document should have multiple blocks", doc.blocks.size >= 3)

        // Heading verification
        val heading = doc.blocks[0] as DocxBlock.Paragraph
        assertEquals("OpenDocument Specification Report", heading.fullText)
        assertEquals(1, heading.headingLevel)

        // Paragraph with span verification
        val p1 = doc.blocks[1] as DocxBlock.Paragraph
        assertTrue(p1.fullText.contains("Welcome to ODF 1.3 standard."))

        // Paragraph with link verification
        val p2 = doc.blocks[2] as DocxBlock.Paragraph
        assertTrue(p2.fullText.contains("OASIS Open"))
        assertTrue(p2.runs.any { it.linkUrl == "https://oasis-open.org" })

        // Table verification
        val table = doc.blocks.filterIsInstance<DocxBlock.Table>().firstOrNull()
        assertNotNull("Table block must be extracted", table)
        assertEquals(2, table!!.rows.size)
        assertEquals("Document", table.rows[0].cells[0].fullText)
        assertEquals("Format", table.rows[0].cells[1].fullText)
        assertEquals("Writer", table.rows[1].cells[0].fullText)
        assertEquals("ODT", table.rows[1].cells[1].fullText)
    }

    // =============================================================================================
    // 3. SpreadsheetParser Coordinate & ODS Tests
    // =============================================================================================

    @Test
    fun testSpreadsheetCoordinateConversions() {
        // Bi-directional coordinate math tests
        assertEquals("A", SpreadsheetParser.indexToColumnLetter(0))
        assertEquals("Z", SpreadsheetParser.indexToColumnLetter(25))
        assertEquals("AA", SpreadsheetParser.indexToColumnLetter(26))
        assertEquals("AB", SpreadsheetParser.indexToColumnLetter(27))
        assertEquals("AZ", SpreadsheetParser.indexToColumnLetter(51))
        assertEquals("BA", SpreadsheetParser.indexToColumnLetter(52))
        assertEquals("ZZ", SpreadsheetParser.indexToColumnLetter(701))
        assertEquals("AAA", SpreadsheetParser.indexToColumnLetter(702))

        assertEquals(0, SpreadsheetParser.columnLetterToIndex("A"))
        assertEquals(25, SpreadsheetParser.columnLetterToIndex("Z"))
        assertEquals(26, SpreadsheetParser.columnLetterToIndex("AA"))
        assertEquals(27, SpreadsheetParser.columnLetterToIndex("AB"))
        assertEquals(51, SpreadsheetParser.columnLetterToIndex("AZ"))
        assertEquals(52, SpreadsheetParser.columnLetterToIndex("BA"))
        assertEquals(701, SpreadsheetParser.columnLetterToIndex("ZZ"))
        assertEquals(702, SpreadsheetParser.columnLetterToIndex("AAA"))
    }

    @Test
    fun testParseSyntheticOdsSpreadsheet() {
        val tempOds = File.createTempFile("test_sheet", ".ods")
        tempOds.deleteOnExit()

        val contentXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                                     xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0"
                                     xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0">
                <office:body>
                    <office:spreadsheet>
                        <table:table table:name="FinancialData">
                            <table:table-row>
                                <table:table-cell office:value-type="string"><text:p>Category</text:p></table:table-cell>
                                <table:table-cell office:value-type="string"><text:p>Q1</text:p></table:table-cell>
                                <table:table-cell office:value-type="string"><text:p>Q2</text:p></table:table-cell>
                            </table:table-row>
                            <table:table-row>
                                <table:table-cell office:value-type="string"><text:p>Hardware</text:p></table:table-cell>
                                <table:table-cell office:value-type="float" office:value="15000"><text:p>15000</text:p></table:table-cell>
                                <table:table-cell office:value-type="float" office:value="22000"><text:p>22000</text:p></table:table-cell>
                            </table:table-row>
                        </table:table>
                    </office:spreadsheet>
                </office:body>
            </office:document-content>
        """.trimIndent()

        createZipFile(
            tempOds,
            mapOf(
                "content.xml" to contentXml.toByteArray(Charsets.UTF_8),
                "mimetype" to "application/vnd.oasis.opendocument.spreadsheet".toByteArray(Charsets.UTF_8)
            )
        )

        val workbook = SpreadsheetParser.parse(tempOds)
        assertNotNull(workbook)
        assertEquals(1, workbook.sheets.size)
        val sheet = workbook.sheets[0]
        assertEquals("FinancialData", sheet.name)
        assertTrue(sheet.rowCount >= 2)
        assertTrue(sheet.colCount >= 3)

        assertEquals("Category", sheet.getCell(0, 0)?.displayValue)
        assertEquals("Q1", sheet.getCell(0, 1)?.displayValue)
        assertEquals("Hardware", sheet.getCell(1, 0)?.displayValue)
        assertEquals("15000", sheet.getCell(1, 1)?.displayValue)
    }

    // =============================================================================================
    // 4. PresentationParser Dimension & ODP Tests
    // =============================================================================================

    @Test
    fun testPresentationOdpDimensionMath() {
        // 1 inch = 914400 EMU = 2.54 cm = 72 pt = 25.4 mm
        assertEquals(914400L, PresentationParser.parseOdpDimension("1in", 0L))
        assertEquals(914400L, PresentationParser.parseOdpDimension("1inch", 0L))
        assertEquals(914400L, PresentationParser.parseOdpDimension("2.54cm", 0L))
        assertEquals(914400L, PresentationParser.parseOdpDimension("72pt", 0L))
        assertEquals(914400L, PresentationParser.parseOdpDimension("25.4mm", 0L))

        // Default fallback on invalid or empty
        assertEquals(12345L, PresentationParser.parseOdpDimension(null, 12345L))
        assertEquals(12345L, PresentationParser.parseOdpDimension("", 12345L))
    }

    @Test
    fun testParseSyntheticOdpPresentation() {
        val tempOdp = File.createTempFile("test_presentation", ".odp")
        tempOdp.deleteOnExit()

        val contentXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                                     xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"
                                     xmlns:svg="urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0"
                                     xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0">
                <office:body>
                    <office:presentation>
                        <draw:page draw:name="page1">
                            <draw:frame svg:x="2.54cm" svg:y="2.54cm" svg:width="15cm" svg:height="4cm">
                                <draw:text-box>
                                    <text:p>Keynote Presentation 2026</text:p>
                                    <text:p>Modern Privacy-First Architecture</text:p>
                                </draw:text-box>
                            </draw:frame>
                        </draw:page>
                        <draw:page draw:name="page2">
                            <draw:frame svg:x="1in" svg:y="1in" svg:width="8in" svg:height="2in">
                                <draw:text-box>
                                    <text:p>Slide 2: Roadmap and Milestones</text:p>
                                </draw:text-box>
                            </draw:frame>
                        </draw:page>
                    </office:presentation>
                </office:body>
            </office:document-content>
        """.trimIndent()

        createZipFile(
            tempOdp,
            mapOf(
                "content.xml" to contentXml.toByteArray(Charsets.UTF_8),
                "mimetype" to "application/vnd.oasis.opendocument.presentation".toByteArray(Charsets.UTF_8)
            )
        )

        val presentation = PresentationParser.parse(tempOdp)
        assertNotNull(presentation)
        assertEquals(2, presentation.slides.size)

        val slide1 = presentation.slides[0]
        assertEquals(1, slide1.slideNumber)
        assertEquals("Keynote Presentation 2026", slide1.title)
        assertTrue(slide1.plainTextSummary.contains("Modern Privacy-First Architecture"))

        val slide2 = presentation.slides[1]
        assertEquals(2, slide2.slideNumber)
        assertEquals("Slide 2: Roadmap and Milestones", slide2.title)
    }
}
