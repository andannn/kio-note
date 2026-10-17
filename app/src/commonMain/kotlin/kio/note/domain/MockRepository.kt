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
    // Mutable storage is private; callers receive detached domain snapshots.
    private data class MockNote(
        val id: Long,
        var title: String,
        val blocks: MutableList<NoteBlock> = mutableListOf(),
    )

    private data class MockFolder(
        val id: Long,
        var name: String,
        val noteIds: MutableList<Long> = mutableListOf(),
    )

    private val notes = mutableListOf(
        MockNote(1, "Learn HTML", mutableListOf(
            Content(0, "Today I learned HTML components."),
            Content(1, "This is the second paragraph."),
            TaskListItem(2, true, "This is the second paragraph."),
            Code(3, """
                fun main() {
                    println("hello world")
                }
            """.trimIndent()),
        )),
    )
    private val folders = mutableListOf(
        MockFolder(1, "Test Name", mutableListOf(1L)),
    )

    private var nextNoteId = (notes.maxOfOrNull { it.id } ?: 0L) + 1
    private var nextBlockId = (notes.flatMap { it.blocks }.maxOfOrNull { it.blockId } ?: 0L) + 1
    private var nextFolderId = (folders.maxOfOrNull { it.id } ?: 0L) + 1

    private fun findNote(id: Long) = notes.firstOrNull { it.id == id }

    private fun MockNote.snapshot(): Note = Note(
        id = id,
        title = title,
        blocks = blocks.toMutableList(),
    )

    private fun MockFolder.snapshot(): NoteFolder = NoteFolder(
        id = id,
        name = name,
        // Avoid a cyclic Note -> NoteFolder -> Note object graph.
        notes = noteIds.mapNotNull { findNote(it)?.snapshot() },
    )

    // Development authentication stubs; not intended for authentication tests.
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
        return Session(1)
    }

    override suspend fun createUser(userName: String, password: String): User? {
        return User(1, "aa", "asdf")
    }

    override suspend fun createNewNoteForUser(userId: Long): Note {
        val newNote = MockNote(nextNoteId++, title = "Untitled")
        notes.add(0, newNote)
        return newNote.snapshot()
    }

    override suspend fun getAllNoteFolder(userId: Long, excludedFolderIds: List<Long>): List<NoteFolder> {
        return folders.filter { it.id !in excludedFolderIds }.map { it.snapshot() }
    }

    override suspend fun getAllNoteMetaData(userId: Long): List<Note> {
        return notes.map { it.snapshot() }
    }

    override suspend fun getNoteById(id: Long): Note? {
        return findNote(id)?.snapshot()
    }

    override suspend fun getNoteFolderByNoteId(noteId: Long): NoteFolder? {
        return folders.firstOrNull { it.noteIds.any { it == noteId} }?.snapshot()
    }

    override suspend fun getNoteBlocksFlow(id: Long): Flow<NoteBlock> {
        return flow {
            findNote(id)?.blocks?.toList().orEmpty().forEach { emit(it) }
        }
    }

    override suspend fun changeNoteTitleById(id: Long, title: String): Note? {
        val note = findNote(id) ?: return null
        note.title = title
        return note.snapshot()
    }

    override suspend fun changeNoteFolder(
        noteId: Long,
        folderId: Long?
    ): Note? {
        val note = findNote(noteId) ?: return null

        val targetFolder = if (folderId == null) {
            null
        } else {
            folders.firstOrNull { it.id == folderId } ?: return null
        }

        folders.forEach { folder ->
            folder.noteIds.removeAll { it == noteId }
        }
        targetFolder?.noteIds?.add(noteId)

        return note.snapshot()
    }

    override suspend fun deleteNoteById(id: Long) {
        val index = notes.indexOfFirst { it.id == id }
        if (index == -1) return

        notes.removeAt(index)
        folders.forEach { it.noteIds.removeAll { noteId -> noteId == id } }
    }

    override suspend fun addBlockAfter(
        noteId: Long,
        blockId: Long?,
        type: BlockType
    ): NoteBlock? {
        val note = findNote(noteId) ?: return null
        val index = if (blockId == null) 0 else {
            val previous = note.blocks.indexOfFirst { it.blockId == blockId }
            if (previous == -1) return null
            previous + 1
        }
        val newBlock = createBlock(nextBlockId++, type, "", null)
        note.blocks.add(index, newBlock)
        return newBlock
    }

    override suspend fun updateNoteBlock(
        noteId: Long,
        blockId: Long,
        type: BlockType,
        textContent: String,
        extra: Any?
    ): NoteBlock? {
        val note = findNote(noteId) ?: return null

        val blockIndex = note.blocks.indexOfFirst { it.blockId == blockId }
        if (blockIndex == -1) return null

        val newBlock = createBlock(blockId, type, textContent, extra)

        note.blocks[blockIndex] = newBlock
        return newBlock
    }

    override suspend fun deleteBlock(noteId: Long, noteBlockId: Long) {
        val note = findNote(noteId) ?: return

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
        val note = findNote(noteId) ?: return null
        val noteBlockIndex = note.blocks.indexOfFirst { it.blockId == noteBlockId }
        if (noteBlockIndex == -1) return null
        val oldBlock = note.blocks[noteBlockIndex] as? NoteBlock.Image ?: return null

        val newUrl = saveImageAndRemoveOldIfNotNull(fileSource, oldBlock.url)
        val newBlock = oldBlock.copy(url = newUrl)

        note.blocks[noteBlockIndex] = newBlock
        return newBlock
    }

    override suspend fun saveImageAndChangeBlockTypeToImage(
        noteId: Long,
        noteBlockId: Long,
        fileSource: AsyncRawSource
    ): NoteBlock.Image? {
        val note = findNote(noteId) ?: return null
        val noteBlockIndex = note.blocks.indexOfFirst { it.blockId == noteBlockId }
        if (noteBlockIndex == -1) return null
        val oldBlock = note.blocks[noteBlockIndex]

        val newUrl = saveImageAndRemoveOldIfNotNull(fileSource, (oldBlock as? Image)?.url)
        val newBlock = NoteBlock.Image(blockId = oldBlock.blockId, url = newUrl)

        note.blocks[noteBlockIndex] = newBlock
        return newBlock
    }

    override suspend fun saveTextToTextBlock(
        noteId: Long,
        noteBlockId: Long,
        content: String
    ): NoteBlock.Text? {
        val note = findNote(noteId) ?: return null
        val noteBlockIndex = note.blocks.indexOfFirst { it.blockId == noteBlockId }
        if (noteBlockIndex == -1) return null
        val oldBlock = note.blocks[noteBlockIndex] as? NoteBlock.Text ?: return null

        val newBlock = oldBlock.copyText(content)
        note.blocks[noteBlockIndex] = newBlock
        return newBlock
    }

    override suspend fun saveCheckedToTaskListItemBlock(
        noteId: Long,
        noteBlockId: Long,
        checked: Boolean
    ): NoteBlock.TaskListItem? {
        val note = findNote(noteId) ?: return null
        val noteBlockIndex = note.blocks.indexOfFirst { it.blockId == noteBlockId }
        if (noteBlockIndex == -1) return null
        val oldBlock = note.blocks[noteBlockIndex] as? NoteBlock.TaskListItem ?: return null

        val newBlock = oldBlock.copy(checked = checked)
        note.blocks[noteBlockIndex] = newBlock
        return newBlock
    }

    override suspend fun getNoteFolderById(folderId: Long): NoteFolder? {
        return folders.firstOrNull { it.id == folderId }?.snapshot()
    }

    override suspend fun newFolder(userId: Long, folderName: String): NoteFolder {
        val folder = MockFolder(nextFolderId++, folderName)
        folders.add(0, folder)
        return folder.snapshot()
    }

    override suspend fun changeFolderName(
        folderId: Long,
        name: String
    ): NoteFolder? {
        val folder = folders.first { it.id == folderId }
        folder.name = name
        return folder.snapshot()
    }

    override suspend fun deleteNoteFolder(folderId: Long): NoteFolder? {
        val index = folders.indexOfFirst { it.id == folderId }
        if (index == -1) {
            return null
        } else {
            return folders.removeAt(index).snapshot()
        }
    }

    private fun createBlock(id: Long, type: BlockType, text: String, extra: Any?): NoteBlock = when (type) {
        BlockType.TEXT -> Content(id, text)
        BlockType.H1 -> H1(id, text)
        BlockType.H2 -> H2(id, text)
        BlockType.H3 -> H3(id, text)
        BlockType.H4 -> H4(id, text)
        BlockType.IMAGE -> Image(id, null)
        BlockType.TASK_LIST_ITEM -> TaskListItem(id, extra == true, text)
        BlockType.CODE -> Code(id, text)
    }

    private fun NoteBlock.Text.copyText(content: String): NoteBlock.Text = when (this) {
        is NoteBlock.Text.Content -> this.copy(text = content)
        is NoteBlock.Text.H1 -> this.copy(text = content)
        is NoteBlock.Text.H2 -> this.copy(text = content)
        is NoteBlock.Text.H3 -> this.copy(text = content)
        is NoteBlock.Text.H4 -> this.copy(text = content)
    }
}
