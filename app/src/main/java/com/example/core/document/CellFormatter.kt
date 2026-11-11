package com.example.core.document

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

/**
 * Result of formatting a cell: the text to display, plus any extracted styling such as text color.
 */
data class FormattedCellResult(
    val text: String,
    val textColorRgb: Int? = null,
    val isDate: Boolean = false
)

/**
 * Self-contained, robust Excel and spreadsheet cell number/date formatting engine.
 * Fully supports OOXML numFmt codes, custom formats, 1900/1904 date systems (including the
 * historical serial 60 leap-year bug), accounting, currencies, percentages, and scientific notation.
 */
object CellFormatter {

    // Built-in standard OOXML format ID table (ECMA-376 Part 1, Section 18.8.30)
    private val BUILTIN_FORMATS: Map<Int, String> = mapOf(
        0 to "General",
        1 to "0",
        2 to "0.00",
        3 to "#,##0",
        4 to "#,##0.00",
        5 to "$#,##0;($#,##0)",
        6 to "$#,##0;[Red]($#,##0)",
        7 to "$#,##0.00;($#,##0.00)",
        8 to "$#,##0.00;[Red]($#,##0.00)",
        9 to "0%",
        10 to "0.00%",
        11 to "0.00E+00",
        12 to "# ?/?",
        13 to "# ??/??",
        14 to "yyyy-mm-dd",
        15 to "d-mmm-yy",
        16 to "d-mmm",
        17 to "mmm-yy",
        18 to "h:mm AM/PM",
        19 to "h:mm:ss AM/PM",
        20 to "h:mm",
        21 to "h:mm:ss",
        22 to "yyyy-mm-dd h:mm",
        37 to "#,##0;(#,##0)",
        38 to "#,##0;[Red](#,##0)",
        39 to "#,##0.00;(#,##0.00)",
        40 to "#,##0.00;[Red](#,##0.00)",
        45 to "mm:ss",
        46 to "[h]:mm:ss",
        47 to "mm:ss.0",
        48 to "##0.0E+0",
        49 to "@"
    )

