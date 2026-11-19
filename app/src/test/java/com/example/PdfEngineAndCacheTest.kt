package com.example

import android.graphics.Bitmap
import com.example.ui.components.PdfPageCache
import com.example.ui.components.PdfPageDimension
import com.example.ui.components.PdfReadingTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PdfEngineAndCacheTest {

    @Test
    fun testPdfPageDimensionStandardA4Portrait() {
        val dim = PdfPageDimension(595, 842)
        assertEquals(595, dim.width)
        assertEquals(842, dim.height)
        val ratio = dim.aspectRatio
        assertTrue("A4 portrait ratio should be ~0.707", ratio in 0.70f..0.71f)
    }

    @Test
    fun testPdfPageDimensionLandscapeSlide() {
        val dim = PdfPageDimension(1920, 1080)
        val ratio = dim.aspectRatio
        assertTrue("16:9 slide ratio should be ~1.777", ratio in 1.77f..1.78f)
    }

    @Test
    fun testPdfPageDimensionSafeFallback() {
        val zeroHeight = PdfPageDimension(500, 0)
        assertTrue("Zero height should safely fall back without divide by zero", zeroHeight.aspectRatio > 0f)

        val negativeDim = PdfPageDimension(-10, -10)
        assertTrue("Negative dimensions should safely fall back", negativeDim.aspectRatio > 0f)
    }

    @Test
    fun testPdfReadingThemes() {
        assertEquals("Day", PdfReadingTheme.DAY.label)
        assertEquals("Night", PdfReadingTheme.NIGHT.label)
        assertEquals("Sepia", PdfReadingTheme.SEPIA.label)
        assertEquals(3, PdfReadingTheme.entries.size)
    }

    @Test
    fun testPdfPageCachePutAndGet() {
        PdfPageCache.clear()

        val bmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val key = "/sdcard/test.pdf::0::600"

        PdfPageCache.put(key, bmp)
        val cached = PdfPageCache.get(key)
        assertNotNull("Cached bitmap should be retrievable", cached)
        assertEquals(bmp, cached)

        // Evict specific file
        PdfPageCache.evictFile("/sdcard/test.pdf")
        assertNull("Evicted file pages should no longer exist in cache", PdfPageCache.get(key))
    }

    @Test
    fun testPdfPageCacheScopedEviction() {
        PdfPageCache.clear()

        val bmpA = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        val bmpB = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)

        PdfPageCache.put("file_a.pdf::0::600", bmpA)
        PdfPageCache.put("file_b.pdf::0::600", bmpB)

        PdfPageCache.evictFile("file_a.pdf")
        assertNull(PdfPageCache.get("file_a.pdf::0::600"))
        assertNotNull(PdfPageCache.get("file_b.pdf::0::600"))

        PdfPageCache.clear()
        assertNull(PdfPageCache.get("file_b.pdf::0::600"))
    }
}
