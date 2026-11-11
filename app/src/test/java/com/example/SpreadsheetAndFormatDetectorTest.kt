package com.example

import com.example.core.document.CellRange
import com.example.core.document.DetectedDocumentType
import com.example.core.document.DocumentFormatDetector
import com.example.core.document.SpreadsheetParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SpreadsheetAndFormatDetectorTest {

    // ---------------------------------------------------------------------------------------------
    // Column Letter to Index Conversion Tests
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testColumnLetterToIndex() {
        assertEquals(0, SpreadsheetParser.columnLetterToIndex("A"))
        assertEquals(1, SpreadsheetParser.columnLetterToIndex("B"))
        assertEquals(25, SpreadsheetParser.columnLetterToIndex("Z"))
        assertEquals(26, SpreadsheetParser.columnLetterToIndex("AA"))
        assertEquals(27, SpreadsheetParser.columnLetterToIndex("AB"))
        assertEquals(51, SpreadsheetParser.columnLetterToIndex("AZ"))
        assertEquals(52, SpreadsheetParser.columnLetterToIndex("BA"))
        assertEquals(701, SpreadsheetParser.columnLetterToIndex("ZZ"))
        assertEquals(702, SpreadsheetParser.columnLetterToIndex("AAA"))
        assertEquals(16383, SpreadsheetParser.columnLetterToIndex("XFD")) // Excel max col
    }

    @Test
    fun testIndexToColumnLetter() {
        assertEquals("A", SpreadsheetParser.indexToColumnLetter(0))
        assertEquals("B", SpreadsheetParser.indexToColumnLetter(1))
        assertEquals("Z", SpreadsheetParser.indexToColumnLetter(25))
        assertEquals("AA", SpreadsheetParser.indexToColumnLetter(26))
        assertEquals("AB", SpreadsheetParser.indexToColumnLetter(27))
        assertEquals("AZ", SpreadsheetParser.indexToColumnLetter(51))
        assertEquals("BA", SpreadsheetParser.indexToColumnLetter(52))
        assertEquals("ZZ", SpreadsheetParser.indexToColumnLetter(701))
        assertEquals("AAA", SpreadsheetParser.indexToColumnLetter(702))
        assertEquals("XFD", SpreadsheetParser.indexToColumnLetter(16383))
    }

    // ---------------------------------------------------------------------------------------------
    // Cell Reference & Range Parsing Tests
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testParseCellRef() {
        val (colA1, rowA1) = SpreadsheetParser.parseCellRef("A1")
        assertEquals(0, colA1)
        assertEquals(0, rowA1)

        val (colB14, rowB14) = SpreadsheetParser.parseCellRef("B14")
        assertEquals(1, colB14)
        assertEquals(13, rowB14)

        val (colXFD1048576, rowXFD1048576) = SpreadsheetParser.parseCellRef("XFD1048576")
        assertEquals(16383, colXFD1048576)
        assertEquals(1048575, rowXFD1048576)
    }

    @Test
    fun testParseRangeRef() {
        val range = SpreadsheetParser.parseRangeRef("B2:D6")
        assertNotNull(range)
        assertEquals(1, range!!.firstRow)
        assertEquals(5, range.lastRow)
        assertEquals(1, range.firstCol)
        assertEquals(3, range.lastCol)
        assertEquals(5, range.rowSpan)
        assertEquals(3, range.colSpan)
        assertTrue(range.contains(3, 2))
        assertFalse(range.contains(0, 0))
    }

    // ---------------------------------------------------------------------------------------------
    // Format Detection & Magic Bytes Tests
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testDetectPdf() {
        val tempPdf = File.createTempFile("test", ".pdf")
        tempPdf.deleteOnExit()
        tempPdf.writeBytes(byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x35)) // %PDF-1.5

        val type = DocumentFormatDetector.detect(tempPdf)
        assertTrue(type is DetectedDocumentType.Pdf)
    }

    @Test
    fun testDetectLegacyOle2() {
        val tempXls = File.createTempFile("test", ".xls")
        tempXls.deleteOnExit()
        // OLE2 Compound Document magic bytes: D0 CF 11 E0 A1 B1 1A E1
        tempXls.writeBytes(
            byteArrayOf(
                0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(),
                0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte(),
                0x00, 0x00
            )
        )

        val type = DocumentFormatDetector.detect(tempXls)
        assertTrue(type is DetectedDocumentType.LegacyOle2)
    }

    @Test
    fun testZipSlipPrevention() {
        assertTrue(DocumentFormatDetector.isZipSlipAttempt("../evil.xml"))
        assertTrue(DocumentFormatDetector.isZipSlipAttempt("/etc/passwd"))
        assertTrue(DocumentFormatDetector.isZipSlipAttempt("sub/../../root.xml"))
        assertFalse(DocumentFormatDetector.isZipSlipAttempt("xl/worksheets/sheet1.xml"))
        assertFalse(DocumentFormatDetector.isZipSlipAttempt("ppt/slides/slide1.xml"))
    }

    // ---------------------------------------------------------------------------------------------
    // CSV State Machine Tests (with quotes, newlines and commas)
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testParseCsvWithEmbeddedCommasAndNewlines() {
        val csvFile = File.createTempFile("test", ".csv")
        csvFile.deleteOnExit()
        csvFile.writeText(
            "Name,Amount,\"Address, Line 1\"\n" +
            "Alice,1500,\"123 Main St, Suite 4\"\n" +
            "Bob,2400,\"Line with\nnewline\"\n" +
            "\"Quoted \"\"Escaped\"\"\",99.9,Simple"
        )

        val model = SpreadsheetParser.parseDelimited(csvFile, ',')
        val sheet = model.sheets.first()
        assertEquals(4, sheet.rowCount)
        assertEquals(3, sheet.colCount)

        // Check header
        assertEquals("Name", sheet.getCell(0, 0)?.displayValue)
        assertEquals("Amount", sheet.getCell(0, 1)?.displayValue)
        assertEquals("Address, Line 1", sheet.getCell(0, 2)?.displayValue)

        // Check embedded comma
        assertEquals("123 Main St, Suite 4", sheet.getCell(1, 2)?.displayValue)

        // Check embedded newline
        assertTrue(sheet.getCell(2, 2)?.displayValue?.contains("\n") == true)

        // Check escaped quotes
        assertEquals("Quoted \"Escaped\"", sheet.getCell(3, 0)?.displayValue)
    }
}
