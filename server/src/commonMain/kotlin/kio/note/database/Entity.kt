package kio.note.database

import kio.postgres.types.PgInt8
import kio.postgres.types.PgText
import kio.postgres.types.PgTimestampTz
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    @SerialName("user_id")
    val userId: PgInt8,
)

@Serializable
data class NoteUserEntity(
    @SerialName("id")
    val id: PgInt8,
    @SerialName("username")
    val username: PgText,
    @SerialName("password_hash")
    val passwordHash: PgText,
    @SerialName("create_at")
    val createAt: PgTimestampTz,
)

@Serializable
data class NoteSessionEntity(
    @SerialName("id")
    val id: PgText,
    @SerialName("user_id")
    val userId: PgText,
    @SerialName("create_at")
    val createAt: PgTimestampTz,
)
