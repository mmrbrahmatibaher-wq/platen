package com.mahdi.platen

import com.mahdi.platen.data.NoteDao
import com.mahdi.platen.data.NoteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeNoteDao : NoteDao {
    val rows = MutableStateFlow<Map<Long, NoteEntity>>(emptyMap())
    private var next = 1L
    var updates = 0

    override fun observeAll(): Flow<List<NoteEntity>> =
        rows.map { m -> m.values.sortedWith(compareByDescending<NoteEntity> { it.updatedAt }.thenByDescending { it.id }) }

    override suspend fun get(id: Long) = rows.value[id]
    override suspend fun count() = rows.value.size
    override suspend fun insert(note: NoteEntity): Long {
        val id = next++
        rows.value = rows.value + (id to note.copy(id = id))
        return id
    }
    override suspend fun update(id: Long, json: String, updatedAt: Long) {
        updates++
        rows.value[id]?.let { rows.value = rows.value + (id to it.copy(glyphsJson = json, updatedAt = updatedAt)) }
    }
    override suspend fun delete(id: Long) { rows.value = rows.value - id }
}
