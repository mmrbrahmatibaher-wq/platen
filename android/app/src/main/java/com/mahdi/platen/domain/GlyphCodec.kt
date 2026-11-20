package com.mahdi.platen.domain

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

object GlyphCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val serializer = ListSerializer(Glyph.serializer())

    fun encode(glyphs: List<Glyph>): String = json.encodeToString(serializer, glyphs)

    fun decode(raw: String?): List<Glyph> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }
            .getOrDefault(emptyList())
            .filter { it.text.isNotEmpty() }
    }

    fun plainText(glyphs: List<Glyph>, includeStruck: Boolean = false): String =
        buildString {
            glyphs.forEach {
                when {
                    !it.struck -> append(it.text)
                    includeStruck -> append(it.text)
                    it.over != null -> append(it.over) // overstruck cell reads as the letter typed over it
                }
            }
        }
}
