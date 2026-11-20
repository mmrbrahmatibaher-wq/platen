package com.mahdi.platen.domain

/**
 * Morse mode, after Mohammad Mahdi's MorseMode.kt draft: a pure codec plus a small state machine.
 * The machine only ever *appends* (a typewriter can't change what's already on the paper), so
 * translation happens when a line is finished with Enter, and the result is printed below it.
 */
object MorseCodec {
    private val table: Map<Char, String> = linkedMapOf(
        'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".",
        'F' to "..-.", 'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---",
        'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.", 'O' to "---",
        'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-",
        'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--",
        'Z' to "--..",
        '1' to ".----", '2' to "..---", '3' to "...--", '4' to "....-", '5' to ".....",
        '6' to "-....", '7' to "--...", '8' to "---..", '9' to "----.", '0' to "-----",
        '.' to ".-.-.-", ',' to "--..--", '?' to "..--..", '!' to "-.-.--",
        '\'' to ".----.", ':' to "---...", ';' to "-.-.-.", '=' to "-...-",
        '+' to ".-.-.", '-' to "-....-", '@' to ".--.-.", '/' to "-..-.",
        '(' to "-.--.", ')' to "-.--.-", '&' to ".-...", '"' to ".-..-.",
    )

    private val reverse: Map<String, Char> = table.entries.associate { (k, v) -> v to k }

    /** A-Z in order, for the reference panel. */
    val letters: List<Pair<Char, String>> = table.entries.filter { it.key in 'A'..'Z' }.map { it.key to it.value }

    fun codeFor(c: Char): String? = table[c.uppercaseChar()]

    /** "HI THERE" -> ".... .. / - .... . .-. ." (characters without a code are skipped). */
    fun toMorse(text: String): String =
        text.trim().uppercase()
            .split(Regex("\\s+"))
            .map { word -> word.mapNotNull { table[it] }.joinToString(" ") }
            .filter { it.isNotEmpty() }
            .joinToString(" / ")

    /** ".... .. / - .... . .-. ." -> "HI THERE". Unknown codes become '?'. */
    fun toEnglish(morse: String): String =
        normalize(morse).trim()
            .split(Regex("\\s*/\\s*|\\s{3,}"))      // word gap: "/" or three or more spaces
            .map { word ->
                word.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                    .joinToString("") { code -> (reverse[code] ?: '?').toString() }
            }
            .filter { it.isNotEmpty() }
            .joinToString(" ")

    /**
     * Phone keyboards "smart-punctuate": "--" becomes an en/em dash, "..." an ellipsis, and some
     * offer bullets or middle dots. Bring them all back to plain '.' and '-'.
     */
    fun normalize(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '\u2026' -> append("...")                                            // …
            '\u2022', '\u00B7', '\u2219', '\u22C5', '\u25CF', '\u30FB', '\u2027' -> append('.')  // • · ∙ ⋅ ● ・ ‧
            '\u2013', '\u2014', '\u2212', '\u2010', '\u2011', '\u2012', '\u2015', '_', '\u02D7', '\uFE63', '\uFF0D' -> append('-')
            '\u00A0', '\u2009', '\u202F' -> append(' ')
            else -> append(c)
        }
    }
}

const val MORSE_TRIGGER = "<morsecode>"

enum class MorseDir { MORSE_TO_ENGLISH, ENGLISH_TO_MORSE }

sealed interface MorseState {
    data object Off : MorseState
    data object Menu : MorseState
    data class On(val dir: MorseDir) : MorseState
}

/**
 * The command state machine. Pure: it is told about each line the *user* finished with Enter and
 * answers with the text the machine should type by itself (or null). Auto-typed text is never fed
 * back in, which is what keeps printed lines from re-triggering anything.
 */
class MorseController {
    var state: MorseState = MorseState.Off
        private set

    /**
     * [line] is the visible text of the line just finished: overstruck cells arrive as '■', so a
     * corrected line can never match the trigger by accident. [room] is how many characters still
     * fit under the sheet limit; output that doesn't fit is replaced by [NO_SPACE], never truncated.
     */
    fun onEnter(line: String, room: Int): String? {
        when (val s = state) {
            MorseState.Off -> {
                if (line.trimEnd() != MORSE_TRIGGER) return null
                return if (MENU.length <= room) { state = MorseState.Menu; MENU } else noSpace(room)
            }
            MorseState.Menu -> {
                val dir = when (line.trim()) {
                    "1" -> MorseDir.MORSE_TO_ENGLISH
                    "2" -> MorseDir.ENGLISH_TO_MORSE
                    else -> { state = MorseState.Off; return null }
                }
                val hint = hintFor(dir)
                return if (hint.length <= room) { state = MorseState.On(dir); hint } else { state = MorseState.Off; noSpace(room) }
            }
            is MorseState.On -> {
                if (line.isBlank()) { state = MorseState.Off; return null }
                val result = when (s.dir) {
                    MorseDir.MORSE_TO_ENGLISH -> MorseCodec.toEnglish(line)
                    MorseDir.ENGLISH_TO_MORSE -> MorseCodec.toMorse(line)
                }.ifEmpty { "?" }
                val out = "$PREFIX$result\n"
                return if (out.length <= room) out else noSpace(room)
            }
        }
    }

    fun reset() { state = MorseState.Off }

    private fun noSpace(room: Int): String? = NO_SPACE.takeIf { it.length <= room }

    companion object {
        const val PREFIX = "=> "
        const val MENU = "1. Morse to English\n2. English to Morse\n"
        const val NO_SPACE = "=> (no space)\n"
        fun hintFor(dir: MorseDir): String = when (dir) {
            MorseDir.MORSE_TO_ENGLISH -> "Type . and - ; use / between words.\nEnter = translate. Empty line = exit.\n"
            MorseDir.ENGLISH_TO_MORSE -> "Type English text.\nEnter = translate. Empty line = exit.\n"
        }
    }
}
