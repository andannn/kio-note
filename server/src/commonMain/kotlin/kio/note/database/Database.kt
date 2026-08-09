package kio.note.database

import kio.postegre.types.PgInt8
import kio.postegre.types.PgText
import kio.postegre.types.PgTimestampTz
import kio.postgres.conn.PgConnection
import kio.postgres.conn.exec
import kio.postgres.conn.query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toCollection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

suspend fun PgConnection.initDb(isTest: Boolean = false) {
    val createStr = if (isTest) "CREATE TEMPORARY" else "CREATE"
    exec(
        """
        $createStr table if not exists notes (
            id bigserial primary key,
            title text not null,
            create_at timestamptz not null default now(),
            update_at timestamptz not null default now()
        )
        """.trimIndent()
    )

    exec(
        """
        $createStr table if not exists note_blocks (
            id bigserial primary key,
            note_id bigint not null
                references notes(id)
                on delete cascade,
            type text not null,
            sort_order bigint not null,
            text_content text,
            image_url text
        )    
        """.trimIndent()
    )

    exec(
        """
        create index if not exists idx_note_blocks_note_order
        on note_blocks(note_id, sort_order);
        """.trimIndent()
    )
}

@Serializable
data class NoteBlockEntity(
    @SerialName("id")
    val id: PgInt8,
    @SerialName("note_id")
    val noteId: PgInt8,
    @SerialName("type")
    val type: PgText,
    @SerialName("sort_order")
    val sortOrder: PgInt8,
    @SerialName("text_content")
    val textContent: PgText?,
    @SerialName("image_url")
    val imageUrl: PgText?,
)

@Serializable
data class NotesEntity(
    @SerialName("id")
    val id: PgInt8,
    @SerialName("title")
    val title: PgText,
    @SerialName("create_at")
    val createAt: PgTimestampTz,
    @SerialName("update_at")
    val updateAt: PgTimestampTz,
)

@Serializable
private class NotesToCreate(
    @SerialName("title")
    val title: PgText,
)

suspend fun PgConnection.createNote(title: PgText): NotesEntity {
    val ret: Flow<NotesEntity> = query(
        """
            insert into notes(title)
            values ($1)
            returning id, title, create_at, update_at
        """.trimIndent(),
        NotesToCreate(title)
    )
    return ret.firstOrNull() ?: error("note create failed")
}

@Serializable
private class NoteBlockToCreate(
    @SerialName("note_id")
    val noteId: PgInt8,
    @SerialName("type")
    val type: PgText,
    @SerialName("sort_order")
    val sortOrder: PgInt8,
)

suspend fun PgConnection.createBlockAfter(
    noteId: Long,
    noteBlockType: String,
    afterBlockId: Long?
): NoteBlockEntity {
    val sortOrder = if (afterBlockId == null) {
        // insert first block
        1000L
    } else {
        findInsertSortOrder(noteId, afterBlockId)
    }
    val ret: Flow<NoteBlockEntity> = query(
        """
            insert into note_blocks(
                note_id,
                type,
                sort_order
            )
            values ($1, $2, $3)
            returning
                id,
                note_id,
                type,
                sort_order,
                text_content,
                image_url
        """.trimIndent(),
        NoteBlockToCreate(
            noteId = noteId,
            type = noteBlockType,
            sortOrder = sortOrder
        )
    )
    return ret.firstOrNull() ?: error("can not create note block.")
}

private suspend fun PgConnection.findInsertSortOrder(noteId: Long, afterBlockId: Long): Long {
    @Serializable
    data class Sort(
        @SerialName("sort_order") val value: PgInt8
    )

    val currentSortOrder: Sort = query<Sort>(
        """
        select sort_order from note_blocks
        where note_id = $noteId and id = $afterBlockId
        """.trimIndent()
    ).firstOrNull() ?: error("no sort order")

    val nextSortOrder: Sort? = query<Sort>(
        """
        select sort_order from note_blocks
        where note_id = $noteId AND sort_order > ${currentSortOrder.value}
        order by sort_order
        limit 1
        """.trimIndent()
    ).firstOrNull()
    val nextOrder = if (nextSortOrder == null) {
        currentSortOrder.value + 1000
    } else {
        (currentSortOrder.value + nextSortOrder.value) / 2
    }
    // TODO: re-assign all sort_order if calculated order equals to current/next sort_order
    return nextOrder
}

suspend fun PgConnection.getAllNote(): List<NotesEntity> {
    val ret: Flow<NotesEntity> = query("select * from notes")
    val list = mutableListOf<NotesEntity>()
    ret.toCollection(list)
    return list
}

suspend fun PgConnection.getNoteById(noteId: PgInt8): NotesEntity? {
    @Serializable
    data class Param(@SerialName("id") val id: PgInt8)

    val ret: Flow<NotesEntity> = query("select * from notes where id = $1", Param(noteId))
    return ret.firstOrNull()
}

suspend fun PgConnection.getNoteBlocksByNoteBlockId(noteBlockId: PgInt8): NoteBlockEntity? {
    @Serializable
    data class Param(@SerialName("id") val id: PgInt8)

    val ret: Flow<NoteBlockEntity> =
        query("select * from note_blocks where id = $1 order by sort_order", Param(noteBlockId))
    return ret.firstOrNull()
}

suspend fun PgConnection.getNoteBlocksById(noteId: PgInt8): List<NoteBlockEntity> {
    @Serializable
    data class Param(@SerialName("id") val id: PgInt8)

    val ret: Flow<NoteBlockEntity> =
        query("select * from note_blocks where note_id = $1 order by sort_order", Param(noteId))
    val list = mutableListOf<NoteBlockEntity>()
    ret.toCollection(list)
    return list
}

suspend fun PgConnection.deleteBlockById(blockId: Long) {
    @Serializable
    data class Param(@SerialName("id") val id: PgInt8)

    exec("delete from note_blocks where id = $1", Param(blockId))
}

suspend fun PgConnection.updateImageBlock(
    noteBlockId: Long,
    imageUrl: String
): NoteBlockEntity? {
    @Serializable
    data class Param(
        @SerialName("image_url") val imageUrl: PgText,
        @SerialName("id") val blockId: PgInt8,
    )

    val ret: Flow<NoteBlockEntity> = query(
        """
        update note_blocks set image_url = $1 where id = $2
        returning 
            id,
            note_id,
            type,
            sort_order,
            text_content,
            image_url
        """.trimIndent(),
        Param(imageUrl, noteBlockId)
    )
    return ret.firstOrNull()
}
