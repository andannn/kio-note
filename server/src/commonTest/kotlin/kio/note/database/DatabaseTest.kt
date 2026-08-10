package kio.note.database

import kio.async.PollerFactory
import kio.async.runPollEventLoop
import kio.note.util.getEnv
import kio.postgres.conn.PgConnection
import kio.postgres.conn.openPgConnection
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

abstract class DatabaseTest {
    abstract val pollerFactory: PollerFactory

    @Test
    fun getNoteByIdTest() = withTestPgDatabase {
        val note = createNote("new note")
        assertEquals("new note", getNoteById(note.id)?.title)
    }

    @Test
    fun deleteNoteByIdTest() = withTestPgDatabase {
        val note = createNote("new note")
        deleteNoteById(note.id)
        assertEquals(null, getNoteById(note.id))
    }

    @Test
    fun changeNoteTitleTest() = withTestPgDatabase {
        val note = createNote("new note")
        val newNote = changeNoteTitle(note.id, "new title")
        assertEquals("new title", newNote?.title)
        assertEquals("new title", getNoteById(note.id)?.title)
    }

    @Test
    fun updateContentForTextBlockTest() = withTestPgDatabase {
        val note = createNote("new note")
        val block = createBlockAfter(note.id, "text", null)
        val ret = updateContentForTextBlock(note.id, noteBlockId = block.id, "new content")
        assertEquals("new content", ret?.textContent)
    }

    @Test
    fun getAllNoteTest() = withTestPgDatabase {
        val note = createNote("new note")
        assertEquals(1, getAllNote().size)
    }

    @Test
    fun deleteBlock() = withTestPgDatabase {
        val note = createNote("new note")
        assertEquals(0, getNoteBlocksById(note.id).size)
        val block1 = createBlockAfter(note.id, "text", null)
        assertEquals(1, getNoteBlocksById(note.id).size)
        deleteBlockById(block1.id)
        assertEquals(0, getNoteBlocksById(note.id).size)
    }

    @Test
    fun getNoteBlocksByNoteBlockIdTest() = withTestPgDatabase {
        val note = createNote("new note")
        assertEquals(0, getNoteBlocksById(note.id).size)
        val block1 = createBlockAfter(note.id, "text", null)
        assertEquals(block1, getNoteBlocksByNoteBlockId(noteBlockId = block1.id))
    }

    @Test
    fun insertNoteBlockBetweenTest() = withTestPgDatabase {
        val note = createNote("new note")
        val block1 = createBlockAfter(note.id, "text", null)
        assertEquals(1000, block1.sortOrder)
        val block2 = createBlockAfter(note.id, "text", block1.id)
        assertEquals(2000, block2.sortOrder)

        // insert block between 1 and 2
        val block3 = createBlockAfter(note.id, "text", block1.id)
        assertEquals(1500, block3.sortOrder)
    }

    @Test
    fun updateImageUrlTest() = withTestPgDatabase {
        val note = createNote("new note")
        val block1 = createBlockAfter(note.id, "text", null)
        val newBlock = updateImageBlock(block1.id, "new url")
        assertEquals("new url", newBlock?.imageUrl)
    }

    fun withTestPgDatabase(
        block: suspend PgConnection.() -> Unit
    ) =
        runPollEventLoop(pollerFactory) {
            val host = getEnv("POSTGRES_HOST") ?: "127.0.0.1"
            val port = getEnv("POSTGRES_PORT")?.toInt() ?: 5432
            val user = getEnv("POSTGRES_USER")  ?: error("no value found: POSTGRES_USER")
            val password = getEnv("POSTGRES_PASSWORD")  ?: error("no value found: POSTGRES_PASSWORD")
            val database = getEnv("POSTGRES_DB") ?: error("no value found: POSTGRES_DB")

            withTimeout(1.seconds) {
                val conn = openPgConnection(
                    host = host,
                    port = port,
                    user = user,
                    password = password,
                    database = database,
                )
                conn.initDb(isTest = true)
                conn.block()
                conn.close()
            }
        }
}