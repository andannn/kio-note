package kio.note.database

import kio.postgres.types.PgInt8
import kio.postgres.types.PgText
import kio.postgres.conn.PgConnection
import kio.postgres.conn.TransactionScope
import kio.postgres.conn.param
import kio.postgres.conn.query
import kio.postgres.conn.transaction
import kio.postgres.types.PostgresBoolSerializer
import kio.postgres.types.PostgresInt8Serializer
import kio.postgres.types.PostgresTextSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toCollection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

suspend fun PgConnection.createUser(userName: String, passwordHash: String): NoteUserEntity? {
    val ret: Flow<NoteUserEntity> = query(
        """
        insert into users (
            username,
            password_hash
        )
        values ($1, $2)
        ON CONFLICT (username) DO NOTHING
        returning id, username, password_hash, create_at
        """.trimIndent()
    ) {
        param(userName, PostgresTextSerializer)
        param(passwordHash, PostgresTextSerializer)
    }
    return ret.firstOrNull()
}

suspend fun PgConnection.createSession(userId: Long, sessionId: String) {
    val ret = exec(
        """
            insert into sessions(
                id,
                user_id
            )
            values ($1, $2)
            """.trimIndent()
    ) {
        param(sessionId, PostgresTextSerializer)
        param(userId, PostgresInt8Serializer)
    }

    if (ret != "INSERT 0 1") {
        error("create session failed $ret")
    }
}

suspend fun PgConnection.getUserIdBySessionId(sessionId: String): Long? {
    @Serializable
    class UserId(val user_id: PgInt8)

    return query<UserId>(
        """
            select user_id
            from sessions
            where id = $1
            limit 1
            """.trimIndent()
    ) {
        param(sessionId, PostgresTextSerializer)
    }.firstOrNull()?.user_id
}

suspend fun PgConnection.getUserByUsername(userName: String): NoteUserEntity? {
    val ret: Flow<NoteUserEntity> = query(
        """
        select *
        from users
        where username = $1
        limit 1
        """.trimIndent()
    ) {
        param(userName, PostgresTextSerializer)
    }
    return ret.firstOrNull()
}

suspend fun PgConnection.deleteNoteById(id: Long) {
    exec("delete from notes where id = $1") {
        param(id, PostgresInt8Serializer)
    }
}

suspend fun PgConnection.createNoteForUser(title: PgText, userId: Long): NotesEntity {
    val ret: Flow<NotesEntity> = query(
        """
            insert into notes(title, user_id)
            values ($1, $2)
            returning id, title, create_at, update_at, user_id
        """.trimIndent()
    ) {
        param(title, PostgresTextSerializer)
        param(userId, PostgresInt8Serializer)
    }
    return ret.firstOrNull() ?: error("note create failed")
}

/**
 * add block to head if [afterBlockId] is null
 */
suspend fun PgConnection.createBlockAfter(
    noteId: Long,
    noteBlockType: String,
    afterBlockId: Long?
): NoteBlockEntity = transaction { t ->
    val sortOrder = t.findInsertSortOrder(noteId, afterBlockId)
    val ret: Flow<NoteBlockEntity> = t.query(
        """
            insert into note_blocks(
                note_id,
                type,
                sort_order
            )
            values ($1, $2, $3)
            returning id, note_id, type, sort_order, text_content, image_url, checked
        """.trimIndent(),
    ) {
        param(noteId, PostgresInt8Serializer)
        param(noteBlockType, PostgresTextSerializer)
        param(sortOrder, PostgresInt8Serializer)
    }
    t.touchNote(noteId)
    ret.firstOrNull() ?: error("can not create note block.")
}

suspend fun PgConnection.getAllNote(userId: Long): List<NotesEntity> {
    val ret: Flow<NotesEntity> = query("select * from notes where user_id = $1 order by update_at desc") {
        param(userId, PostgresInt8Serializer)
    }
    val list = mutableListOf<NotesEntity>()
    ret.toCollection(list)
    return list
}

