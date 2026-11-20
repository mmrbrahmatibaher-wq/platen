package com.mahdi.platen

import com.mahdi.platen.domain.GlyphJitter
import com.mahdi.platen.domain.NoteLimits
import com.mahdi.platen.domain.Prompts
import com.mahdi.platen.export.ImageFormat
import com.mahdi.platen.export.Pagination
import com.mahdi.platen.export.PaperSize
import com.mahdi.platen.ui.Routes
import com.mahdi.platen.ui.lamp.LampMath
import com.mahdi.platen.ui.theme.PaperTexture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class DomainRulesTest {
    @Test fun freeTierAllowsThreeNotes() {
        assertTrue(NoteLimits.canCreate(0, studio = false))
        assertTrue(NoteLimits.canCreate(2, studio = false))
        assertFalse(NoteLimits.canCreate(3, studio = false))
        assertFalse(NoteLimits.canCreate(10, studio = false))
        assertEquals(1, NoteLimits.remaining(2, false))
        assertEquals(0, NoteLimits.remaining(7, false))
    }

    @Test fun studioRemovesTheCap() {
        assertTrue(NoteLimits.canCreate(3, studio = true))
        assertTrue(NoteLimits.canCreate(10_000, studio = true))
    }

    @Test fun jitterIsStableAndBounded() {
        for (i in 0 until 5000) {
            val r = GlyphJitter.rotation(i, 42)
            assertTrue(abs(r) <= 0.5f)
            assertEquals(r, GlyphJitter.rotation(i, 42))
            val a = GlyphJitter.alpha(i, 42)
            assertTrue(a in 0.74f..1f)
            assertTrue(GlyphJitter.offset(i, 42) in -1f..1f)
        }
    }

    @Test fun jitterVariesBetweenGlyphsAndNotes() {
        val rs = (0 until 100).map { GlyphJitter.rotation(it, 1) }.toSet()
        assertTrue(rs.size > 90)
        assertNotEquals(GlyphJitter.rotation(5, 1), GlyphJitter.rotation(5, 2))
        assertTrue((0 until 1000).any { GlyphJitter.rotation(it, 1) < -0.4f })
        assertTrue((0 until 1000).any { GlyphJitter.rotation(it, 1) > 0.4f })
    }

    @Test fun thereAre120DistinctPrompts() {
        assertEquals(120, Prompts.all.size)
        assertEquals(120, Prompts.all.toSet().size)
        assertTrue(Prompts.all.all { it.isNotBlank() && it.length < 120 })
    }

    @Test fun randomPromptAvoidsRepeat() {
        val r = Random(3)
        var p = Prompts.random(r)
        repeat(200) { val n = Prompts.random(r, except = p); assertNotEquals(p, n); p = n }
    }

    @Test fun storyImageIsNineBySixteen() {
        assertEquals(9f / 16f, ImageFormat.STORY.aspect, 0.001f)
        assertEquals(1080, ImageFormat.STANDARD.widthPx)
    }

    @Test fun paperSizesInPoints() {
        assertEquals(595, PaperSize.A4.widthPt); assertEquals(842, PaperSize.A4.heightPt)
        assertEquals(612, PaperSize.LETTER.widthPt); assertEquals(792, PaperSize.LETTER.heightPt)
        assertEquals(420, PaperSize.A5.widthPt); assertEquals(595, PaperSize.A5.heightPt)
        // ISO sizes keep the √2 ratio; A5 is half of A4.
        assertEquals(Math.sqrt(2.0), PaperSize.A4.heightPt / PaperSize.A4.widthPt.toDouble(), 0.01)
        assertEquals(PaperSize.A4.widthPt, PaperSize.A5.heightPt)
        PaperSize.entries.forEach {
            assertTrue(it.contentWidth > 250)
            assertTrue(it.contentHeight > 400)
        }
    }

    @Test fun paginationFitsWholeLines() {
        val tops = (0 until 10).map { it * 20f }
        val bottoms = tops.map { it + 20f }
        assertEquals(listOf(0..3, 4..7, 8..9), Pagination.pages(tops, bottoms, 85f))
        assertEquals(listOf(0..9), Pagination.pages(tops, bottoms, 200f))
    }

    @Test fun paginationHandlesEmptyAndOversizedLines() {
        assertEquals(1, Pagination.pages(emptyList(), emptyList(), 100f).size)
        assertEquals(listOf(0..0, 1..1), Pagination.pages(listOf(0f, 300f), listOf(300f, 310f), 100f))
    }

    @Test fun fitScale() {
        assertEquals(1f, Pagination.fitScale(100f, 200f))
        assertEquals(0.5f, Pagination.fitScale(400f, 200f), 0.0001f)
        assertEquals(0.4f, Pagination.fitScale(4000f, 200f))
    }

    @Test fun paperTextureIsOpaqueTileableAndNearBaseColour() {
        val base = 0xFFF8F2E4.toInt()
        val px = PaperTexture.generate(64, base, seed = 1)
        assertEquals(64 * 64, px.size)
        assertTrue(px.all { (it ushr 24) == 0xFF })
        val avgR = px.map { (it shr 16) and 0xFF }.average()
        assertEquals(0xF8.toDouble(), avgR, 6.0)
        assertTrue(px.toSet().size > 20) // actually textured
        assertTrue(PaperTexture.generate(64, base, 1).contentEquals(px)) // deterministic
    }

    @Test fun lampMath() {
        assertTrue(LampMath.beamFraction(0f) < LampMath.beamFraction(1f))
        assertTrue(LampMath.darkness(1f) < LampMath.darkness(0f))
        assertTrue(LampMath.lightColor(1f).blue < LampMath.lightColor(0f).blue)
        assertEquals(LampMath.beamFraction(1f), LampMath.beamFraction(5f))
    }

    @Test fun routes() {
        assertEquals(12L, Routes.editorId(Routes.editor(12)))
        assertNull(Routes.editorId(Routes.SETTINGS))
        assertNull(Routes.editorId("editor/x"))
    }
}
