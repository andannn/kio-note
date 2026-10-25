package kio.note.domain

import kio.async.AsyncRawSource
import kio.http.currentLogger
import kio.http.info
import kio.http.trace
import kio.note.database.NoteBlockEntity
import kio.note.database.NoteFolderEntity
import kio.note.database.NoteUserEntity
import kio.note.database.NoteWithFolderEntity
import kio.note.database.NotesEntity
import kio.note.database.changeNoteFolder
import kio.note.database.changeNoteFolderName
import kio.note.database.changeNoteTitle
import kio.note.database.createBlockAfter
import kio.note.database.createNewNoteFolder
import kio.note.database.createNoteForUser
import kio.note.database.createSession
import kio.note.database.createUser
import kio.note.database.deleteBlockById
import kio.note.database.deleteNoteById
import kio.note.database.deleteNoteFolder
import kio.note.database.getAllNote
import kio.note.database.getAllNoteFolder
import kio.note.database.getNoteBlocksById
import kio.note.database.getNoteBlocksByNoteBlockId
import kio.note.database.getNoteBlocksFlow
import kio.note.database.getNoteById
import kio.note.database.getNoteFolderById
import kio.note.database.getNoteFolderByNoteId
import kio.note.database.getUserByUsername
import kio.note.database.getUserIdBySessionId
import kio.note.database.saveCheckedToBlock
import kio.note.database.saveImageToBlockAndChangeTypeToImage
import kio.note.database.updateContentForTextBlock
import kio.note.database.updateImageBlock
import kio.note.database.updateNoteBlock
import kio.note.util.Config
import kio.note.util.hashPassword
import kio.note.util.removeFile
import kio.note.util.saveFileToPath
import kio.postgres.conn.PgConnectionPool
import kio.postgres.conn.useConnection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.io.files.Path
import kotlin.uuid.Uuid

enum class BlockType {
    H1,
    H2,
    H3,
    H4,
    TEXT,
    IMAGE,
    TASK_LIST_ITEM,
    CODE,
    ;

    fun isTextBlock(): Boolean {
        return this == H1 ||
                this == H2 ||
                this == H3 ||
                this == H4 ||
                this == TEXT ||
                this == CODE ||
                this == TASK_LIST_ITEM
    }

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

data class NoteFolder(
    val id: Long,
    val name: String,
    val notes: List<Note>,
)

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

    data class TaskListItem(
        override val blockId: Long,
        val checked: Boolean,
        val text: String,
    ) : NoteBlock

    data class Code(
        override val blockId: Long,
        val text: String,
    ): NoteBlock
}

fun Repository(pgPool: PgConnectionPool): Repository = RepositoryImpl(pgPool)

interface Repository {
    // login
    suspend fun findUserByUsername(userName: String): User?
    suspend fun verifyPassword(user: User, password: String): Boolean
    suspend fun createSession(userId: Long): String
    suspend fun getSessionById(sessionId: String): Session?
    suspend fun createUser(userName: String, password: String): User?

    // note
    suspend fun createNewNoteForUser(userId: Long): Note
    suspend fun getAllNoteFolder(userId: Long, excludedFolderIds: List<Long> = emptyList()): List<NoteFolder>
    suspend fun getAllNoteMetaData(userId: Long): List<Note>
    suspend fun getNoteById(userId: Long, id: Long): Note?
    suspend fun getNoteFolderByNoteId(userId: Long, noteId: Long): NoteFolder?
    suspend fun getNoteBlocksFlow(userId: Long, id: Long): Flow<NoteBlock>
    suspend fun changeNoteTitleById(userId: Long, id: Long, title: String): Note?
    suspend fun deleteNoteById(userId: Long, id: Long)
    suspend fun addBlockAfter(userId: Long, noteId: Long, blockId: Long?, type: BlockType): NoteBlock?
    suspend fun updateNoteBlock(userId: Long, noteId: Long, blockId: Long, type: BlockType, textContent: String, extra: Any?): NoteBlock?
    suspend fun deleteBlock(userId: Long, noteId: Long, noteBlockId: Long)
    suspend fun saveImageToImageBlock(userId: Long, noteId: Long, noteBlockId: Long, fileSource: AsyncRawSource): NoteBlock.Image?
    suspend fun saveImageAndChangeBlockTypeToImage(userId: Long, noteId: Long, noteBlockId: Long, fileSource: AsyncRawSource): NoteBlock.Image?
    suspend fun saveTextToTextBlock(userId: Long, noteId: Long, noteBlockId: Long, content: String): NoteBlock.Text?
    suspend fun saveCheckedToTaskListItemBlock(userId: Long, noteId: Long, noteBlockId: Long, checked: Boolean): NoteBlock.TaskListItem?