    private val MONTH_NAMES_SHORT = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )
    private val MONTH_NAMES_FULL = arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    private val DAY_NAMES_SHORT = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    private val DAY_NAMES_FULL = arrayOf(
        "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
    )

    /**
     * Resolves the effective format code from an explicit format code string or a standard numFmtId.
     */
    fun resolveFormatCode(numFmtId: Int, explicitCode: String?): String {
        if (!explicitCode.isNullOrBlank() && !explicitCode.equals("General", ignoreCase = true)) {
            return explicitCode
        }
        return BUILTIN_FORMATS[numFmtId] ?: "General"
    }

    /**
     * Formats a raw cell value according to its format code and workbook date system.
     */
    fun format(
        rawValue: String?,
        numFmtId: Int,
        explicitCode: String?,
        date1904: Boolean = false,
        cellType: CellType = CellType.NUMBER
    ): FormattedCellResult {
        if (rawValue == null || rawValue.isEmpty()) {
            return FormattedCellResult("")
        }

        if (cellType == CellType.BOOLEAN) {
            val boolVal = rawValue == "1" || rawValue.equals("TRUE", ignoreCase = true)
            return FormattedCellResult(if (boolVal) "TRUE" else "FALSE")
        }

        if (cellType == CellType.ERROR) {
            return FormattedCellResult(rawValue)
        }

        if (cellType == CellType.STRING || cellType == CellType.FORMULA_STRING) {
            return FormattedCellResult(rawValue)
        }

        val formatCode = resolveFormatCode(numFmtId, explicitCode)
        if (formatCode.equals("General", ignoreCase = true) || formatCode.isBlank()) {
            return formatGeneral(rawValue)
        }

        // Text format
        if (formatCode == "@") {
            return FormattedCellResult(rawValue)
        }

        val num = rawValue.toDoubleOrNull()
        if (num == null) {
            // Not a number, return as text
            return FormattedCellResult(rawValue)
        }

        return formatNumeric(num, formatCode, date1904)
    }

    /**
     * General format behavior:
     * - Cleans up floating-point noise (e.g. 0.1 + 0.2 -> 0.3)
     * - Formats integers cleanly
     * - Falls back to scientific notation for very large/small numbers
     */
    fun formatGeneral(rawValue: String): FormattedCellResult {
        val num = rawValue.toDoubleOrNull() ?: return FormattedCellResult(rawValue)

        if (num.isNaN()) return FormattedCellResult("#NUM!")
        if (num.isInfinite()) return FormattedCellResult(if (num > 0) "#NUM!" else "-#NUM!")

        // Clean floating point rounding to 15 significant digits
        val bd = try {
            BigDecimal(rawValue).setScale(12, RoundingMode.HALF_UP).stripTrailingZeros()
        } catch (_: Exception) {
            BigDecimal.valueOf(num).setScale(12, RoundingMode.HALF_UP).stripTrailingZeros()
        }

        val absVal = Math.abs(num)
        if (absVal != 0.0 && (absVal >= 1e11 || absVal < 1e-5)) {
            val df = DecimalFormat("0.######E0", DecimalFormatSymbols(Locale.US))
            var sci = df.format(num)
            if (!sci.contains("E-") && sci.contains("E")) {
                sci = sci.replace("E", "E+")
            }
            return FormattedCellResult(sci)
        }

        val plain = bd.toPlainString()
        val text = if (plain.contains(".")) {
            plain.trimEnd('0').trimEnd('.')
        } else {
            plain
        }
        return FormattedCellResult(if (text.isEmpty()) "0" else text)
    }

    /**
     * Determines whether a format code represents a Date/Time format.
     */
    fun isDateFormat(formatCode: String): Boolean {
        var inQuotes = false
        var inBrackets = false
        var hasDateToken = false

        for (ch in formatCode) {
            when (ch) {
                '"' -> inQuotes = !inQuotes
                '[' -> inBrackets = true
                ']' -> inBrackets = false
                else -> {
                    if (!inQuotes && !inBrackets) {
                        val lower = ch.lowercaseChar()
                        if (lower == 'y' || lower == 'd' || lower == 'h' || lower == 's' || lower == 'm') {
                            hasDateToken = true
                        }
                    }
                }
            }
        }
        return hasDateToken
    }

    /**
     * Main numeric and date formatter handling custom multi-section format codes:
     * positive;negative;zero;text
     */
    private fun formatNumeric(
        value: Double,
        formatCode: String,
        date1904: Boolean
    ): FormattedCellResult {
        val sections = splitFormatSections(formatCode)

        // Select the appropriate section
        val (sectionPattern, isNegativeApplied) = selectSection(sections, value)

        // Parse bracketed attributes: colors [Red], currency [$€-409], condition [>100]
        var (cleanPattern, color) = parseBracketAttributes(sectionPattern)

        // Check if date format
        if (isDateFormat(cleanPattern)) {
            val dateText = formatExcelDate(value, cleanPattern, date1904)
            return FormattedCellResult(dateText, textColorRgb = color, isDate = true)
        }

        // If the section is purely literal text (e.g. "ZERO" or "N/A"), return literal directly
        if (!hasNumberPlaceholders(cleanPattern)) {
            val literalText = extractPureLiteral(cleanPattern)
            return FormattedCellResult(literalText, textColorRgb = color)
        }

        var valToFormat = if (isNegativeApplied) Math.abs(value) else value

        // Thousand scale: trailing commas scale down by 1,000 each
        val thousandScale = countTrailingCommas(cleanPattern)
        if (thousandScale > 0) {
            valToFormat /= Math.pow(1000.0, thousandScale.toDouble())
            cleanPattern = cleanPattern.trimEnd(',')
        }

        // Clean custom symbols like '_)' (spacing) and '*' (fill)
        cleanPattern = stripSpacingAndFill(cleanPattern)

        // Format decimal
        val formattedNum = try {
            val df = buildDecimalFormat(cleanPattern)
            var res = df.format(valToFormat)
            // Fix scientific notation positive exponent format e.g. E4 -> E+04 or E+4
            if (cleanPattern.contains("E+", ignoreCase = true) && !res.contains("E-") && res.contains("E")) {
                val parts = res.split("E")
                if (parts.size == 2) {
                    val expNum = parts[1].toIntOrNull()
                    if (expNum != null) {
                        res = parts[0] + "E+" + String.format(Locale.US, "%02d", expNum)
                    }
                }
            }
            res
        } catch (_: Exception) {
            formatGeneral(value.toString()).text
        }

        return FormattedCellResult(formattedNum, textColorRgb = color)
    }

    private fun hasNumberPlaceholders(pattern: String): Boolean {
        var inQuotes = false
        for (ch in pattern) {
            if (ch == '"') inQuotes = !inQuotes
            else if (!inQuotes && (ch == '0' || ch == '#' || ch == '?' || ch == '@')) {
                return true
            }
        }
        return false
    }

    private fun extractPureLiteral(pattern: String): String {
        val sb = StringBuilder()
        var inQuotes = false
        for (ch in pattern) {
            if (ch == '"') {
                inQuotes = !inQuotes
            } else if (ch != '\\') {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * Converts Excel serial number to date/time string.
     * Excel serial 1.0 is Jan 1, 1900.
     * Excel includes a fictional Feb 29, 1900 (serial 60) due to a Lotus 1-2-3 compatibility bug.
     */
    fun formatExcelDate(serialNumber: Double, pattern: String, date1904: Boolean): String {
        if (serialNumber < 0.0) return "#NUM!"

        var days = serialNumber.toLong()
        val fractionalDay = serialNumber - days

        // Handle serial 60 bug: 1900 is not a leap year in Gregorian, but Excel treats serial 60 as 1900-02-29
        if (!date1904 && days == 60L) {
            return renderSerial60(pattern, fractionalDay)
        }

        val cal = GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.US)
        cal.clear()

        if (date1904) {
            // 1904 date system: Jan 1, 1904 is serial 0
            cal.set(1904, Calendar.JANUARY, 1, 0, 0, 0)
            cal.add(Calendar.DAY_OF_YEAR, days.toInt())
        } else {
            // 1900 date system
            if (days > 60L) {
                days -= 1L // Adjust for the non-existent leap day
            }
            cal.set(1899, Calendar.DECEMBER, 31, 0, 0, 0)
            cal.add(Calendar.DAY_OF_YEAR, days.toInt())
        }

        // Add fractional day (hours, minutes, seconds, milliseconds)
        val totalMillisInDay = Math.round(fractionalDay * 86400000.0)
        cal.add(Calendar.MILLISECOND, totalMillisInDay.toInt())

        return renderDateTokens(cal, pattern, serialNumber)
    }

    private fun renderSerial60(pattern: String, fractionalDay: Double): String {
        val totalSeconds = Math.round(fractionalDay * 86400.0).toInt()
        val hour24 = totalSeconds / 3600
        val minute = (totalSeconds % 3600) / 60
        val second = totalSeconds % 60
        val hour12 = if (hour24 % 12 == 0) 12 else hour24 % 12
        val isAm = hour24 < 12

        // Synthesize a calendar with dummy year/month/day = 1900, 2 (Feb), 29
        val cal = GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.US)
        cal.clear()
        cal.set(Calendar.HOUR_OF_DAY, hour24)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, second)

        // Custom render where year=1900, month=Feb(1), day=29, dayOfWeek=Wed(4)
        return renderDateTokensInternal(
            year = 1900,
            month = 1,
            day = 29,
            dayOfWeek = 4,
            hour24 = hour24,
            hour12 = hour12,
            minute = minute,
            second = second,
            isAm = isAm,
            pattern = pattern,
            serialNumber = 60.0 + fractionalDay
        )
    }

    private fun renderDateTokens(cal: Calendar, pattern: String, serialNumber: Double): String {
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) // 0-based
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1 = Sunday
        val hour24 = cal.get(Calendar.HOUR_OF_DAY)
        val hour12 = cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }
        val minute = cal.get(Calendar.MINUTE)
        val second = cal.get(Calendar.SECOND)
        val isAm = cal.get(Calendar.AM_PM) == Calendar.AM

        return renderDateTokensInternal(
            year, month, day, dayOfWeek, hour24, hour12, minute, second, isAm, pattern, serialNumber
        )
    }

    private fun renderDateTokensInternal(
        year: Int,
        month: Int,
        day: Int,
        dayOfWeek: Int,
        hour24: Int,
        hour12: Int,
        minute: Int,
        second: Int,
        isAm: Boolean,
        pattern: String,
        serialNumber: Double
    ): String {
        val is12Hour = pattern.contains("AM/PM", ignoreCase = true) || pattern.contains("A/P", ignoreCase = true)

        val sb = StringBuilder()
        var i = 0
        var prevCharWasH = false

        while (i < pattern.length) {
            val ch = pattern[i]

            // Quoted literal
            if (ch == '"') {
                val endQuote = pattern.indexOf('"', i + 1)
                if (endQuote != -1) {
                    sb.append(pattern.substring(i + 1, endQuote))
                    i = endQuote + 1
                    continue
                }
            }

            // Backslash escape
            if (ch == '\\' && i + 1 < pattern.length) {
                sb.append(pattern[i + 1])
                i += 2
                continue
            }

            // Elapsed hours: [h]
            if (ch == '[' && pattern.substring(i).startsWith("[h]", ignoreCase = true)) {
                val totalHours = (serialNumber * 24.0).toLong()
                sb.append(totalHours)
                i += 3
                prevCharWasH = true
                continue
            }

            // AM/PM marker
            if (pattern.regionMatches(i, "AM/PM", 0, 5, ignoreCase = true)) {
                sb.append(if (isAm) "AM" else "PM")
                i += 5
                prevCharWasH = false
                continue
            }
            if (pattern.regionMatches(i, "A/P", 0, 3, ignoreCase = true)) {
                sb.append(if (isAm) "A" else "P")
                i += 3
                prevCharWasH = false
                continue
            }

            val count = countRepeatingChar(pattern, i)
            when (ch.lowercaseChar()) {
                'y' -> {
                    if (count >= 4) {
                        sb.append(String.format(Locale.US, "%04d", year))
                    } else {
                        sb.append(String.format(Locale.US, "%02d", year % 100))
                    }
                    i += count
                    prevCharWasH = false
                }
                'm' -> {
                    val isMinute = prevCharWasH || isFollowedBySecond(pattern, i + count)
                    if (isMinute) {
                        if (count >= 2) {
                            sb.append(String.format(Locale.US, "%02d", minute))
                        } else {
                            sb.append(minute)
                        }
                    } else {
                        when (count) {
                            1 -> sb.append(month + 1)
                            2 -> sb.append(String.format(Locale.US, "%02d", month + 1))
                            3 -> sb.append(MONTH_NAMES_SHORT[month])
                            else -> sb.append(MONTH_NAMES_FULL[month])
                        }
                    }
                    i += count
                }
                'd' -> {
                    when (count) {
                        1 -> sb.append(day)
                        2 -> sb.append(String.format(Locale.US, "%02d", day))
                        3 -> sb.append(DAY_NAMES_SHORT[dayOfWeek - 1])
                        else -> sb.append(DAY_NAMES_FULL[dayOfWeek - 1])
                    }
                    i += count
                    prevCharWasH = false
                }
                'h' -> {
                    val h = if (is12Hour) hour12 else hour24
                    if (count >= 2) {
                        sb.append(String.format(Locale.US, "%02d", h))
                    } else {
                        sb.append(h)
                    }
                    i += count
                    prevCharWasH = true
                }
                's' -> {
                    if (count >= 2) {
                        sb.append(String.format(Locale.US, "%02d", second))
                    } else {
                        sb.append(second)
                    }
                    i += count
                    prevCharWasH = false
                }
                else -> {
                    sb.append(ch)
                    i++
                    if (!ch.isWhitespace() && ch != ':') {
                        prevCharWasH = false
                    }
                }
            }
        }

        return sb.toString()
    }

    private fun isFollowedBySecond(pattern: String, startIdx: Int): Boolean {
        for (i in startIdx until pattern.length) {
            val c = pattern[i].lowercaseChar()
            if (c == 's') return true
            if (c == 'y' || c == 'd' || c == 'h') return false
        }
        return false
    }

    private fun countRepeatingChar(s: String, startIndex: Int): Int {
        val target = s[startIndex].lowercaseChar()
        var count = 0
        for (i in startIndex until s.length) {
            if (s[i].lowercaseChar() == target) count++ else break
        }
        return count
    }

    private fun splitFormatSections(formatCode: String): List<String> {
        val sections = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var inBrackets = false

        for (ch in formatCode) {
            when (ch) {
                '"' -> {
                    inQuotes = !inQuotes
                    sb.append(ch)
                }
                '[' -> {
                    inBrackets = true
                    sb.append(ch)
                }
                ']' -> {
                    inBrackets = false
                    sb.append(ch)
                }
                ';' -> {
                    if (!inQuotes && !inBrackets) {
                        sections.add(sb.toString())
                        sb.setLength(0)
                    } else {
                        sb.append(ch)
                    }
                }
                else -> sb.append(ch)
            }
        }
        sections.add(sb.toString())
        return sections
    }

    private fun selectSection(sections: List<String>, value: Double): Pair<String, Boolean> {
        return when (sections.size) {
            1 -> sections[0] to false
            2 -> {
                // Section 1: positive & zero, Section 2: negative
                if (value < 0.0) sections[1] to true else sections[0] to false
            }
            3 -> {
                // Section 1: positive, Section 2: negative, Section 3: zero
                when {
                    value > 0.0 -> sections[0] to false
                    value < 0.0 -> sections[1] to true
                    else -> sections[2] to false
                }
            }
            else -> {
                // 4 sections: positive, negative, zero, text
                when {
                    value > 0.0 -> sections[0] to false
                    value < 0.0 -> sections[1] to true
                    else -> sections[2] to false
                }
            }
        }
    }

    private fun parseBracketAttributes(pattern: String): Pair<String, Int?> {
        var clean = pattern
        var color: Int? = null

        val bracketRegex = Regex("\\[(.*?)\\]")
        val matches = bracketRegex.findAll(pattern)

        for (match in matches) {
            val content = match.groupValues[1].trim()
            val lower = content.lowercase()
            when {
                lower == "red" -> color = 0xFFD32F2F.toInt()
                lower == "blue" -> color = 0xFF1976D2.toInt()
                lower == "green" -> color = 0xFF388E3C.toInt()
                lower == "yellow" -> color = 0xFFFBC02D.toInt()
                lower == "magenta" -> color = 0xFF7B1FA2.toInt()
                lower == "cyan" -> color = 0xFF0097A7.toInt()
                lower == "black" -> color = 0xFF000000.toInt()
                lower == "white" -> color = 0xFFFFFFFF.toInt()
                content.startsWith("$") -> {
                    // Currency syntax in OOXML: [$<symbol>-<lcid>]
                    // e.g. [$$-409], [$€-409], [$₹-4009]
                    val afterDollar = content.substring(1)
                    val sym = if (afterDollar.contains("-")) {
                        afterDollar.substringBefore('-')
                    } else {
                        afterDollar
                    }
                    val effectiveSymbol = if (sym.isEmpty()) "$" else sym
                    // Replace the bracket with quoted currency symbol for DecimalFormat
                    clean = clean.replace(match.value, "\"$effectiveSymbol\"")
                    continue
                }
            }
            // Strip non-condition brackets
            if (!content.startsWith(">") && !content.startsWith("<") && !content.startsWith("=") && !content.equals("h", ignoreCase = true)) {
                clean = clean.replace(match.value, "")
            }
        }

        return Pair(clean, color)
    }

    private fun countTrailingCommas(pattern: String): Int {
        var commas = 0
        for (i in pattern.length - 1 downTo 0) {
            if (pattern[i] == ',') commas++ else break
        }
        return commas
    }

    private fun stripSpacingAndFill(pattern: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < pattern.length) {
            val ch = pattern[i]
            if ((ch == '_' || ch == '*') && i + 1 < pattern.length) {
                i += 2 // Skip escape token and following char
            } else {
                sb.append(ch)
                i++
            }
        }
        return sb.toString()
    }

    private fun buildDecimalFormat(pattern: String): DecimalFormat {
        // Convert OOXML double quotes "..." to single quotes '...' for DecimalFormat literals
        val converted = StringBuilder()
        var inDQuote = false
        var i = 0
        while (i < pattern.length) {
            val ch = pattern[i]
            when {
                ch == '"' -> {
                    inDQuote = !inDQuote
                    converted.append('\'')
                }
                ch == '\\' && i + 1 < pattern.length -> {
                    // Literal escaped char -> wrap in single quotes
                    converted.append('\'').append(pattern[i + 1]).append('\'')
                    i++
                }
                else -> converted.append(ch)
            }
            i++
        }

        // DecimalFormat uses E0 rather than E+00
        var clean = converted.toString()
            .replace("E+00", "E00")
            .replace("E+0", "E0")
            .replace("E-00", "E00")
            .replace("E-0", "E0")

        val symbols = DecimalFormatSymbols(Locale.US)
        return DecimalFormat(clean, symbols)
    }
}
