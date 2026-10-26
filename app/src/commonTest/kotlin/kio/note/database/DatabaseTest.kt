package kio.note.database

import kio.async.PollerFactory
import kio.async.io.getEnv
import kio.async.runPollEventLoop
import kio.note.db.dropAllTables
import kio.note.db.migrations
import kio.postgres.conn.PgConnection
import kio.postgres.conn.openPgConnection
import kio.postgres.migration.MigrationResult
import kio.postgres.migration.migrate
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

abstract class DatabaseTest {
    abstract val pollerFactory: PollerFactory

    @Test
    fun getNoteByIdTest() = withTestPgDatabase {
        val note = createNoteForUser("new note", 1)
        assertEquals("new note", getNoteByIdAndBlocks(1, note.id).firstOrNull()?.title)
    }

    @Test
    fun deleteNoteByIdTest() = withTestPgDatabase {
        val note = createNoteForUser("new note", 1)
        deleteNoteById(note.id)
        assertTrue(getNoteByIdAndBlocks(1, note.id).isEmpty())
    }

    @Test
    fun changeNoteTitleTest() = withTestPgDatabase {
        val note = createNoteForUser("new note", 1)
        val newNote = changeNoteTitle(1, note.id, "new title")
        assertEquals("new title", newNote?.title)
        assertEquals("new title", getNoteByIdAndBlocks(1, note.id).firstOrNull()?.title)
    }

    @Test
    fun updateContentForTextBlockTest() = withTestPgDatabase {
        val note = createNoteForUser("new note", 1)
        val block = createBlockAfter(1, note.id, "text", null)!!
        val ret = updateContentForTextBlock(1, note.id, noteBlockId = block.id, "new content")
        assertEquals("new content", ret?.textContent)
    }

    @Test
    fun getAllNoteTest() = withTestPgDatabase {
        val note = createNoteForUser("new note", 1)
        assertEquals(1, getAllNote(userId = 1).size)
    }

    @Test
    fun deleteBlock() = withTestPgDatabase {
        val note = createNoteForUser("new note", 1)
        assertEquals(0, getNoteBlocksById(1, note.id).size)
        val block1 = createBlockAfter(1, note.id, "text", null)!!
        assertEquals(1, getNoteBlocksById(1, note.id).size)
        assertNull(deleteBlockById(1, note.id, block1.id))
        assertEquals(0, getNoteBlocksById(1, note.id).size)
    }

    @Test
    fun getNoteBlocksByNoteBlockIdTest() = withTestPgDatabase {
        val note = createNoteForUser("new note", 1)
        assertEquals(0, getNoteBlocksById(1, note.id).size)
        val block1 =  createBlockAfter(1, note.id, "text", null)!!
        assertEquals(block1, getNoteBlocksByNoteBlockId(1, noteBlockId = block1.id))
    }

    @Test
    fun insertNoteBlockAtBeginningTest() = withTestPgDatabase {
        val note = createNoteForUser("new note", 1)
        val first =  createBlockAfter(1, note.id, "text", null)!!
        val second =  createBlockAfter(1, note.id, "text", null)!!
        val third =  createBlockAfter(1, note.id, "text", null)!!

        assertEquals(1000L, first.sortOrder)
        assertEquals(0L, second.sortOrder)
        assertEquals(-1000L, third.sortOrder)
        assertEquals(
            listOf(third.id, second.id, first.id),
            getNoteBlocksById(1, note.id).map { it.id }
        )
    }

    @Test
    fun insertNoteBlockBetweenTest() = withTestPgDatabase {
        val note = createNoteForUser("new note", 1)
        val block1 =  createBlockAfter(1, note.id, "text", null)!!
        assertEquals(1000, block1.sortOrder)
        val block2 =  createBlockAfter(1, note.id, "text", block1.id)!!
        assertEquals(2000, block2.sortOrder)

        // insert block between 1 and 2
        val block3 =  createBlockAfter(1, note.id, "text", block1.id)!!
        assertEquals(1500, block3.sortOrder)
    }

    @Test
    fun insertNoteBlockReassignsExhaustedSortOrdersTest() = withTestPgDatabase {
        val note = createNoteForUser("new note", 1)
        val first =  createBlockAfter(1, note.id, "text", null)!!
        val last =  createBlockAfter(1, note.id, "text", first.id)!!
        val expectedIds = mutableListOf(first.id, last.id)
        val otherNote = createNoteForUser("other note", 1)
        val otherBlock =  createBlockAfter(1, otherNote.id, "text", null)!!

        repeat(20) {
            val inserted =  createBlockAfter(1, note.id, "text", first.id)!!
            expectedIds.add(1, inserted.id)
            val blocks = getNoteBlocksById(1, note.id)
            assertEquals(expectedIds, blocks.map { it.id })
            assertEquals(blocks.size, blocks.map { it.sortOrder }.distinct().size)
        }
        assertEquals(listOf(otherBlock), getNoteBlocksById(1, otherNote.id))
    }

    @Test
    fun updateImageUrlTest() = withTestPgDatabase {
        val note = createNoteForUser("new note", 1)
        val block1 =  createBlockAfter(1, note.id, "text", null)!!
        val newBlock = updateImageBlock(1, note.id, block1.id, "new url")
        assertEquals("new url", newBlock?.imageUrl)
    }

