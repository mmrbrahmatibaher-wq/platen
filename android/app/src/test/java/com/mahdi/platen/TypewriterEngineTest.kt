package com.mahdi.platen

import com.mahdi.platen.domain.CorrectionMode
import com.mahdi.platen.domain.Glyph
import com.mahdi.platen.domain.GlyphCodec
import com.mahdi.platen.domain.TypeEvent
import com.mahdi.platen.domain.TypewriterEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TypewriterEngineTest {

    @Test fun typesCharactersInOrder() {
        val e = TypewriterEngine()
        e.type("abc")
        assertEquals(listOf("a", "b", "c"), e.glyphs.map { it.text })
        assertEquals("abc", e.activeText())
        assertEquals(3, e.activeCount)
    }

    @Test fun eventsForKeySpaceAndReturn() {
        val e = TypewriterEngine()
        assertEquals(listOf(TypeEvent.KEY, TypeEvent.SPACE, TypeEvent.KEY, TypeEvent.RETURN), e.type("a b\n"))
    }

    @Test fun emptyInputDoesNothing() {
        val e = TypewriterEngine()
        assertTrue(e.type("").isEmpty())
        assertEquals(0, e.size)
    }

    @Test fun typewriterBackspaceOnlyMovesTheCarriage() {
        val e = TypewriterEngine()
        e.type("cat")
        assertEquals(TypeEvent.BACKSPACE, e.backspace())
        assertEquals(3, e.size)
        assertEquals(2, e.cursor)
        assertFalse(e.glyphs.any { it.struck })
        assertEquals("ca", e.fieldText())
        assertEquals(3, e.activeCount)
    }

    @Test fun typingADifferentLetterOverstrikesTheCell() {
        val e = TypewriterEngine()
        e.type("abc")
        e.backspace(); e.backspace()
        assertEquals(listOf(TypeEvent.STRIKE), e.type("X"))
        assertEquals(3, e.size)
        assertEquals(Glyph("b", struck = true, over = "X"), e.glyphs[1])
        assertEquals("aX", e.fieldText())
        assertEquals("aXc", GlyphCodec.plainText(e.glyphs))
        assertEquals("abc", GlyphCodec.plainText(e.glyphs, includeStruck = true))
    }

    @Test fun sameLetterReInksAndSpaceJustAdvances() {
        val e = TypewriterEngine()
        e.type("ab c")
        repeat(4) { e.backspace() }
        assertEquals(listOf(TypeEvent.KEY, TypeEvent.SPACE), e.type("a "))
        assertFalse(e.glyphs.any { it.struck })
        assertEquals(listOf(TypeEvent.KEY), e.type("x")) // a blank cell simply takes the letter
        assertEquals(Glyph("x"), e.glyphs[2])
        assertEquals(4, e.size)
    }

    @Test fun backspaceStopsAtTheLeftMargin() {
        val e = TypewriterEngine()
        assertNull(e.backspace())
        e.type("ab\nc")
        assertEquals(TypeEvent.BACKSPACE, e.backspace())
        assertNull(e.backspace()) // never climbs back over a carriage return
        assertEquals(3, e.cursor)
    }

    @Test fun returnAlwaysFeedsAtTheEnd() {
        val e = TypewriterEngine()
        e.type("abc")
        e.backspace(); e.backspace()
        assertEquals(listOf(TypeEvent.RETURN), e.type("\n"))
        assertEquals("abc\n", GlyphCodec.plainText(e.glyphs))
        assertEquals(4, e.cursor)
    }

    @Test fun deleteModeRemovesGlyph() {
        val e = TypewriterEngine()
        e.mode = CorrectionMode.DELETE
        e.type("dog")
        assertEquals(TypeEvent.ERASE, e.backspace())
        assertEquals("do", e.activeText())
        assertEquals(2, e.size)
    }

    @Test fun deleteModeRemovesNewlineToo() {
        val e = TypewriterEngine()
        e.mode = CorrectionMode.DELETE
        e.type("a\n")
        assertEquals(TypeEvent.ERASE, e.backspace())
        assertEquals(listOf(Glyph("a")), e.glyphs)
    }

    @Test fun legacyStruckNotesStillLoad() {
        val e = TypewriterEngine()
        e.reset(GlyphCodec.decode("""[{"c":"a"},{"c":"b","s":true},{"c":"c"}]"""))
        assertEquals(3, e.size)
        assertTrue(e.glyphs[1].struck)
        assertNull(e.glyphs[1].over)
        assertEquals("ac", GlyphCodec.plainText(e.glyphs))
    }

    @Test fun carriageReturnsAreNormalised() {
        val e = TypewriterEngine()
        e.type("a\r\nb\rc")
        assertEquals("a\nb\nc", e.activeText())
        assertEquals(5, e.size)
    }

    @Test fun tabsBecomeSpacesAndControlCharsAreDropped() {
        val e = TypewriterEngine()
        e.type("a\tb\u0007c\u0000")
        assertEquals("a bc", e.activeText())
    }

    @Test fun hardLimitOnLiveCharacters() {
        val e = TypewriterEngine()
        val ev = e.type("x".repeat(3005))
        assertEquals(3000, e.activeCount)
        assertEquals(3000, e.size)
        assertEquals(TypeEvent.LIMIT, ev.last())
        assertTrue(e.isFull)
        assertEquals(listOf(TypeEvent.LIMIT), e.type("y"))
        assertEquals(3000, e.size)
    }

    @Test fun limitCountsNewlinesAndSpaces() {
        val e = TypewriterEngine(limit = 4)
        e.type("a \nbc")
        assertEquals("a \nb", e.activeText())
    }

    @Test fun overtypingNeverGrowsTheSheet() {
        val e = TypewriterEngine(limit = 3)
        e.type("abc")
        assertEquals(listOf(TypeEvent.LIMIT), e.type("d"))
        e.backspace()
        assertEquals(listOf(TypeEvent.STRIKE), e.type("d")) // overstrike fits inside a full sheet
        assertEquals(3, e.size)
        repeat(20) { e.backspace(); e.type("q") }
        assertEquals(3, e.size)
    }

    @Test fun surrogatePairEmojiIsOneGlyph() {
        val e = TypewriterEngine()
        e.type("a😀b")
        assertEquals(listOf("a", "😀", "b"), e.glyphs.map { it.text })
        e.backspace(); e.backspace()
        assertEquals("a", e.fieldText())
        e.type("z")
        assertTrue(e.glyphs[1].struck)
        assertEquals("😀", e.glyphs[1].text)
        assertEquals("z", e.glyphs[1].over)
    }

    @Test fun zwjFamilyAndFlagsAndSkinTonesAreSingleGlyphs() {
        val e = TypewriterEngine()
        e.type("👨‍👩‍👧🇮🇷👍🏽")
        assertEquals(listOf("👨‍👩‍👧", "🇮🇷", "👍🏽"), e.glyphs.map { it.text })
        assertEquals(3, e.activeCount)
    }

    @Test fun loneSurrogatesAreDropped() {
        val e = TypewriterEngine()
        e.type("a\uD83Db\uDE00c")
        assertEquals("abc", e.activeText())
    }

    @Test fun combiningMarkTypedSeparatelyJoinsPreviousGlyph() {
        val e = TypewriterEngine()
        e.type("e")
        e.type("\u0301")
        assertEquals(listOf("e\u0301"), e.glyphs.map { it.text })
        assertEquals(1, e.activeCount)
    }

    @Test fun skinToneTypedSeparatelyJoinsEmoji() {
        val e = TypewriterEngine()
        e.type("👍")
        e.type("🏽")
        assertEquals(listOf("👍🏽"), e.glyphs.map { it.text })
    }

    @Test fun markAfterNewlineOrStruckGlyphDoesNotJoin() {
        val e = TypewriterEngine()
        e.type("ab")
        e.backspace()
        e.type("x") // b overstruck by x
        e.type("\u0301")
        assertEquals(3, e.size)
        e.type("\n\u0301")
        assertEquals("\n", e.glyphs[3].text)
        assertEquals("\u0301", e.glyphs[4].text)
    }

    @Test fun persianTextWithZwnjAndHarakat() {
        val e = TypewriterEngine()
        val text = "\u0645\u06CC\u200C\u062E\u0648\u0627\u0647\u0645 \u0633\u064E\u0644\u0627\u0645" // می‌خواهم سَلام
        e.type(text)
        // The ZWNJ (half-space) stays with ی and the fatha stays with س.
        assertTrue(e.glyphs.any { it.text == "\u06CC\u200C" })
        assertTrue(e.glyphs.any { it.text == "\u0633\u064E" })
        assertEquals(text, e.activeText())
        assertEquals(12, e.activeCount)
        e.backspace()
        assertEquals(text.dropLast(1), e.fieldText())
    }

    @Test fun columnCountsGlyphsSinceLastNewline() {
        val e = TypewriterEngine()
        e.type("abc\nde")
        assertEquals(2, e.column())
        e.backspace()
        assertEquals(1, e.column()) // the carriage stepped back one cell
        e.type("\n")
        assertEquals(0, e.column())
    }

    @Test fun marginBellRingsAtBellColumn() {
        val e = TypewriterEngine()
        e.bellColumn = 5
        val ev = e.type("abcdefg")
        assertEquals(1, ev.count { it == TypeEvent.BELL })
        assertEquals(TypeEvent.BELL, ev[5])
        e.type("\n")
        assertTrue(e.type("abcde").contains(TypeEvent.BELL))
    }

    @Test fun bellDisabledByDefault() {
        assertFalse(TypewriterEngine().type("x".repeat(80)).contains(TypeEvent.BELL))
    }

    @Test fun resetRestoresStateAndCounts() {
        val e = TypewriterEngine()
        e.reset(listOf(Glyph("a"), Glyph("b", true, "x"), Glyph(""), Glyph("c")))
        assertEquals(3, e.size)
        assertEquals(3, e.activeCount)
        assertEquals(3, e.cursor)
        assertEquals("axc", e.fieldText())
    }

    @Test fun glyphsIsASnapshot() {
        val e = TypewriterEngine()
        e.type("a")
        val snap = e.glyphs
        e.type("b")
        assertEquals(1, snap.size)
    }

    @Test fun serializationRoundTripPreservesEverything() {
        val e = TypewriterEngine()
        e.type("Hi 😀\nسلام")
        e.backspace()
        e.type("é👍🏽")
        assertTrue(e.glyphs.any { it.over == "é" })
        val json = GlyphCodec.encode(e.glyphs)
        assertEquals(e.glyphs, GlyphCodec.decode(json))
    }

    @Test fun codecIsCompactAndTolerant() {
        val json = GlyphCodec.encode(listOf(Glyph("a"), Glyph("b", true), Glyph("c", true, "d")))
        assertEquals("""[{"c":"a"},{"c":"b","s":true},{"c":"c","s":true,"o":"d"}]""", json)
        assertEquals(emptyList<Glyph>(), GlyphCodec.decode("not json"))
        assertEquals(emptyList<Glyph>(), GlyphCodec.decode(null))
        assertEquals(listOf(Glyph("x")), GlyphCodec.decode("""[{"c":"x","extra":1},{"c":""}]"""))
    }

    @Test fun plainTextOptionallyIncludesStruck() {
        val g = listOf(Glyph("a"), Glyph("b", true), Glyph("c"), Glyph("d", true, "e"))
        assertEquals("ace", GlyphCodec.plainText(g))
        assertEquals("abcd", GlyphCodec.plainText(g, includeStruck = true))
    }
}
