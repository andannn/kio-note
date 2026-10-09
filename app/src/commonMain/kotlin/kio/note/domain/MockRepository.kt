package kio.note.domain

import kio.async.AsyncRawSource
import kio.note.domain.NoteBlock.*
import kio.note.domain.NoteBlock.Text.*
import kio.note.util.Config
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

class MockRepositoryImpl : Repository {
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
                NoteBlock.TaskListItem(
                    blockId = 2,
                    checked = true,
                    "This is the second paragraph."
                )
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
//        return null
        return Session(1)
    }

    override suspend fun createUser(userName: String, password: String): User? {
        return User(1, "aa", "asdf")
    }

    override suspend fun createNewNoteForUser(userId: Long): Note {
        val newNote = Note(nextNoteId++, title = "Untitled")
        notes.add(0, newNote)
        return newNote
    }

    override suspend fun getAllNoteMetaData(userId: Long): List<Note> {
        return notes
    }

    override suspend fun getNoteById(id: Long): Note? {
        return notes.firstOrNull { it.id == id }
    }

    override suspend fun getNoteBlocksFlow(id: Long): Flow<NoteBlock> {
        return flow {
            val notes = notes.firstOrNull { it.id == id }?.blocks?.toList()?: listOf<NoteBlock>()
            notes.forEach { emit(it) }
        }
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
            BlockType.TASK_LIST_ITEM -> NoteBlock.TaskListItem(nextBlockId++, false, "")
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

    override suspend fun updateNoteBlock(
        noteId: Long,
        blockId: Long,
        type: BlockType,
        textContent: String,
        extra: Any?
    ): NoteBlock? {
        val note = getNoteById(noteId) ?: return null

        val blockIndex = note.blocks.indexOfFirst { it.blockId == blockId }
        if (blockIndex == -1) return null

        val newBlock = when (type) {
            BlockType.IMAGE -> {
                Image(
                    blockId = blockId,
                    url = null,
                )
            }

            BlockType.TEXT -> {
                Content(
                    blockId = blockId,
                    text = textContent,
                )
            }

            BlockType.H1 -> {
                H1(
                    blockId = blockId,
                    text = textContent,
                )
            }

            BlockType.H2 -> {
                H2(
                    blockId = blockId,
                    text = textContent,
                )
            }

            BlockType.H3 -> {
                H3(
                    blockId = blockId,
                    text = textContent,
                )
            }

            BlockType.H4 -> {
                H4(
                    blockId = blockId,
                    text = textContent,
                )
            }

            BlockType.TASK_LIST_ITEM -> {
                TaskListItem(
                    blockId = blockId,
                    text = textContent,
                    checked = extra == true
                )
            }
        }

        note.blocks[blockIndex] = newBlock
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

        val newUrl = saveImageAndRemoveOldIfNotNull(fileSource, oldBlock.url)
        val newBlock = oldBlock.copy(url = newUrl)

        note.blocks.removeAt(noteBlockIndex)
        note.blocks.add(noteBlockIndex, newBlock)
        return newBlock
    }

    override suspend fun saveImageAndChangeBlockTypeToImage(
        noteId: Long,
        noteBlockId: Long,
        fileSource: AsyncRawSource
    ): NoteBlock.Image? {
        val note = getNoteById(noteId) ?: return null
        val noteBlockIndex = note.blocks.indexOfFirst { it.blockId == noteBlockId }
        if (noteBlockIndex == -1) return null
        val oldBlock = note.blocks[noteBlockIndex]

        val newUrl = saveImageAndRemoveOldIfNotNull(fileSource, null)
        val newBlock = NoteBlock.Image(blockId = oldBlock.blockId, url = newUrl)

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

    override suspend fun saveCheckedToTaskListItemBlock(
        noteId: Long,
        noteBlockId: Long,
        checked: Boolean
    ): NoteBlock.TaskListItem? {
        val note = getNoteById(noteId) ?: return null
        val noteBlockIndex = note.blocks.indexOfFirst { it.blockId == noteBlockId }
        if (noteBlockIndex == -1) return null
        val oldBlock = note.blocks[noteBlockIndex] as? NoteBlock.TaskListItem ?: return null

        val newBlock = oldBlock.copy(checked = checked)
        note.blocks.removeAt(noteBlockIndex)
        note.blocks.add(noteBlockIndex, newBlock)
        return newBlock
    }

    private fun NoteBlock.Text.copyText(content: String): NoteBlock.Text = when (this) {
        is NoteBlock.Text.Content -> this.copy(text = content)
        is NoteBlock.Text.H1 -> this.copy(text = content)
        is NoteBlock.Text.H2 -> this.copy(text = content)
        is NoteBlock.Text.H3 -> this.copy(text = content)
        is NoteBlock.Text.H4 -> this.copy(text = content)
    }
}
