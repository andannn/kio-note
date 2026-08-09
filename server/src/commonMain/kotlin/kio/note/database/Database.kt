package kio.note.database

import kio.postegre.types.PgInt8
import kio.postegre.types.PgText
import kio.postegre.types.PgTimestampTz
import kio.postgres.conn.PgConnection
import kio.postgres.conn.exec
import kio.postgres.conn.query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
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

@Serializable
private class NotesToCreate(
    @SerialName("title")
    val title: PgText,
)

suspend fun PgConnection.createNote(title: PgText): NotesEntity {
    val note: Flow<NotesEntity> = query(
        """
            insert into notes(title)
            values ($1)
            returning id, title, create_at, update_at
        """.trimIndent(),
        NotesToCreate(title)
    )
    val createdNote = note.firstOrNull()
    return createdNote ?: error("note create failed")
}
