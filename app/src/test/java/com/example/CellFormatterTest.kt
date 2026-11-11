package com.example

import com.example.core.document.CellFormatter
import com.example.core.document.CellType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CellFormatterTest {

    // ---------------------------------------------------------------------------------------------
    // General Format & Floating Point Precision Tests
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testGeneralInteger() {
        val res = CellFormatter.format("42", 0, "General")
        assertEquals("42", res.text)
    }

    @Test
    fun testGeneralFloatingPointCleanup() {
        // 0.1 + 0.2 floating point representation cleanup
        val res = CellFormatter.format("0.30000000000000004", 0, "General")
        assertEquals("0.3", res.text)
    }

    @Test
    fun testGeneralLargeNumberScientific() {
        val res = CellFormatter.format("1234567890123", 0, "General")
        assertTrue(res.text.contains("E+"))
    }

    @Test
    fun testGeneralSmallNumberScientific() {
        val res = CellFormatter.format("0.00000123", 0, "General")
        assertTrue(res.text.contains("E-"))
    }

    @Test
    fun testGeneralZero() {
        val res = CellFormatter.format("0", 0, "General")
        assertEquals("0", res.text)
    }

    @Test
    fun testGeneralNegative() {
        val res = CellFormatter.format("-15.75", 0, "General")
        assertEquals("-15.75", res.text)
    }

    // ---------------------------------------------------------------------------------------------
    // Built-in Standard Formats
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testBuiltinFormat1_ZeroDecimal() {
        val res = CellFormatter.format("1234.56", 1, null)
        assertEquals("1235", res.text)
    }

    @Test
    fun testBuiltinFormat2_TwoDecimals() {
        val res = CellFormatter.format("1234.5", 2, null)
        assertEquals("1234.50", res.text)
    }

    @Test
    fun testBuiltinFormat3_ThousandsSeparator() {
        val res = CellFormatter.format("1234567", 3, null)
        assertEquals("1,234,567", res.text)
    }

    @Test
    fun testBuiltinFormat4_ThousandsWithTwoDecimals() {
        val res = CellFormatter.format("1234567.891", 4, null)
        assertEquals("1,234,567.89", res.text)
    }

    @Test
    fun testBuiltinFormat9_PercentInteger() {
        val res = CellFormatter.format("0.25", 9, null)
        assertEquals("25%", res.text)
    }

    @Test
    fun testBuiltinFormat10_PercentTwoDecimals() {
        val res = CellFormatter.format("0.1234", 10, null)
        assertEquals("12.34%", res.text)
    }

    @Test
    fun testBuiltinFormat11_Scientific() {
        val res = CellFormatter.format("12345", 11, null)
        assertTrue(res.text.startsWith("1.23E"))
    }

    @Test
    fun testBuiltinFormat49_Text() {
        val res = CellFormatter.format("Raw text string", 49, null)
        assertEquals("Raw text string", res.text)
    }

    // ---------------------------------------------------------------------------------------------
    // Multi-Section Custom Formats (Positive; Negative; Zero; Text)
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testMultiSection_Positive() {
        val formatCode = "#,##0.00;(#,##0.00);\"ZERO\""
        val res = CellFormatter.format("1500", 100, formatCode)
        assertEquals("1,500.00", res.text)
    }

    @Test
    fun testMultiSection_NegativeParentheses() {
        val formatCode = "#,##0.00;(#,##0.00);\"ZERO\""
        val res = CellFormatter.format("-1500", 100, formatCode)
        assertEquals("(1,500.00)", res.text)
    }

    @Test
    fun testMultiSection_ZeroText() {
        val formatCode = "#,##0.00;(#,##0.00);\"ZERO\""
        val res = CellFormatter.format("0", 100, formatCode)
        assertEquals("ZERO", res.text)
    }

    @Test
    fun testColorExtraction_RedNegative() {
        val formatCode = "#,##0;[Red](#,##0)"
        val res = CellFormatter.format("-450", 100, formatCode)
        assertEquals("(450)", res.text)
        assertEquals(0xFFD32F2F.toInt(), res.textColorRgb)
    }

    // ---------------------------------------------------------------------------------------------
    // Currency Formats
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testCurrency_Dollar() {
        val formatCode = "$#,##0.00"
        val res = CellFormatter.format("1250.5", 100, formatCode)
        assertEquals("$1,250.50", res.text)
    }

    @Test
    fun testCurrency_BracketDollar() {
        val formatCode = "[$$-409]#,##0.00"
        val res = CellFormatter.format("99.9", 100, formatCode)
        assertEquals("$99.90", res.text)
    }

    @Test
    fun testCurrency_Euro() {
        val formatCode = "[$€-409]#,##0.00"
        val res = CellFormatter.format("1200", 100, formatCode)
        assertEquals("€1,200.00", res.text)
    }

    @Test
    fun testCurrency_Rupee() {
        val formatCode = "[$₹-4009]#,##0.00"
        val res = CellFormatter.format("5000", 100, formatCode)
        assertEquals("₹5,000.00", res.text)
    }

    // ---------------------------------------------------------------------------------------------
    // Thousand Scaling and Literals
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testThousandScale_SingleComma() {
        // Trailing comma scales down by 1,000
        val formatCode = "#,##0,"
        val res = CellFormatter.format("1200000", 100, formatCode)
        assertEquals("1,200", res.text)
    }

    @Test
    fun testQuotedLiteralInFormat() {
        val formatCode = "#,##0 \"USD\""
        val res = CellFormatter.format("500", 100, formatCode)
        assertEquals("500 USD", res.text)
    }

    @Test
    fun testBackslashEscapedLiteral() {
        val formatCode = "\\$#,##0"
        val res = CellFormatter.format("120", 100, formatCode)
        assertEquals("$120", res.text)
    }

    // ---------------------------------------------------------------------------------------------
    // 1900 Date System & Edge Serials
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testDate1900_Serial1_Jan1_1900() {
        val res = CellFormatter.format("1.0", 14, "yyyy-mm-dd", date1904 = false)
        assertEquals("1900-01-01", res.text)
        assertTrue(res.isDate)
    }

    @Test
    fun testDate1900_Serial59_Feb28_1900() {
        val res = CellFormatter.format("59.0", 14, "yyyy-mm-dd", date1904 = false)
        assertEquals("1900-02-28", res.text)
    }

    @Test
    fun testDate1900_Serial60_Feb29_1900_HistoricalBug() {
        // Excel's deliberate Lotus 1-2-3 compatibility bug
        val res = CellFormatter.format("60.0", 14, "yyyy-mm-dd", date1904 = false)
        assertEquals("1900-02-29", res.text)
    }

    @Test
    fun testDate1900_Serial61_Mar1_1900() {
        val res = CellFormatter.format("61.0", 14, "yyyy-mm-dd", date1904 = false)
        assertEquals("1900-03-01", res.text)
    }

    @Test
    fun testDate1900_ModernDate() {
        // Serial 45000 is ~March 15, 2023
        val res = CellFormatter.format("45000.0", 14, "yyyy-mm-dd", date1904 = false)
        assertEquals("2023-03-15", res.text)
    }

    // ---------------------------------------------------------------------------------------------
    // 1904 Date System
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testDate1904_Serial0_Jan1_1904() {
        val res = CellFormatter.format("0.0", 14, "yyyy-mm-dd", date1904 = true)
        assertEquals("1904-01-01", res.text)
    }

    @Test
    fun testDate1904_Serial1_Jan2_1904() {
        val res = CellFormatter.format("1.0", 14, "yyyy-mm-dd", date1904 = true)
        assertEquals("1904-01-02", res.text)
    }

    // ---------------------------------------------------------------------------------------------
    // Date & Time Token Combinations
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testDateFormat_ShortMonth_d_mmm_yy() {
        // Serial 44927 is Jan 1, 2023
        val res = CellFormatter.format("44927.0", 15, "d-mmm-yy", date1904 = false)
        assertEquals("1-Jan-23", res.text)
    }

    @Test
    fun testDateFormat_FullMonthAndWeekday() {
        // Jan 1, 2023 was a Sunday
        val res = CellFormatter.format("44927.0", 100, "dddd, mmmm d, yyyy", date1904 = false)
        assertEquals("Sunday, January 1, 2023", res.text)
    }

    @Test
    fun testTimeFormat_24Hour_h_mm_ss() {
        // 0.5 is 12:00:00 (noon)
        val res = CellFormatter.format("0.5", 21, "h:mm:ss", date1904 = false)
        assertEquals("12:00:00", res.text)
    }

    @Test
    fun testTimeFormat_12Hour_AMPM() {
        // 0.75 is 6:00 PM (18:00)
        val res = CellFormatter.format("0.75", 18, "h:mm AM/PM", date1904 = false)
        assertEquals("6:00 PM", res.text)
    }

    @Test
    fun testDateTimeCombined() {
        // 44927.5 is 2023-01-01 12:00
        val res = CellFormatter.format("44927.5", 22, "yyyy-mm-dd h:mm", date1904 = false)
        assertEquals("2023-01-01 12:00", res.text)
    }

    @Test
    fun testElapsedHours() {
        // 2.5 days = 60 hours
        val res = CellFormatter.format("2.5", 46, "[h]:mm", date1904 = false)
        assertEquals("60:00", res.text)
    }

    // ---------------------------------------------------------------------------------------------
    // Booleans, Errors, and Special Types
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testBoolean_True() {
        val res = CellFormatter.format("1", 0, null, cellType = CellType.BOOLEAN)
        assertEquals("TRUE", res.text)
    }

    @Test
    fun testBoolean_False() {
        val res = CellFormatter.format("0", 0, null, cellType = CellType.BOOLEAN)
        assertEquals("FALSE", res.text)
    }

    @Test
    fun testErrorValue() {
        val res = CellFormatter.format("#DIV/0!", 0, null, cellType = CellType.ERROR)
        assertEquals("#DIV/0!", res.text)
    }

    @Test
    fun testStringPreservation() {
        val res = CellFormatter.format("Hello World", 0, null, cellType = CellType.STRING)
        assertEquals("Hello World", res.text)
    }

    @Test
    fun testNullOrEmptyCell() {
        val res = CellFormatter.format("", 0, null)
        assertEquals("", res.text)
    }

    @Test
    fun testInvalidNumberFallback() {
        val res = CellFormatter.format("NotANumber", 2, "0.00")
        assertEquals("NotANumber", res.text)
    }

    @Test
    fun testIsDateFormatDetection() {
        assertTrue(CellFormatter.isDateFormat("yyyy-mm-dd"))
        assertTrue(CellFormatter.isDateFormat("d-mmm-yy"))
        assertTrue(CellFormatter.isDateFormat("h:mm:ss"))
        assertFalse(CellFormatter.isDateFormat("#,##0.00"))
        assertFalse(CellFormatter.isDateFormat("0%"))
        assertFalse(CellFormatter.isDateFormat("[$$-409]#,##0"))
    }
}