    // folder
    suspend fun getNoteFolderById(userId: Long, folderId: Long): NoteFolder?
    suspend fun newFolder(userId: Long, folderName: String): NoteFolder
    suspend fun changeFolderName(userId: Long, folderId: Long, name: String): NoteFolder?
    suspend fun deleteNoteFolder(userId: Long, folderId: Long): NoteFolder?
    suspend fun changeNoteFolder(userId: Long, noteId: Long, folderId: Long?): Note?
}

private class RepositoryImpl(
    private val pgPool: PgConnectionPool,
) : Repository {
    override suspend fun findUserByUsername(userName: String): User? {
        return pgPool.useConnection { it.getUserByUsername(userName) }?.toUser()
    }

    override suspend fun verifyPassword(user: User, password: String): Boolean {
        val actualHash = hashPassword(password)

        return actualHash == user.passwordHash
    }

    override suspend fun createSession(userId: Long): String {
        currentLogger().info("createSession for user $userId")
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

    override suspend fun createUser(userName: String, password: String): User? {
        val actualHash = hashPassword(password)
        return pgPool.useConnection { it.createUser(userName, actualHash) }?.toUser()
    }

    override suspend fun createNewNoteForUser(userId: Long): Note {
        return pgPool.useConnection { it.createNoteForUser("Untitled", userId) }.toNote()
    }

    override suspend fun getAllNoteFolder(userId: Long, excludedFolderIds: List<Long>): List<NoteFolder> {
        return pgPool.useConnection {
            noteWithFolderToFolderModels(it.getAllNoteFolder(userId, excludedFolderIds))
        }
    }

    override suspend fun getAllNoteMetaData(userId: Long): List<Note> {
        return pgPool.useConnection { it.getAllNote(userId) }.map { it.toNote() }
    }

    override suspend fun getNoteById(userId: Long, id: Long): Note? {
// TODO: get note and blocks in one transaction.
        val note = pgPool.useConnection { it.getNoteById(userId, id) }?.toNote()
        val noteBlocks = pgPool.useConnection { it.getNoteBlocksById(userId, id) }.map { it.toNoteBlock() }
        return note?.copy(blocks = noteBlocks.toMutableList())
    }

    override suspend fun getNoteFolderByNoteId(userId: Long, noteId: Long): NoteFolder? {
        return pgPool.useConnection {
            noteWithFolderToFolderModels(it.getNoteFolderByNoteId(userId, noteId)).firstOrNull()
        }
    }

    override suspend fun getNoteBlocksFlow(userId: Long, id: Long): Flow<NoteBlock> {
        return pgPool.useConnection { it.getNoteBlocksFlow(userId, id).map { it.toNoteBlock() } }
    }

    override suspend fun changeNoteTitleById(userId: Long, id: Long, title: String): Note? {
        return pgPool.useConnection { it.changeNoteTitle(userId, id, title) }?.toNote()
    }

    override suspend fun deleteNoteById(userId: Long, id: Long) {
        pgPool.useConnection { c ->
            val imageUrls = c.getNoteBlocksById(userId, id).mapNotNull { it.imageUrl }
            c.deleteNoteById(id)

            imageUrls.forEach { removeFile(imageUrlToLocalPath(it)) }
        }
    }

    override suspend fun addBlockAfter(
        userId: Long,
        noteId: Long,
        blockId: Long?,
        type: BlockType
    ): NoteBlock? {
        val block = pgPool.useConnection { it.createBlockAfter(userId, noteId, type.toEntityType(), blockId) }
        return block?.toNoteBlock()
    }

    override suspend fun updateNoteBlock(
        userId: Long,
        noteId: Long,
        blockId: Long,
        type: BlockType,
        textContent: String,
        extra: Any?
    ): NoteBlock? {
        val block = pgPool.useConnection { c ->
            if (type == BlockType.TASK_LIST_ITEM) {
                c.updateNoteBlock(userId, noteId, blockId, type.toEntityType(), textContent, checked = extra == true)
            } else {
                c.updateNoteBlock(userId, noteId, blockId, type.toEntityType(), textContent)
            }
        }
        return block?.toNoteBlock()
    }

    override suspend fun deleteBlock(userId: Long, noteId: Long, noteBlockId: Long) {
        val deletedUrl = pgPool.useConnection { it.deleteBlockById(userId, noteId, noteBlockId) }
        if (deletedUrl != null) {
            removeFile(imageUrlToLocalPath(deletedUrl))
        }
    }

    override suspend fun saveImageToImageBlock(
        userId: Long,
        noteId: Long,
        noteBlockId: Long,
        fileSource: AsyncRawSource
    ): NoteBlock.Image? {
        val oldBlock = pgPool.useConnection { it.getNoteBlocksByNoteBlockId(userId, noteBlockId) }
        if (oldBlock?.type != "image") return null

        val newImageUrl = saveImageAndRemoveOldIfNotNull(fileSource, oldBlock.imageUrl)

        return pgPool.useConnection { it.updateImageBlock(userId,noteId, noteBlockId, newImageUrl) }
            ?.toNoteBlock() as? NoteBlock.Image
    }

    override suspend fun saveImageAndChangeBlockTypeToImage(
        userId: Long,
        noteId: Long,
        noteBlockId: Long,
        fileSource: AsyncRawSource
    ): NoteBlock.Image? {
        val oldBlock = pgPool.useConnection { it.getNoteBlocksByNoteBlockId(userId, noteBlockId) }
        if (oldBlock == null) return null

        val newImageUrl = saveImageAndRemoveOldIfNotNull(fileSource, oldBlock.imageUrl)

        return pgPool.useConnection { it.saveImageToBlockAndChangeTypeToImage(userId, noteId, noteBlockId, newImageUrl) }
            ?.toNoteBlock() as? NoteBlock.Image
    }

    override suspend fun saveTextToTextBlock(
        userId: Long,
        noteId: Long,
        noteBlockId: Long,
        content: String
    ): NoteBlock.Text? {
        val block = pgPool.useConnection { it.updateContentForTextBlock(userId, noteId, noteBlockId, content) }
            ?.toNoteBlock() as? NoteBlock.Text
        return block
    }

    override suspend fun saveCheckedToTaskListItemBlock(
        userId: Long,
        noteId: Long,
        noteBlockId: Long,
        checked: Boolean
    ): NoteBlock.TaskListItem? {
        val oldBlock = pgPool.useConnection { it.getNoteBlocksByNoteBlockId(userId, noteBlockId) }
        if (oldBlock == null) return null

        return pgPool.useConnection { it.saveCheckedToBlock(userId, noteId, noteBlockId, checked) }
            ?.toNoteBlock() as? NoteBlock.TaskListItem
    }

    override suspend fun getNoteFolderById(userId: Long, folderId: Long): NoteFolder? {
        return pgPool.useConnection {
            noteWithFolderToFolderModels(it.getNoteFolderById(userId, folderId)).firstOrNull()
        }
    }

    override suspend fun newFolder(userId: Long, folderName: String): NoteFolder {
        return pgPool.useConnection { it.createNewNoteFolder(userId, folderName) }.toFolder()
    }

    override suspend fun changeFolderName(userId: Long, folderId: Long, name: String): NoteFolder? {
        return pgPool.useConnection {
            noteWithFolderToFolderModels(it.changeNoteFolderName(userId, folderId, name)).firstOrNull()
        }
    }

    override suspend fun deleteNoteFolder(userId: Long, folderId: Long): NoteFolder? {
        return pgPool.useConnection { it.deleteNoteFolder(userId, folderId)?.toFolder() }
    }

    override suspend fun changeNoteFolder(userId: Long, noteId: Long, folderId: Long?): Note? {
        return pgPool.useConnection { it.changeNoteFolder(userId, noteId, folderId)?.toNote() }
    }
}

private fun NoteUserEntity.toUser(): User = User(id = id, username = username, passwordHash = passwordHash)
private fun NotesEntity.toNote(): Note = Note(id = id, title = title)
private fun NoteFolderEntity.toFolder(notes: List<Note> = emptyList()): NoteFolder = NoteFolder(id = id, name = name, notes = notes)
private fun noteWithFolderToFolderModels(
    rows: List<NoteWithFolderEntity>,
): List<NoteFolder> =
    rows.groupBy { it.folderId }.map { (folderId, items) ->
        NoteFolder(
            id = folderId,
            name = items.first().folderName,
            notes = items.mapNotNull { item ->
                val noteId = item.noteId ?: return@mapNotNull null
                val title = item.noteTitle ?: return@mapNotNull null
                Note(id = noteId, title = title)
            },
        )
    }

private fun NoteBlockEntity.toNoteBlock(): NoteBlock = when (type) {
    NoteBlockEntity.BLOCK_TYPE_H1 -> NoteBlock.Text.H1(blockId = id, textContent ?: "")
    NoteBlockEntity.BLOCK_TYPE_H2 -> NoteBlock.Text.H2(blockId = id, textContent ?: "")
    NoteBlockEntity.BLOCK_TYPE_H3 -> NoteBlock.Text.H3(blockId = id, textContent ?: "")
    NoteBlockEntity.BLOCK_TYPE_H4 -> NoteBlock.Text.H4(blockId = id, textContent ?: "")
    NoteBlockEntity.BLOCK_TYPE_TEXT -> NoteBlock.Text.Content(blockId = id, textContent ?: "")
    NoteBlockEntity.BLOCK_TYPE_IMAGE -> NoteBlock.Image(blockId = id, imageUrl)
    NoteBlockEntity.BLOCK_TYPE_TASK_LIST_ITEM -> NoteBlock.TaskListItem(blockId = id, checked, textContent ?: "")
    NoteBlockEntity.BLOCK_TYPE_CODE -> NoteBlock.Code(blockId = id, textContent ?: "")
    else -> error("not valid block type $type")
}

private fun BlockType.toEntityType(): String {
    return when (this) {
        BlockType.TEXT -> NoteBlockEntity.BLOCK_TYPE_TEXT
        BlockType.IMAGE -> NoteBlockEntity.BLOCK_TYPE_IMAGE
        BlockType.H1 -> NoteBlockEntity.BLOCK_TYPE_H1
        BlockType.H2 -> NoteBlockEntity.BLOCK_TYPE_H2
        BlockType.H3 -> NoteBlockEntity.BLOCK_TYPE_H3
        BlockType.H4 -> NoteBlockEntity.BLOCK_TYPE_H4
        BlockType.TASK_LIST_ITEM -> NoteBlockEntity.BLOCK_TYPE_TASK_LIST_ITEM
        BlockType.CODE -> NoteBlockEntity.BLOCK_TYPE_CODE
    }
}

suspend fun saveImageAndRemoveOldIfNotNull(
    fileSource: AsyncRawSource,
    oldImageUrl: String?
): String {
    val uuid = Uuid.random().toString()
    val filePath = Path(Config.UPLOAD_DIR, uuid).toString()

    currentLogger().trace("Saving image to $filePath")
    fileSource.saveFileToPath(filePath)
    currentLogger().trace("Save image finished to $filePath")

    if (oldImageUrl != null) {
        removeFile(imageUrlToLocalPath(oldImageUrl))
    }

    return "/attachments/$uuid"
}

fun imageUrlToLocalPath(url: String): String {
    return Path(Config.UPLOAD_DIR, imageFileName(url)).toString()
}

fun imageFileName(url: String): String {
    return url.substringAfterLast("/")
}