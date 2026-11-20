package com.mahdi.platen

import com.mahdi.platen.domain.CorrectionMode
import com.mahdi.platen.domain.Glyph
import com.mahdi.platen.domain.NoteTitle
import com.mahdi.platen.domain.Strike
import com.mahdi.platen.domain.TypeEvent
import com.mahdi.platen.domain.TypewriterEngine
import com.mahdi.platen.ui.stage.StageGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V32DomainTest {
    private fun glyphs(s: String) = s.map { Glyph(it.toString()) }

    // ---------------------------------------------------------------- erase
    @Test fun eraseRemovesBackAcrossLineBreaks() {
        val e = TypewriterEngine().apply { mode = CorrectionMode.DELETE }
        e.type("Title\nab")
        assertEquals(8, e.activeCount)
        assertEquals(TypeEvent.ERASE, e.backspace())
        assertEquals(TypeEvent.ERASE, e.backspace())
        assertEquals("Title\n", e.fieldText())
        assertEquals(TypeEvent.ERASE, e.backspace())              // the newline itself: back onto line 1
        assertEquals("Title", e.fieldText())
        assertEquals(5, e.activeCount)
        e.type("s")
        assertEquals("Titles", e.fieldText())
        repeat(6) { e.backspace() }
        assertNull(e.backspace())                                 // nothing left
        assertEquals(0, e.activeCount)
    }

    @Test fun overstrikeOnlyWithoutErase() {
        val e = TypewriterEngine().apply { mode = CorrectionMode.STRIKE }
        e.type("ab"); e.backspace()
        assertEquals(listOf(TypeEvent.STRIKE), e.type("c"))
        assertTrue(e.glyphs[1].struck)
        val d = TypewriterEngine().apply { mode = CorrectionMode.DELETE }
        d.type("ab"); d.backspace()
        assertEquals(listOf(TypeEvent.KEY), d.type("c"))
        assertEquals(listOf(Glyph("a"), Glyph("c")), d.glyphs)
    }

    // ---------------------------------------------------------------- title
    @Test fun titleIsTheFirstLine() {
        val g = glyphs("My letter\nDear you,\nhello")
        assertEquals(9, NoteTitle.length(g))
        assertEquals("My letter", NoteTitle.of(g))
        assertEquals("My letter", NoteTitle.display(g))
        assertEquals("Dear you,\nhello", NoteTitle.body(g).joinToString("") { it.text })
        assertTrue(NoteTitle.isTitle(g, 0)); assertTrue(NoteTitle.isTitle(g, 8))
        assertTrue(!NoteTitle.isTitle(g, 9)); assertTrue(!NoteTitle.isTitle(g, 10))
    }

    @Test fun titleWhileTypingTheFirstLineAndOldNotes() {
        assertEquals("Half a tit", NoteTitle.of(glyphs("Half a tit")))  // no Return yet: the whole line
        assertEquals(NoteTitle.UNTITLED, NoteTitle.display(emptyList()))
        assertEquals(NoteTitle.UNTITLED, NoteTitle.display(glyphs("\nbody")))
        // An overstruck cell reads as the key typed over it; old v2 struck cells vanish.
        val old = listOf(Glyph("T"), Glyph("o", struck = true, over = "e"), Glyph("a"), Glyph("x", struck = true), Glyph("\n"))
        assertEquals("Tea", NoteTitle.of(old))
    }

    @Test fun fileStemsAreSafe() {
        assertEquals("platen-my-letter-to-you", NoteTitle.fileStem(glyphs("My letter: to you!\nx"), 3))
        assertEquals("platen-note-3", NoteTitle.fileStem(glyphs("   \nx"), 3))
        assertEquals("platen-سلام-دنیا", NoteTitle.fileStem(glyphs("سلام دنیا"), 3))
        assertTrue(NoteTitle.fileStem(glyphs("a".repeat(200)), 1).length <= 47)
    }

    @Test fun longTitlesAreShortenedForDisplay() {
        val t = NoteTitle.display(glyphs("x".repeat(100)), maxChars = 20)
        assertEquals(20, t.length); assertTrue(t.endsWith("…"))
    }

    // ---------------------------------------------------------------- strike
    @Test fun struckCellIsOnlyASingleKeyAtTheCarriage() {
        val a = glyphs("Hel")
        assertEquals(3, Strike.struckCell(a, 3, glyphs("Hell"), 4))             // appended letter
        assertEquals(-1, Strike.struckCell(a, 3, glyphs("Hel "), 4))            // space: no hammer
        assertEquals(-1, Strike.struckCell(a, 3, glyphs("Hel\n"), 4))           // Return
        assertEquals(-1, Strike.struckCell(a, 3, glyphs("Hello"), 5))           // two keys at once (paste)
        assertEquals(-1, Strike.struckCell(a, 3, glyphs("He"), 2))              // erase
        assertEquals(-1, Strike.struckCell(a, 3, a, 3))                         // nothing
        assertEquals(-1, Strike.struckCell(a, 3, glyphs("Xell"), 4))            // earlier cell changed too
        // typing over the "e" (typewriter mode): overstrike and re-ink are strikes on cell 1
        val over = listOf(Glyph("H"), Glyph("e", struck = true, over = "a"), Glyph("l"))
        assertEquals(1, Strike.struckCell(a, 1, over, 2))
        assertEquals(1, Strike.struckCell(a, 1, a, 2))
    }

    @Test fun glyphIsStampedAtImpactBeforeTheCarriageSteps() {
        val t = Strike.timeline.toMap()
        val order = Strike.timeline.map { it.first }
        assertEquals(listOf(Strike.Phase.LEVER_UP, Strike.Phase.IMPACT_STAMP, Strike.Phase.LEVER_DOWN, Strike.Phase.CARRIAGE_STEP), order)
        assertEquals(t[Strike.Phase.LEVER_UP]!! + Strike.RISE_MS, t[Strike.Phase.IMPACT_STAMP])  // stamped when the lever arrives
        assertTrue(t[Strike.Phase.CARRIAGE_STEP]!! > t[Strike.Phase.IMPACT_STAMP]!!)            // carriage moves after
        // measured on the reference: a full keystroke cycle is well under 0.25 s
        assertTrue(Strike.RISE_MS + Strike.STEP_DELAY_MS + Strike.STEP_MS <= 250)
        assertTrue(Strike.RISE_MS in 20..60)
    }

    // ---------------------------------------------------------------- stage geometry
    @Test fun portraitAndLandscapeGeometry() {
        val land = StageGeometry.of(1280f, 889f, 1f, portrait = false)
        assertEquals(0.153f * 1280f, land.paperLeft, 0.5f)
        assertEquals(0.695f * 1280f, land.paperWidth, 0.5f)
        assertEquals(0.326f * 889f, land.drumTop, 0.5f)
        val port = StageGeometry.of(889f, 915f, 1f, portrait = true)
        assertEquals(0f, port.paperLeft, 0.01f)
        assertEquals(889f, port.paperWidth, 0.01f)
        assertTrue(port.toolbarTop > port.carriageBottom)                 // the pill sits under the rod
        assertTrue(port.toolbarTop + port.toolbarHeight <= 915f)
        assertTrue(port.drumTop in 0.3f * 915f..0.42f * 915f)
    }

    @Test fun rowsRollOverTheDrum() {
        val g = StageGeometry.of(1280f, 889f, 1f, portrait = false)
        val typing = g.project(g.baseline)!!
        assertEquals(g.baseline, typing.baseline, 0.01f); assertEquals(1f, typing.scaleX, 0f)
        var prev = typing
        var y = g.baseline
        var hidden = false
        repeat(60) {
            y -= g.lineHeight
            val p = g.project(y)
            if (p == null) { hidden = true; return@repeat }
            assertTrue(p.baseline <= prev.baseline)          // still above the previous row
            assertTrue(p.baseline >= g.drumTop - 0.5f)       // never above the sheet's top edge
            assertTrue(p.scaleY <= prev.scaleY + 1e-4f)      // more and more foreshortened
            prev = p
        }
        assertTrue("old rows roll out of sight", hidden)
        assertNotNull(g.project(g.bendStart + 1f))
    }
}
