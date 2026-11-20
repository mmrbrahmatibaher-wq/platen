package com.mahdi.platen.data

import com.mahdi.platen.domain.CreateResult
import com.mahdi.platen.domain.Glyph
import com.mahdi.platen.domain.GlyphCodec
import com.mahdi.platen.domain.NoteLimits
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class Note(
    val id: Long,
    val glyphs: List<Glyph>,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val activeText: String get() = GlyphCodec.plainText(glyphs)
    val activeCount: Int get() = glyphs.size
}

class NoteRepository(
    private val dao: NoteDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val createLock = Mutex()

    fun observeNotes(): Flow<List<Note>> = dao.observeAll().map { list -> list.map { it.toNote() } }

    suspend fun get(id: Long): Note? = dao.get(id)?.toNote()

    suspend fun count(): Int = dao.count()

    /** Creates a blank note if the active-note rule allows it. */
    suspend fun create(studio: Boolean, initial: List<Glyph> = emptyList()): CreateResult = createLock.withLock {
        if (!NoteLimits.canCreate(dao.count(), studio)) return CreateResult.LimitReached
        val now = clock()
        CreateResult.Created(dao.insert(NoteEntity(glyphsJson = GlyphCodec.encode(initial), createdAt = now, updatedAt = now)))
    }

    suspend fun save(id: Long, glyphs: List<Glyph>) = dao.update(id, GlyphCodec.encode(glyphs), clock())

    suspend fun delete(id: Long) = dao.delete(id)

    private fun NoteEntity.toNote() = Note(id, GlyphCodec.decode(glyphsJson), createdAt, updatedAt)
}
