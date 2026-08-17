package kio.note.domain

import kio.async.AsyncRawSource
import kio.http.Logger
import kio.http.trace
import kio.note.util.Config
import kio.note.util.saveFileToPath
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.uuid.Uuid

class MockRepositoryImpl(private val logger: Logger) : Repository {
    private val notes = mutableListOf(
        Note(
            id = 1,
            title = "Learn HTML",
            blocks = mutableListOf(
                NoteBlock.Text.Content(
                    blockId = 0,
                    "Today I learned HTML components."
                ),
                NoteBlock.Text.Content(
                    blockId = 1,
                    "This is the second paragraph."
                ),
            ),
        )
    )

    private var nextNoteId = 3L
    private var nextBlockId = 2L

    override suspend fun findUserByUsername(userName: String): User? {
        return User(1, "Test", "has")
    }

    override suspend fun verifyPassword(user: User, password: String): Boolean {
        return true
    }

    override suspend fun createSession(userId: Long): String {
        return "asdf"
    }

    override suspend fun getSessionById(sessionId: String): Session? {
        return null
//        return Session(1)
    }

    override suspend fun createNewNote(): Note {
        val newNote = Note(nextNoteId++, title = "Untitled")
        notes.add(0, newNote)
        return newNote
    }

    override suspend fun getAllNoteMetaData(): List<Note> {
        return notes
    }

    override suspend fun getNoteById(id: Long): Note? {
        return notes.firstOrNull { it.id == id }
    }

    override suspend fun changeNoteTitleById(id: Long, title: String): Note? {
        val note = getNoteById(id) ?: return null
        return note.copy(title = title)
    }

    override suspend fun deleteNoteById(id: Long) {
        val index = notes.indexOfFirst { it.id == id }
        if (index == -1) return

        notes.removeAt(index)
    }

    override suspend fun addBlockAfter(
        noteId: Long,
        blockId: Long?,
        type: BlockType
    ): NoteBlock? {
        val newBlock = when (type) {
            BlockType.IMAGE -> NoteBlock.Image(nextBlockId++, url = null)
            BlockType.TEXT -> NoteBlock.Text.Content(nextBlockId++, "")
            BlockType.H1 -> NoteBlock.Text.H1(nextBlockId++, "")
            BlockType.H2 -> NoteBlock.Text.H2(nextBlockId++, "")
            BlockType.H3 -> NoteBlock.Text.H3(nextBlockId++, "")
            BlockType.H4 -> NoteBlock.Text.H4(nextBlockId++, "")
        }

        val note = getNoteById(noteId) ?: return null

        if (blockId == null) {
            note.blocks.add(0, newBlock)
            return newBlock
        }

        val blockIndex = note.blocks.indexOfFirst { it.blockId == blockId }

        if (blockIndex == -1) return null

        note.blocks.add(blockIndex + 1, newBlock)
        return newBlock
    }

    override suspend fun deleteBlock(noteId: Long, noteBlockId: Long) {
        val note = getNoteById(noteId) ?: return

        val blockIndex = note.blocks.indexOfFirst { it.blockId == noteBlockId }
        if (blockIndex == -1) return

        val block = note.blocks[blockIndex]
        if (block is NoteBlock.Image && block.url != null) {
            val oldPath = Path(Config.UPLOAD_DIR, block.url.substringAfterLast("/"))
            SystemFileSystem.delete(oldPath)
        }

        note.blocks.removeAt(blockIndex)
    }

    override suspend fun saveImageToImageBlock(
        noteId: Long,
        noteBlockId: Long,
        fileSource: AsyncRawSource
    ): NoteBlock.Image? {
        val note = getNoteById(noteId) ?: return null
        val noteBlockIndex = note.blocks.indexOfFirst { it.blockId == noteBlockId }
        if (noteBlockIndex == -1) return null
        val oldBlock = note.blocks[noteBlockIndex] as? NoteBlock.Image ?: return null

        val uuid = Uuid.random().toString()
        val filePath = Path(Config.UPLOAD_DIR, uuid).toString()

        logger.trace("Saving image to $filePath")
        fileSource.saveFileToPath(filePath)
        logger.trace("Save image finished to $filePath")

        if (oldBlock.url != null) {
            val oldPath = Path(Config.UPLOAD_DIR, oldBlock.url.substringAfterLast("/"))
            SystemFileSystem.delete(oldPath)
        }

        val newBlock = oldBlock.copy(url = "/attachments/$uuid")
        note.blocks.removeAt(noteBlockIndex)
        note.blocks.add(noteBlockIndex, newBlock)
        return newBlock
    }

    override suspend fun saveTextToTextBlock(
        noteId: Long,
        noteBlockId: Long,
        content: String
    ): NoteBlock.Text? {
        val note = getNoteById(noteId) ?: return null
        val noteBlockIndex = note.blocks.indexOfFirst { it.blockId == noteBlockId }
        if (noteBlockIndex == -1) return null
        val oldBlock = note.blocks[noteBlockIndex] as? NoteBlock.Text ?: return null

        val newBLock = oldBlock.copyText(content = content)
        return newBLock
    }

    private fun NoteBlock.Text.copyText(content: String): NoteBlock.Text = when (this) {
        is NoteBlock.Text.Content -> this.copy(text = content)
        is NoteBlock.Text.H1 -> this.copy(text = content)
        is NoteBlock.Text.H2 -> this.copy(text = content)
        is NoteBlock.Text.H3 -> this.copy(text = content)
        is NoteBlock.Text.H4 -> this.copy(text = content)
    }
}
