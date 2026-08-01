package kio.note

import kio.async.io.ServerSocket
import kio.async.readString
import kio.http.CallContext
import kio.http.get
import kio.http.httpServer
import kio.http.post
import kio.http.receiveFormParameters
import kio.http.respondHtml
import kio.http.route
import kio.http.staticResource
import kio.note.components.noteCard
import kio.note.domain.Note
import kotlinx.html.*

private val notes = mutableListOf<Note>()
private var nextNoteId = 1L

suspend fun noteServer(serverSocket: ServerSocket) {
    httpServer(serverSocket) {
        get("/") { call ->
            call.respondHtml {
                noteMainPage()
            }
        }

        route("notes") {
            post { call ->
                call.handleAddNotes()
            }
        }

        staticResource("/", "resource")
    }
}

private suspend fun CallContext.handleAddNotes() {
    val formParameter = receiveFormParameters()
    val title = formParameter["title"].orEmpty()
    val content = formParameter["content"].orEmpty()
    if (title.isBlank()) {
        respondHtml {
            p(classes = "form-error") {
                +"Title is required."
            }
        }
        return
    }

    val note = Note(
        id = nextNoteId++,
        title = title,
        content = content,
    )
    notes += note
    respondHtml {
        section {
            noteCard(note)
        }
    }
}
