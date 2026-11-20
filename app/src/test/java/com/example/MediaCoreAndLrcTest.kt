package com.example

import com.example.core.media.EqualizerBand
import com.example.core.media.EqualizerEngine
import com.example.core.media.EqualizerPreset
import com.example.core.media.LrcParser
import com.example.core.model.FileReference
import com.example.core.model.ProviderType
import com.example.ui.components.formatMediaDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MediaCoreAndLrcTest {

    @Test
    fun testLrcParserStandardAndFractions() {
        val lrc = """
            [ti:Test Song]
            [ar:Artist]
            [00:01.00]First line
            [00:02.5]Tenths fraction
            [00:05.123]Milliseconds fraction
            [01:00.00]One minute mark
        """.trimIndent()

        val parsed = LrcParser.parse(lrc)
        assertEquals(4, parsed.size)
        assertEquals(1000L, parsed[0].timeMs)
        assertEquals("First line", parsed[0].text)

        // 2.5 seconds = 2000 + 500 = 2500ms
        assertEquals(2500L, parsed[1].timeMs)
        assertEquals("Tenths fraction", parsed[1].text)

        // 5.123 seconds = 5123ms
        assertEquals(5123L, parsed[2].timeMs)
        assertEquals("Milliseconds fraction", parsed[2].text)

        assertEquals(60000L, parsed[3].timeMs)
    }

    @Test
    fun testLrcParserMultipleTagsPerLine() {
        val lrc = """
            [00:10.00][00:20.00]Chorus line
        """.trimIndent()

        val parsed = LrcParser.parse(lrc)
        assertEquals(2, parsed.size)
        assertEquals(10000L, parsed[0].timeMs)
        assertEquals("Chorus line", parsed[0].text)
        assertEquals(20000L, parsed[1].timeMs)
        assertEquals("Chorus line", parsed[1].text)
    }

    @Test
    fun testLrcParserHoursAndExtendedMinutes() {
        val lrc = """
            [120:30.00]Long podcast over 100 minutes
            [01:15:30.50]Hours minutes seconds
        """.trimIndent()

        val parsed = LrcParser.parse(lrc)
        assertEquals(2, parsed.size)

        // 120 minutes * 60,000 + 30,000 = 7,230,000 ms
        assertEquals(7230000L, parsed[1].timeMs)
        assertEquals("Long podcast over 100 minutes", parsed[1].text)

        // 1 hour (3600000) + 15 min (900000) + 30 sec (30000) + 500ms = 4530500 ms
        assertEquals(4530500L, parsed[0].timeMs)
        assertEquals("Hours minutes seconds", parsed[0].text)
    }

    @Test
    fun testLrcParserOffsetTagAndBom() {
        val lrc = "\uFEFF[offset:500]\n[00:01.00]Shifted line"
        val parsed = LrcParser.parse(lrc)
        assertEquals(1, parsed.size)
        // 1000ms + 500ms offset = 1500ms
        assertEquals(1500L, parsed[0].timeMs)
        assertEquals("Shifted line", parsed[0].text)
    }

    @Test
    fun testLrcBinarySearchFindCurrentLineIndex() {
        val lrc = """
            [00:01.00]Line 1
            [00:05.00]Line 2
            [00:10.00]Line 3
        """.trimIndent()
        val lines = LrcParser.parse(lrc)

        assertEquals(-1, LrcParser.findCurrentLineIndex(lines, 500L))
        assertEquals(0, LrcParser.findCurrentLineIndex(lines, 1000L))
        assertEquals(0, LrcParser.findCurrentLineIndex(lines, 3000L))
        assertEquals(1, LrcParser.findCurrentLineIndex(lines, 5000L))
        assertEquals(1, LrcParser.findCurrentLineIndex(lines, 8000L))
        assertEquals(2, LrcParser.findCurrentLineIndex(lines, 10000L))
        assertEquals(2, LrcParser.findCurrentLineIndex(lines, 99999L))
    }

    @Test
    fun testFormatMediaDuration() {
        assertEquals("00:00", formatMediaDuration(0L))
        assertEquals("00:05", formatMediaDuration(5000L))
        assertEquals("01:30", formatMediaDuration(90000L))
        assertEquals("1:01:05", formatMediaDuration(3665000L))
    }

    @Test
    fun testEqualizerNightModeNonNegativeGain() {
        val nightPreset = EqualizerEngine.presets.firstOrNull { it.name == "Night Mode" }
        assertNotNull(nightPreset)
        assertTrue("Night Mode preset loudness gain must be >= 0", nightPreset!!.loudnessGainMb >= 0)
    }

    @Test
    fun testEqualizerSafeBoundsOnCustomLevels() {
        EqualizerEngine.applyPreset(EqualizerPreset("Wild", listOf(99999, -99999, 500, 200, 0), bassBoost = 5000, virtualizer = 9000, loudnessGainMb = 8000))
        val state = EqualizerEngine.state.value

        assertEquals(1000, state.bassBoostStrength)
        assertEquals(1000, state.virtualizerStrength)
        assertEquals(1500, state.loudnessGainMb)

        val band0 = state.bands[0]
        assertTrue(band0.levelMb <= band0.maxLevelMb)

        val band1 = state.bands[1]
        assertTrue(band1.levelMb >= band1.minLevelMb)

        EqualizerEngine.resetToFlat()
        assertEquals("Flat", EqualizerEngine.state.value.selectedPreset)
    }

    @Test
    fun testFindMatchingLrcFileWithSchemePrefix() {
        val tempAudio = File.createTempFile("test_song", ".mp3")
        val tempLrc = File(tempAudio.parentFile, "${tempAudio.nameWithoutExtension}.lrc").apply {
            writeText("[00:01.00]Test")
        }

        try {
            // Test with file:// URI prefix
            val fileRef = FileReference(
                providerType = ProviderType.LOCAL,
                uriOrPath = "file://${tempAudio.absolutePath}",
                displayName = tempAudio.name,
                parentUriOrPath = tempAudio.parent,
                isDirectory = false
            )
            val matched = LrcParser.findMatchingLrcFile(fileRef)
            assertNotNull(matched)
            assertEquals(tempLrc.absolutePath, matched?.absolutePath)
        } finally {
            tempAudio.delete()
            tempLrc.delete()
        }
    }
}
