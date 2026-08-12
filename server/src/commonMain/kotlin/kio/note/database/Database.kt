package kio.note.database

import kio.postgres.types.PgInt8
import kio.postgres.types.PgText
import kio.postgres.types.PgTimestampTz
import kio.postgres.conn.PgConnection
import kio.postgres.conn.exec
import kio.postgres.conn.param
import kio.postgres.conn.query
import kio.postgres.types.PostgresInt8Serializer
import kio.postgres.types.PostgresTextSerializer
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
) {
    companion object {
        const val BLOCK_TYPE_TEXT = "text"
        const val BLOCK_TYPE_IMAGE = "image"
        const val BLOCK_TYPE_H1 = "h1"
        const val BLOCK_TYPE_H2 = "h2"
        const val BLOCK_TYPE_H3 = "h3"
        const val BLOCK_TYPE_H4 = "h4"
    }
}

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

suspend fun PgConnection.deleteNoteById(id: Long) {
    exec("delete from notes where id = $1") {
        param(id, PostgresInt8Serializer)
    }
}

suspend fun PgConnection.createNote(title: PgText): NotesEntity {
    val ret: Flow<NotesEntity> = query(
        """
            insert into notes(title)
            values ($1)
            returning id, title, create_at, update_at
        """.trimIndent()
    ) {
        param(title, PostgresTextSerializer)
    }
    return ret.firstOrNull() ?: error("note create failed")
}

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
    ) {
        param(noteId, PostgresInt8Serializer)
        param(noteBlockType, PostgresTextSerializer)
        param(sortOrder, PostgresInt8Serializer)
    }
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

suspend fun PgConnection.updateContentForTextBlock(
    noteId: Long,
    noteBlockId: Long,
    content: String,
): NoteBlockEntity? {
    val ret: Flow<NoteBlockEntity> = query(
        """
        update note_blocks
        set text_content = $1
        where id = $2 and note_id = $3
        returning
           id,
           note_id,
           type,
           sort_order,
           text_content,
           image_url
        """.trimIndent()
    ) {
        param(content, PostgresTextSerializer)
        param(noteBlockId, PostgresInt8Serializer)
        param(noteId, PostgresInt8Serializer)
    }
    return ret.firstOrNull()

}

suspend fun PgConnection.changeNoteTitle(noteId: PgInt8, title: PgText): NotesEntity? {
    val ret: Flow<NotesEntity> = query(
        """
        update notes
        set title = $2, update_at = now()
        where id = $1
        returning id, title, create_at, update_at
        """.trimIndent()
    ) {
        param(noteId, PostgresInt8Serializer)
        param(title, PostgresTextSerializer)
    }
    return ret.firstOrNull()
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
    val ret: Flow<NoteBlockEntity> =
        query("select * from note_blocks where note_id = $1 order by sort_order") {
            param(noteId, PostgresInt8Serializer)
        }
    val list = mutableListOf<NoteBlockEntity>()
    ret.toCollection(list)
    return list
}

suspend fun PgConnection.deleteBlockById(blockId: Long) {
    exec("delete from note_blocks where id = $1") {
        param(blockId, PostgresInt8Serializer)
    }
}

suspend fun PgConnection.updateImageBlock(
    noteBlockId: Long,
    imageUrl: String
): NoteBlockEntity? {
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
    ) {
        param(imageUrl, PostgresTextSerializer)
        param(noteBlockId, PostgresInt8Serializer)
    }
    return ret.firstOrNull()
}