suspend fun PgConnection.updateContentForTextBlock(
    noteId: Long,
    noteBlockId: Long,
    content: String,
): NoteBlockEntity? = transaction { t ->
    val ret: Flow<NoteBlockEntity> = t.query(
        """
        update note_blocks
        set text_content = $1
        where id = $2 and note_id = $3
        returning id, note_id, type, sort_order, text_content, image_url, checked
        """.trimIndent()
    ) {
        param(content, PostgresTextSerializer)
        param(noteBlockId, PostgresInt8Serializer)
        param(noteId, PostgresInt8Serializer)
    }
    t.touchNote(noteId)
    ret.firstOrNull()
}

suspend fun PgConnection.changeNoteTitle(noteId: PgInt8, title: PgText): NotesEntity? = transaction { t ->
    val ret: Flow<NotesEntity> = t.query(
        """
        update notes
        set title = $2, update_at = now()
        where id = $1
        returning id, title, create_at, update_at, user_id
        """.trimIndent()
    ) {
        param(noteId, PostgresInt8Serializer)
        param(title, PostgresTextSerializer)
    }
    t.touchNote(noteId)
    ret.firstOrNull()
}

suspend fun PgConnection.getNoteById(noteId: PgInt8): NotesEntity? {
    val ret: Flow<NotesEntity> = query("select * from notes where id = $1") {
        param(noteId, PostgresInt8Serializer)
    }
    return ret.firstOrNull()
}

suspend fun PgConnection.getNoteBlocksByNoteBlockId(noteBlockId: PgInt8): NoteBlockEntity? {
    val ret: Flow<NoteBlockEntity> =
        query("select * from note_blocks where id = $1 order by sort_order") {
            param(noteBlockId, PostgresInt8Serializer)
        }
    return ret.firstOrNull()
}

suspend fun PgConnection.getNoteBlocksById(noteId: PgInt8): List<NoteBlockEntity> {
    val ret = getNoteBlocksFlow(noteId)
    val list = mutableListOf<NoteBlockEntity>()
    ret.toCollection(list)
    return list
}

fun PgConnection.getNoteBlocksFlow(noteId: PgInt8): Flow<NoteBlockEntity> {
    val ret: Flow<NoteBlockEntity> =
        query("select * from note_blocks where note_id = $1 order by sort_order") {
            param(noteId, PostgresInt8Serializer)
        }
    return ret
}

// return image_url or null if not image block
suspend fun PgConnection.deleteBlockById(noteId: Long, blockId: Long): String? = transaction { t ->
    @Serializable data class Ret(@SerialName("image_url") val imageUrl: PgText?)
    val ret = t.query<Ret>("delete from note_blocks where id = $1 returning image_url") {
        param(blockId, PostgresInt8Serializer)
    }
    t.touchNote(noteId = noteId)
    ret.firstOrNull()?.imageUrl
}

suspend fun PgConnection.saveImageToBlockAndChangeTypeToImage(
    noteId: Long,
    noteBlockId: Long,
    imageUrl: String
): NoteBlockEntity? = transaction { t ->
    val ret: Flow<NoteBlockEntity> = t.query(
        """
        update note_blocks 
        set image_url = $1,
            type = 'image',
            text_content = NULL
        where id = $2
        returning id, note_id, type, sort_order, text_content, image_url, checked
        """.trimIndent(),
    ) {
        param(imageUrl, PostgresTextSerializer)
        param(noteBlockId, PostgresInt8Serializer)
    }

    t.touchNote(noteId)
    ret.firstOrNull()
}

suspend fun PgConnection.saveCheckedToBlock(
    noteId: Long,
    noteBlockId: Long,
    checked: Boolean
): NoteBlockEntity? = transaction { t ->
    val ret: Flow<NoteBlockEntity> = t.query(
        """
        update note_blocks
        set checked = $1,
            type = 'task-list-item'
        where id = $2
        returning id, note_id, type, sort_order, text_content, image_url, checked
        """.trimIndent(),
    ) {
        param(checked, PostgresBoolSerializer)
        param(noteBlockId, PostgresInt8Serializer)
    }

    t.touchNote(noteId)
    ret.firstOrNull()
}

