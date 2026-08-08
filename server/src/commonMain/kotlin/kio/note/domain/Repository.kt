package kio.note.domain

import kio.async.AsyncRawSource
import kio.note.util.Config
import kio.note.util.saveFileToPath
import kotlin.io.path.Path
import kotlin.uuid.Uuid

enum class BlockType {
    TEXT,
    IMAGE,
    ;
    companion object {
        fun parse(value: String): BlockType? {
            return BlockType.entries.firstOrNull { it.name == value.uppercase() }
        }
    }
}

data class Note(
    val id: Long,
    val title: String,
    val blocks: MutableList<NoteBlock>,
)

sealed interface NoteBlock {
    val blockId: Long
    data class Text(
        override val blockId: Long,
        val text: String,
    ) : NoteBlock

    data class Image(
        override val blockId: Long,
        val url: String?,
        val alt: String = "",
    ) : NoteBlock
}

fun Repository() : Repository = MockRepositoryImpl()

interface Repository {
    suspend fun getAllNoteMetaData(): List<Note>
    suspend fun getNoteById(id: Long): Note?
    suspend fun addBlockAfter(noteId: Long, blockId: Long, type: BlockType): NoteBlock?
    suspend fun deleteBlock(noteId: Long, noteBlockId: Long)
    suspend fun saveImageToImageBlock(noteId: Long, noteBlockId: Long, fileSource: AsyncRawSource): NoteBlock.Image?
}

private class MockRepositoryImpl: Repository {
    private val notes = mutableListOf(
        Note(
            id = 1,
            title = "Learn HTML",
            blocks = mutableListOf(
                NoteBlock.Text(
                    blockId = 0,
                    "Today I learned HTML components."
                ),
                NoteBlock.Text(
                    blockId = 1,
                    "This is the second paragraph."
                ),
            ),
        )
    )

    private var nextNoteId = 3L
    private var nextBlockId = 2L

    override suspend fun getAllNoteMetaData(): List<Note> {
        return notes
    }

    override suspend fun getNoteById(id: Long): Note? {
        return notes.firstOrNull { it.id == id }
    }

    override suspend fun addBlockAfter(
        noteId: Long,
        blockId: Long,
        type: BlockType
    ): NoteBlock? {
        val note = getNoteById(noteId) ?: return null

        val blockIndex = note.blocks.indexOfFirst { it.blockId == blockId }
        if (blockIndex == -1) return null
        val newTextBlock = when (type) {
            BlockType.TEXT -> NoteBlock.Text(nextBlockId++, "")
            BlockType.IMAGE -> NoteBlock.Image(nextBlockId++, url = null)
        }

        note.blocks.add(blockIndex + 1, newTextBlock)
        return newTextBlock
    }

    override suspend fun deleteBlock(noteId: Long, noteBlockId: Long) {
        val note = getNoteById(noteId) ?: return

        val blockIndex = note.blocks.indexOfFirst { it.blockId == noteBlockId }
        if (blockIndex == -1) return

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
        fileSource.saveFileToPath(filePath)

        return oldBlock.copy(url = "/attachments/$uuid")
    }
}