    @Test
    fun updateImageUrlAndChangeImageTypeTest() = withTestPgDatabase {
        val note = createNoteForUser("new note", 1)
        val block1 =  createBlockAfter(1, note.id, "text", null)!!
        val newBlock = saveImageToBlockAndChangeTypeToImage(1, note.id, block1.id, "new url")
        assertEquals("new url", newBlock?.imageUrl)
        assertEquals("image", newBlock?.type)
        assertEquals("new url", deleteBlockById(1, note.id, newBlock!!.id))
    }

    @Test
    fun getUserByUsernameTest() = withTestPgDatabase {
        val user = createUser("userA", "pass")!!
        assertEquals("userA", user.username)
        assertEquals("pass", user.passwordHash)
        assertEquals(user, getUserByUsername(user.username))
    }

    @Test
    fun createUserDuplicatedTest() = withTestPgDatabase {
        val user1 = createUser("userA", "pass")!!
        val user2 = createUser("userA", "pass1")
        assertNull(user2)
    }

    @Test
    fun sessionTest() = withTestPgDatabase {
        val user = createUser(userName = "1", passwordHash = "pas")!!
        createSession(user.id, "12345")
        assertEquals(user.id, getUserIdBySessionId("12345"))
    }

    @Test
    fun changeTextBlockTypeAndContentTest() = withTestPgDatabase {
        val note = createNoteForUser("new note", 1)
        val block1 =  createBlockAfter(1, note.id, "text", null)!!
        val newBlock = updateNoteBlock(1, note.id, block1.id, "h1", "new c")
        assertEquals("h1", newBlock!!.type)
        assertEquals("new c", newBlock.textContent)
    }

    @Test
    fun updateTimeUpdatedWhenInsertBlock() = withTestPgDatabase {
        val note = createNoteForUser("new note", 1)
        val block1 =  createBlockAfter(1, note.id, "text", null)!!
        val updateTime1 = getNoteByIdAndBlocks(1, note.id).first().updateAt

        createBlockAfter(1, note.id, "text", block1.id)!!
        val updateTime2 = getNoteByIdAndBlocks(1, note.id).first().updateAt

        assertTrue { updateTime2 > updateTime1 }
    }

    @Test
    fun saveCheckedToBlockTest() = withTestPgDatabase {
        val note = createNoteForUser("new note", 1)
        val block1 =  createBlockAfter(1, note.id, "text", null)!!
        val retBlock = saveCheckedToBlock(1, note.id, block1.id, true)
        assertTrue(retBlock!!.checked)
        assertTrue(getNoteBlocksByNoteBlockId(1, block1.id)!!.checked)
    }

    @Test
    fun createNoteFolder() = withTestPgDatabase {
        val folder = createNewNoteFolder(1, "Test")
        assertEquals("Test", folder.name)
    }

    @Test
    fun getAllNoteFolderTest() = withTestPgDatabase {
        val folder1 = createNewNoteFolder(1, "Test1")
        val folder2 = createNewNoteFolder(1, "Test2")
        getAllNoteFolder(1, listOf()).also {
            assertEquals(2, it.size)
            assertTrue(it.any { it.folderId == folder1.id })
            assertTrue(it.any { it.folderId == folder2.id })
        }
        getAllNoteFolder(1, listOf(folder1.id)).also {
            assertEquals(1, it.size)
            assertTrue(it.any { it.folderId == folder2.id })
        }
    }

    @Test
    fun deleteNoteFolderTest() = withTestPgDatabase {
        val folder = createNewNoteFolder(1, "Test")
        val deleted = deleteNoteFolder(1, folder.id)
        assertEquals(folder, deleted)
    }

    @Test
    fun getNoteFolderByNoteIdTest() = withTestPgDatabase {
        val folder1 = createNewNoteFolder(1, "Test 1")
        val note = createNoteForUser("new note", 1)
        changeNoteFolder(1, note.id, folder1.id)
        getNoteFolderByNoteId(1, note.id).also { folder ->
            assertEquals(1, folder.size)
            assertEquals(note.id, folder.first().noteId)
        }
    }

    @Test
    fun changeNoteFolderTest() = withTestPgDatabase {
        val folder1 = createNewNoteFolder(1, "Test 1")
        val note = createNoteForUser("new note", 1)
        changeNoteFolder(1, note.id, folder1.id).also { new ->
            assertEquals(folder1.id, new?.folderId)
        }
        changeNoteFolder(1, note.id, null).also { new ->
            assertEquals(null, note.folderId)
        }
    }

    @Test
    fun getNoteFolderByIdTest() = withTestPgDatabase {
        val folder = createNewNoteFolder(1, "Test")
        getNoteFolderById(1, folderId = folder.id).also { folder ->
            assertEquals(1, folder.size)
        }

        val note = createNoteForUser("new note", 1)
        changeNoteFolder(1, note.id, folderId = folder.id)

        getNoteFolderById(1, folderId = folder.id).also { folder ->
            assertEquals(1, folder.size)
            assertEquals(note.id, folder.first().noteId)
            assertEquals(note.title, folder.first().noteTitle)
        }
    }

    @Test
    fun changeFolderNameTest() = withTestPgDatabase {
        val folder = createNewNoteFolder(1, "Test")
        val new = changeNoteFolderName(1, folderId = folder.id, "New Name")
        assertEquals("New Name", new.first().folderName)
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
                conn.dropAllTables()
                val result = conn.migrate(migrations)
                if (result is MigrationResult.Error) {
                    println(result)
                }
                assertIs<MigrationResult.Success>(result)

                try {
                    conn.block()
                } finally {
                    conn.dropAllTables()
                }

                conn.close()
            }
        }
}