package com.mahdi.platen.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val glyphsJson: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC, id DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): NoteEntity?

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun count(): Int

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Query("UPDATE notes SET glyphsJson = :json, updatedAt = :updatedAt WHERE id = :id")
    suspend fun update(id: Long, json: String, updatedAt: Long)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [NoteEntity::class], version = 1, exportSchema = true)
abstract class PlatenDatabase : RoomDatabase() {
    abstract fun notes(): NoteDao

    companion object {
        fun create(context: Context): PlatenDatabase =
            Room.databaseBuilder(context, PlatenDatabase::class.java, "platen.db").build()

        fun inMemory(context: Context): PlatenDatabase =
            Room.inMemoryDatabaseBuilder(context, PlatenDatabase::class.java).allowMainThreadQueries().build()
    }
}
