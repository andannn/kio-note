package kio.note.domain

data class Note(
    val id: Long,
    val title: String,
    val content: String,
)