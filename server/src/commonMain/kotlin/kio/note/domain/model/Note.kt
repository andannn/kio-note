package kio.note.domain.model

data class Note(
    val id: Long,
    val title: String,
    val content: String,
)