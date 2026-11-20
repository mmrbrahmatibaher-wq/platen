package com.mahdi.platen

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mahdi.platen.data.NoteRepository
import com.mahdi.platen.data.PlatenDatabase
import com.mahdi.platen.domain.CreateResult
import com.mahdi.platen.domain.Glyph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class)
class NoteRepositoryRoomTest {
    private lateinit var db: PlatenDatabase
    private lateinit var repo: NoteRepository
    private var now = 100L

    @Before fun setUp() {
        db = PlatenDatabase.inMemory(ApplicationProvider.getApplicationContext())
        repo = NoteRepository(db.notes()) { now }
    }

    @After fun tearDown() = db.close()

    @Test fun createSaveLoadDelete() = runBlocking {
        val id = (repo.create(studio = false) as CreateResult.Created).id
        val glyphs = listOf(Glyph("س"), Glyph("😀"), Glyph("x", struck = true), Glyph("\n"))
        now = 200
        repo.save(id, glyphs)
        val n = repo.get(id)!!
        assertEquals(glyphs, n.glyphs)
        assertEquals(100, n.createdAt)
        assertEquals(200, n.updatedAt)
        repo.delete(id)
        assertNull(repo.get(id))
    }

    @Test fun freeLimitEnforcedInRepository() = runBlocking {
        repeat(3) { assertTrue(repo.create(studio = false) is CreateResult.Created) }
        assertEquals(CreateResult.LimitReached, repo.create(studio = false))
        assertEquals(3, repo.count())
        assertTrue(repo.create(studio = true) is CreateResult.Created)
        assertEquals(4, repo.count())
    }

    @Test fun observeOrdersByLastEdit() = runBlocking {
        val a = (repo.create(false) as CreateResult.Created).id
        now = 150
        val b = (repo.create(false) as CreateResult.Created).id
        now = 300
        repo.save(a, listOf(Glyph("a")))
        assertEquals(listOf(a, b), repo.observeNotes().first().map { it.id })
    }
}