suspend fun PgConnection.updateImageBlock(
    noteId: Long,
    noteBlockId: Long,
    imageUrl: String
): NoteBlockEntity? = transaction { t ->
    val ret: Flow<NoteBlockEntity> = t.query(
        """
        update note_blocks set image_url = $1 where id = $2
        returning id, note_id, type, sort_order, text_content, image_url, checked
        """.trimIndent(),
    ) {
        param(imageUrl, PostgresTextSerializer)
        param(noteBlockId, PostgresInt8Serializer)
    }
    t.touchNote(noteId)
    ret.firstOrNull()
}

suspend fun PgConnection.updateNoteBlock(
    noteId: Long,
    noteBlockId: Long,
    noteBlockType: String,
    textContent: String,
    checked: Boolean = false
): NoteBlockEntity? = transaction { t ->
    val ret: Flow<NoteBlockEntity> = t.query("""
        update note_blocks 
        set 
            type = $1,
            text_content = $2,
            checked = $3
        where id = $4
        returning id, note_id, type, sort_order, text_content, image_url, checked
    """.trimIndent()) {
        param(noteBlockType, PostgresTextSerializer)
        param(textContent, PostgresTextSerializer)
        param(checked, PostgresBoolSerializer)
        param(noteBlockId, PostgresInt8Serializer)
    }
    t.touchNote(noteId)
    ret.firstOrNull()
}


private suspend fun TransactionScope.touchNote(noteId: Long) {
    exec("""
        update notes set update_at = now() where id = $1
    """.trimIndent()) {
        param(noteId, PostgresInt8Serializer)
    }
}

private suspend fun TransactionScope.findInsertSortOrder(noteId: Long, afterBlockId: Long?): Long {
    @Serializable
    data class Sort(
        @SerialName("sort_order") val value: PgInt8
    )

    if (afterBlockId == null) {
        val firstSortOrder = query<Sort>(
            """
            select sort_order from note_blocks
            where note_id = $1
            order by sort_order
            limit 1
            """.trimIndent()
        ) {
            param(noteId, PostgresInt8Serializer)
        }.firstOrNull()

        return firstSortOrder?.value?.minus(1000L) ?: 1000L
    }

    val currentSortOrder: Sort = query<Sort>(
        """
        select sort_order from note_blocks
        where note_id = $1 and id = $2
        """.trimIndent()
    ) {
        param(noteId, PostgresInt8Serializer)
        param(afterBlockId, PostgresInt8Serializer)
    }
        .firstOrNull() ?: error("no sort order")

    val nextSortOrder: Sort? = query<Sort>(
        """
        select sort_order from note_blocks
        where note_id = $1 AND sort_order > $2
        order by sort_order
        limit 1
        """.trimIndent()
    ) {
        param(noteId, PostgresInt8Serializer)
        param(currentSortOrder.value, PostgresInt8Serializer)
    }
        .firstOrNull()

    if (nextSortOrder == null) {
        return currentSortOrder.value + 1000
    }

    // 1 2
    val ret = (currentSortOrder.value + nextSortOrder.value) / 2
    if (ret <= currentSortOrder.value || ret >= nextSortOrder.value) {
        reassignSortOrders(noteId)
        return findInsertSortOrder(noteId, afterBlockId)
    }
    return ret
}

private suspend fun TransactionScope.reassignSortOrders(noteId: Long) {
    exec(
        """
        with ordered as (
            select id,
                   row_number() over (order by sort_order, id) * 1000 as new_order
            from note_blocks
            where note_id = $1
        )
        update note_blocks as b
        set sort_order = ordered.new_order
        from ordered
        where b.id = ordered.id
        """.trimIndent()
    ) {
        param(noteId, PostgresInt8Serializer)
    }
}
