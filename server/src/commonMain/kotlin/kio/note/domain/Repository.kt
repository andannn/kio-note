package kio.note.domain

import kio.async.AsyncRawSource
import kio.note.database.NoteBlockEntity
import kio.note.database.NotesEntity
import kio.note.database.changeNoteTitle
import kio.note.database.createBlockAfter
import kio.note.database.createNote
import kio.note.database.deleteBlockById
import kio.note.database.deleteNoteById
import kio.note.database.getAllNote
import kio.note.database.getNoteBlocksById
import kio.note.database.getNoteBlocksByNoteBlockId
import kio.note.database.getNoteById
import kio.note.database.updateContentForTextBlock
import kio.note.database.updateImageBlock
import kio.note.util.Config
import kio.note.util.saveFileToPath
import kio.postgres.conn.PgConnectionPool
import kio.postgres.conn.useConnection
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
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
    val blocks: MutableList<NoteBlock> = mutableListOf(),
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
    ) : NoteBlock
}

fun Repository(pgPool: PgConnectionPool): Repository = RepositoryImpl(pgPool)

interface Repository {
    suspend fun createNewNote(): Note
    suspend fun getAllNoteMetaData(): List<Note>
    suspend fun getNoteById(id: Long): Note?
    suspend fun changeNoteTitleById(id: Long, title: String): Note?
    suspend fun deleteNoteById(id: Long)
    suspend fun addBlockAfter(noteId: Long, blockId: Long?, type: BlockType): NoteBlock?
    suspend fun deleteBlock(noteId: Long, noteBlockId: Long)
    suspend fun saveImageToImageBlock(noteId: Long, noteBlockId: Long, fileSource: AsyncRawSource): NoteBlock.Image?
    suspend fun saveTextToTextBlock(noteId: Long, noteBlockId: Long, content: String): NoteBlock.Text?
}

private class RepositoryImpl(
    val pgPool: PgConnectionPool,
) : Repository {
    override suspend fun createNewNote(): Note {
        return pgPool.useConnection { it.createNote("Untitled") }.toNote()
    }

    override suspend fun getAllNoteMetaData(): List<Note> {
        return pgPool.useConnection { it.getAllNote() }.map { it.toNote() }
    }

    override suspend fun getNoteById(id: Long): Note? {
        val noteBlocks = pgPool.useConnection { it.getNoteBlocksById(id) }.map { it.toNoteBlock() }
        val note = pgPool.useConnection { it.getNoteById(id) }?.toNote()
        return note?.copy(blocks = noteBlocks.toMutableList())
    }

    override suspend fun changeNoteTitleById(id: Long, title: String): Note? {
        return pgPool.useConnection { it.changeNoteTitle(id, title) }?.toNote()
    }

    override suspend fun deleteNoteById(id: Long) {
        pgPool.useConnection { it.deleteNoteById(id) }
    }

    override suspend fun addBlockAfter(
        noteId: Long,
        blockId: Long?,
        type: BlockType
    ): NoteBlock {
        val type = when (type) {
            BlockType.TEXT -> "text"
            BlockType.IMAGE -> "image"
        }
        val block = pgPool.useConnection { it.createBlockAfter(noteId, type, blockId) }
        return block.toNoteBlock()
    }

    override suspend fun deleteBlock(noteId: Long, noteBlockId: Long) {
        pgPool.useConnection { it.deleteBlockById(noteBlockId) }
    }

    override suspend fun saveImageToImageBlock(
        noteId: Long,
        noteBlockId: Long,
        fileSource: AsyncRawSource
    ): NoteBlock.Image? {
        val oldBlock = pgPool.useConnection { it.getNoteBlocksByNoteBlockId(noteBlockId) }
        if (oldBlock?.type != "image") return null

        val uuid = Uuid.random().toString()
        val filePath = Path(Config.UPLOAD_DIR, uuid).toString()
        fileSource.saveFileToPath(filePath)

        if (oldBlock.imageUrl != null) {
            val oldPath = Path(Config.UPLOAD_DIR, oldBlock.imageUrl.substringAfterLast("/"))
            SystemFileSystem.delete(oldPath)
        }

        return pgPool.useConnection { it.updateImageBlock(noteBlockId, "/attachments/$uuid") }
            ?.toNoteBlock() as? NoteBlock.Image
    }

    override suspend fun saveTextToTextBlock(
        noteId: Long,
        noteBlockId: Long,
        content: String
    ): NoteBlock.Text? {
        val block = pgPool.useConnection { it.updateContentForTextBlock(noteId, noteBlockId, content) }
            ?.toNoteBlock() as? NoteBlock.Text
        return block
    }
}

private fun NotesEntity.toNote(): Note = Note(id = id, title = title)

private fun NoteBlockEntity.toNoteBlock(): NoteBlock = when (type) {
    "text" -> NoteBlock.Text(blockId = id, textContent ?: "")
    "image" -> NoteBlock.Image(blockId = id, imageUrl)
    else -> error("not valid block type $type")
}

class MockRepositoryImpl : Repository {
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
            BlockType.TEXT -> NoteBlock.Text(nextBlockId++, "")
            BlockType.IMAGE -> NoteBlock.Image(nextBlockId++, url = null)
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
        fileSource.saveFileToPath(filePath)

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

        val newBLock = oldBlock.copy(text = content)
        return newBLock
    }
}
