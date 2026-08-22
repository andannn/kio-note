package kio.note.domain

import kio.async.AsyncRawSource
import kio.http.Logger
import kio.http.info
import kio.http.trace
import kio.note.database.NoteBlockEntity
import kio.note.database.NoteUserEntity
import kio.note.database.NotesEntity
import kio.note.database.changeNoteTitle
import kio.note.database.createBlockAfter
import kio.note.database.createNoteForUser
import kio.note.database.createSession
import kio.note.database.deleteBlockById
import kio.note.database.deleteNoteById
import kio.note.database.getAllNote
import kio.note.database.getNoteBlocksById
import kio.note.database.getNoteBlocksByNoteBlockId
import kio.note.database.getNoteById
import kio.note.database.getUserByUsername
import kio.note.database.getUserIdBySessionId
import kio.note.database.updateContentForTextBlock
import kio.note.database.updateImageBlock
import kio.note.util.Config
import kio.note.util.hashPassword
import kio.note.util.saveFileToPath
import kio.postgres.conn.PgConnectionPool
import kio.postgres.conn.useConnection
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.uuid.Uuid

enum class BlockType {
    H1,
    H2,
    H3,
    H4,
    TEXT,
    IMAGE,
    ;

    companion object {
        fun parse(value: String): BlockType? {
            return BlockType.entries.firstOrNull { it.name == value.uppercase() }
        }
    }
}

data class User(
    val id: Long,
    val username: String,
    val passwordHash: String
)

data class Session(val userId: Long)

data class Note(
    val id: Long,
    val title: String,
    val blocks: MutableList<NoteBlock> = mutableListOf(),
)

sealed interface NoteBlock {
    val blockId: Long

    sealed class Text(
        override val blockId: Long,
        open val text: String,
    ) : NoteBlock {
        data class H1(override val blockId: Long, override val text: String) : Text(blockId, text)
        data class H2(override val blockId: Long, override val text: String) : Text(blockId, text)
        data class H3(override val blockId: Long, override val text: String) : Text(blockId, text)
        data class H4(override val blockId: Long, override val text: String) : Text(blockId, text)
        data class Content(override val blockId: Long, override val text: String) : Text(blockId, text)
    }

    data class Image(
        override val blockId: Long,
        val url: String?,
    ) : NoteBlock
}

fun Repository(pgPool: PgConnectionPool, logger: Logger): Repository = RepositoryImpl(pgPool, logger)

interface Repository {
    // login
    suspend fun findUserByUsername(userName: String): User?
    suspend fun verifyPassword(user: User, password: String): Boolean
    suspend fun createSession(userId: Long): String
    suspend fun getSessionById(sessionId: String): Session?

    // note
    suspend fun createNewNoteForUser(userId: Long): Note
    suspend fun getAllNoteMetaData(userId: Long): List<Note>
    suspend fun getNoteById(id: Long): Note?
    suspend fun changeNoteTitleById(id: Long, title: String): Note?
    suspend fun deleteNoteById(id: Long)
    suspend fun addBlockAfter(noteId: Long, blockId: Long?, type: BlockType): NoteBlock?
    suspend fun deleteBlock(noteId: Long, noteBlockId: Long)
    suspend fun saveImageToImageBlock(noteId: Long, noteBlockId: Long, fileSource: AsyncRawSource): NoteBlock.Image?
    suspend fun saveTextToTextBlock(noteId: Long, noteBlockId: Long, content: String): NoteBlock.Text?
}

private class RepositoryImpl(
    private val pgPool: PgConnectionPool,
    private val logger: Logger
) : Repository {
    override suspend fun findUserByUsername(userName: String): User? {
        return pgPool.useConnection { it.getUserByUsername(userName) }?.toUser()
    }

    override suspend fun verifyPassword(user: User, password: String): Boolean {
        val actualHash = hashPassword(password)

        println("password length=${password.length}")
        println("password bytes=${password.encodeToByteArray().toList()}")
        println("actualHash=$actualHash")
        println("expectedHash=${user.passwordHash}")

        return actualHash == user.passwordHash
    }

    override suspend fun createSession(userId: Long): String {
        logger.info("createSession for user $userId")
        val session = generateSessionId()
        pgPool.useConnection { it.createSession(userId, session) }
        return session
    }

    private fun generateSessionId(): String {
        return Uuid.random().toString()
    }

    override suspend fun getSessionById(sessionId: String): Session? {
        val userId = pgPool.useConnection { it.getUserIdBySessionId(sessionId) } ?: return null
        return Session(userId)
    }

    override suspend fun createNewNoteForUser(userId: Long): Note {
        return pgPool.useConnection { it.createNoteForUser("Untitled", userId) }.toNote()
    }

    override suspend fun getAllNoteMetaData(userId: Long): List<Note> {
        return pgPool.useConnection { it.getAllNote(userId) }.map { it.toNote() }
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
            BlockType.TEXT -> NoteBlockEntity.BLOCK_TYPE_TEXT
            BlockType.IMAGE -> NoteBlockEntity.BLOCK_TYPE_IMAGE
            BlockType.H1 -> NoteBlockEntity.BLOCK_TYPE_H1
            BlockType.H2 -> NoteBlockEntity.BLOCK_TYPE_H2
            BlockType.H3 -> NoteBlockEntity.BLOCK_TYPE_H3
            BlockType.H4 -> NoteBlockEntity.BLOCK_TYPE_H4
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

        logger.trace("Saving image to $filePath")
        fileSource.saveFileToPath(filePath)
        logger.trace("Save image finished to $filePath")

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

private fun NoteUserEntity.toUser(): User = User(id = id, username = username, passwordHash = passwordHash)
private fun NotesEntity.toNote(): Note = Note(id = id, title = title)

private fun NoteBlockEntity.toNoteBlock(): NoteBlock = when (type) {
    NoteBlockEntity.BLOCK_TYPE_H1 -> NoteBlock.Text.H1(blockId = id, textContent ?: "")
    NoteBlockEntity.BLOCK_TYPE_H2 -> NoteBlock.Text.H2(blockId = id, textContent ?: "")
    NoteBlockEntity.BLOCK_TYPE_H3 -> NoteBlock.Text.H3(blockId = id, textContent ?: "")
    NoteBlockEntity.BLOCK_TYPE_H4 -> NoteBlock.Text.H4(blockId = id, textContent ?: "")
    NoteBlockEntity.BLOCK_TYPE_TEXT -> NoteBlock.Text.Content(blockId = id, textContent ?: "")
    NoteBlockEntity.BLOCK_TYPE_IMAGE -> NoteBlock.Image(blockId = id, imageUrl)
    else -> error("not valid block type $type")
}
