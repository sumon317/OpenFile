package com.example

import com.example.core.document.ColorMath
import com.example.core.document.ColorTransform
import com.example.core.document.ShapeBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresentationTest {

    // ---------------------------------------------------------------------------------------------
    // EMU Math & Coordinate Tests
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testEmuToPtConversion() {
        // 1 pt = 12,700 EMU
        val emuFor18Pt = 18L * 12700L
        assertEquals(228600L, emuFor18Pt)
        assertEquals(18f, emuFor18Pt / 12700f, 0.001f)
    }

    @Test
    fun testEmuToInchConversion() {
        // 1 inch = 914,400 EMU
        val emuFor10Inches = 10L * 914400L
        assertEquals(9144000L, emuFor10Inches)
    }

    @Test
    fun testShapeBoundsValidity() {
        val valid = ShapeBounds(xEmu = 0L, yEmu = 0L, widthEmu = 1000L, heightEmu = 2000L)
        assertTrue(valid.isValid)

        val invalidZeroW = ShapeBounds(widthEmu = 0L, heightEmu = 1000L)
        assertFalse(invalidZeroW.isValid)

        val invalidZeroH = ShapeBounds(widthEmu = 1000L, heightEmu = 0L)
        assertFalse(invalidZeroH.isValid)
    }

    // ---------------------------------------------------------------------------------------------
    // Color Transformations (lumMod, lumOff, tint, shade, alpha) Tests
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testColorTransform_Alpha() {
        val opaqueBlue = 0xFF0000FF.toInt()
        val halfAlphaTransform = ColorTransform(alpha = 0.5f)
        val transformed = ColorMath.applyTransforms(opaqueBlue, halfAlphaTransform)
        val a = (transformed ushr 24) and 0xFF
        assertEquals(127, a)
    }

    @Test
    fun testColorTransform_Shade() {
        // Shade scales R, G, B down
        val white = 0xFFFFFFFF.toInt()
        val shadeTransform = ColorTransform(shade = 0.5f)
        val transformed = ColorMath.applyTransforms(white, shadeTransform)
        val r = (transformed ushr 16) and 0xFF
        val g = (transformed ushr 8) and 0xFF
        val b = transformed and 0xFF
        assertEquals(127, r)
        assertEquals(127, g)
        assertEquals(127, b)
    }

    @Test
    fun testColorTransform_Tint() {
        // Tint blends towards white: r * t + 255 * (1 - t)
        val black = 0xFF000000.toInt()
        val tintTransform = ColorTransform(tint = 0.5f)
        val transformed = ColorMath.applyTransforms(black, tintTransform)
        val r = (transformed ushr 16) and 0xFF
        assertEquals(127, r)
    }

    @Test
    fun testColorTransform_LumMod() {
        val red = 0xFFFF0000.toInt()
        val lumModTransform = ColorTransform(lumMod = 0.6f)
        val transformed = ColorMath.applyTransforms(red, lumModTransform)
        val r = (transformed ushr 16) and 0xFF
        assertTrue(r in 140..170)
    }
}
