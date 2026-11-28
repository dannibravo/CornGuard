package com.cornguard.app.ui.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LeafFrameTest {

    private fun assertInside(f: LeafFrame) {
        assertTrue("left ${f.left}", f.left >= 0f)
        assertTrue("top ${f.top}", f.top >= 0f)
        assertTrue("right ${f.right}", f.right <= f.imageWidth + 0.001f)
        assertTrue("bottom ${f.bottom}", f.bottom <= f.imageHeight + 0.001f)
        assertTrue("side ${f.side} >= min ${f.minSide}", f.side >= f.minSide - 0.001f)
    }

    @Test
    fun `default frame is the old centre square`() {
        val f = LeafFrame.centered(1024, 768)
        assertEquals(768f, f.side, 0f)
        assertEquals(128f, f.left, 0f)
        assertEquals(0f, f.top, 0f)
        assertEquals(Triple(128, 0, 768), f.cropPixels())
    }

    @Test
    fun `moving stops at the image edges`() {
        val f = LeafFrame.centered(1000, 800, 0.5f) // side 400
        val moved = f.moveBy(5000f, -5000f)
        assertEquals(600f, moved.left, 0f)
        assertEquals(0f, moved.top, 0f)
        assertInside(moved)
    }

    @Test
    fun `pinching is limited to min and max size`() {
        val f = LeafFrame.centered(1000, 800, 0.5f)
        assertEquals(800f, f.scaleBy(100f).side, 0f)
        assertEquals(f.minSide, f.scaleBy(0.001f).side, 0f)
        assertEquals(120f, f.minSide, 0.001f) // 15% of 800 beats the 112 px floor
        assertInside(f.scaleBy(100f))
    }

    @Test
    fun `pinching near an edge keeps the box inside`() {
        val f = LeafFrame.centered(1000, 800, 0.3f).moveBy(-5000f, -5000f)
        val grown = f.scaleBy(2f)
        assertEquals(480f, grown.side, 0.001f)
        assertInside(grown)
    }

    @Test
    fun `corner drag keeps the opposite corner fixed`() {
        val f = LeafFrame.centered(1000, 800, 0.5f) // 300..700 x 200..600
        val r = f.resizeFromAnchor(f.left, f.top, f.left + 250f, f.top + 100f)
        assertEquals(300f, r.left, 0f)
        assertEquals(200f, r.top, 0f)
        assertEquals(250f, r.side, 0f)
    }

    @Test
    fun `corner drag up-left grows towards the pointer and stops at the edge`() {
        val f = LeafFrame.centered(1000, 800, 0.5f)
        val r = f.resizeFromAnchor(f.right, f.bottom, -999f, -999f)
        assertEquals(600f, r.side, 0f) // limited by room above the anchor (bottom = 600)
        assertEquals(700f, r.right, 0.001f)
        assertEquals(600f, r.bottom, 0.001f)
        assertInside(r)
    }

    @Test
    fun `tiny images still give a valid crop`() {
        val f = LeafFrame.centered(50, 80)
        assertEquals(50f, f.minSide, 0f)
        val (l, t, s) = f.moveBy(10f, 999f).cropPixels()
        assertTrue(l >= 0 && t >= 0 && l + s <= 50 && t + s <= 80)
    }
}
