package com.mahdi.platen

import com.mahdi.platen.domain.MORSE_TRIGGER
import com.mahdi.platen.domain.MorseCodec
import com.mahdi.platen.domain.MorseController
import com.mahdi.platen.domain.MorseDir
import com.mahdi.platen.domain.MorseState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MorseTest {
    // ---- codec
    @Test fun morseToEnglish() {
        assertEquals("HELLO", MorseCodec.toEnglish(".... . .-.. .-.. ---"))
        assertEquals("HI THERE", MorseCodec.toEnglish(".... .. / - .... . .-. ."))
        assertEquals("HI THERE", MorseCodec.toEnglish(".... ..   - .... . .-. .")) // three spaces = word gap
        assertEquals("SOS", MorseCodec.toEnglish("  ... --- ...  "))
    }

    @Test fun englishToMorse() {
        assertEquals("... --- ...", MorseCodec.toMorse("SOS"))
        assertEquals("... --- ...", MorseCodec.toMorse("sos"))
        assertEquals(".... .. / - .... . .-. .", MorseCodec.toMorse("Hi there"))
        assertEquals(".... ..", MorseCodec.toMorse("hi ~~~ "))       // unknown characters are ignored
        assertEquals("", MorseCodec.toMorse("~~ #"))
    }

    @Test fun unknownCodesBecomeQuestionMarks() {
        assertEquals("H?I", MorseCodec.toEnglish(".... ........ .."))
        assertEquals("?", MorseCodec.toEnglish("abc"))
        assertEquals("?", MorseCodec.toEnglish("\u25A0")) // an overstruck cell
    }

    @Test fun normalizeFixesPhonePunctuation() {
        assertEquals("--.", MorseCodec.normalize("\u2013\u2014."))                 // en + em dash
        assertEquals("-", MorseCodec.normalize("\u2212"))                         // minus sign
        assertEquals("...", MorseCodec.normalize("\u2022\u00B7\u2219"))           // bullet, middle dot, bullet op
        assertEquals("...", MorseCodec.normalize("\u2026"))                       // ellipsis
        assertEquals("SOS", MorseCodec.toEnglish("\u2026 \u2014\u2014\u2014 \u2022\u2022\u2022"))
        assertEquals("T", MorseCodec.toEnglish("\u2014"))                          // a long-pressed dash is still one dash
    }

    @Test fun lettersForThePanelAreAToZ() {
        assertEquals(26, MorseCodec.letters.size)
        assertEquals('A' to ".-", MorseCodec.letters.first())
        assertEquals('Z' to "--..", MorseCodec.letters.last())
        MorseCodec.letters.forEach { (c, code) -> assertEquals(c.toString(), MorseCodec.toEnglish(code)) }
    }

    // ---- state machine
    private val big = 3000

    @Test fun triggerOnlyOnTheExactLine() {
        val m = MorseController()
        assertNull(m.onEnter("hello $MORSE_TRIGGER", big))
        assertNull(m.onEnter("<morsecode", big))
        assertNull(m.onEnter("<Morsecode>", big))
        assertNull(m.onEnter("<morsecod\u25A0>", big)) // an overstruck cell never matches
        assertEquals(MorseState.Off, m.state)
        assertEquals(MorseController.MENU, m.onEnter(MORSE_TRIGGER, big))
        assertEquals(MorseState.Menu, m.state)
    }

    @Test fun menuChoices() {
        val m = MorseController()
        m.onEnter(MORSE_TRIGGER, big)
        assertEquals(MorseController.hintFor(MorseDir.MORSE_TO_ENGLISH), m.onEnter("1", big))
        assertEquals(MorseState.On(MorseDir.MORSE_TO_ENGLISH), m.state)

        val m2 = MorseController()
        m2.onEnter(MORSE_TRIGGER, big)
        assertEquals(MorseController.hintFor(MorseDir.ENGLISH_TO_MORSE), m2.onEnter("2", big))
        assertEquals(MorseState.On(MorseDir.ENGLISH_TO_MORSE), m2.state)

        val m3 = MorseController()
        m3.onEnter(MORSE_TRIGGER, big)
        assertNull(m3.onEnter("3", big))                 // anything else leaves the menu
        assertEquals(MorseState.Off, m3.state)
        m3.onEnter(MORSE_TRIGGER, big)
        assertNull(m3.onEnter("a 1", big))
        assertEquals(MorseState.Off, m3.state)
    }

    @Test fun translatesEachFinishedLineAndExitsOnEmptyEnter() {
        val m = MorseController()
        m.onEnter(MORSE_TRIGGER, big); m.onEnter("1", big)
        assertEquals("=> HELLO\n", m.onEnter(".... . .-.. .-.. ---", big))
        assertEquals("=> HI THERE\n", m.onEnter(".... .. / - .... . .-. .", big))
        assertEquals(MorseState.On(MorseDir.MORSE_TO_ENGLISH), m.state)
        assertNull(m.onEnter("", big))
        assertEquals(MorseState.Off, m.state)
        assertNull(m.onEnter(".... ..", big))           // Off again: plain typing

        val e = MorseController()
        e.onEnter(MORSE_TRIGGER, big); e.onEnter("2", big)
        assertEquals("=> ... --- ...\n", e.onEnter("SOS", big))
        assertEquals("=> ?\n", e.onEnter("~~", big))     // nothing translatable
        assertNull(e.onEnter("   ", big))                // blank counts as empty
        assertEquals(MorseState.Off, e.state)
    }

    @Test fun outputThatDoesNotFitPrintsNoSpace() {
        val m = MorseController()
        m.onEnter(MORSE_TRIGGER, big); m.onEnter("2", big)
        assertEquals(MorseController.NO_SPACE, m.onEnter("SOS SOS", 20))   // 30 chars needed, never truncated
        assertNull(m.onEnter("SOS SOS", 5))                                // not even room for the notice
        assertEquals(MorseState.On(MorseDir.ENGLISH_TO_MORSE), m.state)     // still on; just out of paper
        assertEquals("=> ...\n", m.onEnter("S", 7))                        // exactly fits

        val menu = MorseController()
        assertEquals(MorseController.NO_SPACE, menu.onEnter(MORSE_TRIGGER, 20))
        assertEquals(MorseState.Off, menu.state)                            // no menu without paper for it
    }
}
