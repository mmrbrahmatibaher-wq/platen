package com.mahdi.platen.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One typed cell on the paper. [text] is a whole grapheme cluster (a letter with its marks,
 * a full emoji sequence, or "\n"), never half of a surrogate pair.
 *
 * [struck] marks an overstruck cell: a different key was typed on top of [text] (v3), shown as a
 * dense ink block. Notes saved by v2 used the same flag for strike-through corrections; they load
 * unchanged and are now drawn as overstrikes. [over] is the key typed on top (null for v2 notes).
 */
@Serializable
data class Glyph(
    @SerialName("c") val text: String,
    @SerialName("s") val struck: Boolean = false,
    @SerialName("o") val over: String? = null,
) {
    val isNewline: Boolean get() = text == "\n"
    val isSpace: Boolean get() = text == " "
}

/** STRIKE = typewriter behaviour (backspace steps back, retyping overstrikes); DELETE = erase cleanly. */
enum class CorrectionMode { STRIKE, DELETE }

/** Mechanical events produced by the engine, used for sound and haptics. */
enum class TypeEvent { KEY, SPACE, RETURN, BELL, STRIKE, BACKSPACE, ERASE, LIMIT }
