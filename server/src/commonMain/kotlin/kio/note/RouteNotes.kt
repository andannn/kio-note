package kio.note

import io.ktor.http.HttpStatusCode
import kio.http.CallContext
import kio.http.Route
import kio.http.delete
import kio.http.get
import kio.http.patch
import kio.http.post
import kio.http.receiveFormParameters
import kio.http.respond
import kio.http.respondHtml
import kio.http.route
import kio.note.components.*
import kio.note.domain.model.Note
import kotlinx.html.*

fun Route.notesRoute() {
    route("/notes") {
        post { call -> call.handleAddNotes() }
        get("/{id}") { call -> call.handleGetNote() }
        patch("/{id}") { call -> call.handlePatchNote() }
        delete("/{id}") { call -> call.handleDeleteNotes() }
        get("/{id}/edit") { call -> call.handleEditNote() }
    }
}

internal val notes = mutableListOf(
    Note(
        id = 1,
        title = "Hello",
        content = "Learn Htmx",
    ),
    Note(
        id = 2,
        title = "Learn HTML",
        content = "Today I learned HTML components.",
    ),
)

private var nextNoteId = 3L

private suspend fun CallContext.handlePatchNote() {
    val id = parameters["id"]?.toLongOrNull()
    if (id == null) {
        respond(HttpStatusCode.BadRequest)
        return
    }
    val noteIndex = notes.indexOfFirst { it.id == id }
    if (noteIndex == -1) {
        respond(HttpStatusCode.NotFound)
        return
    }

    val formParameter = receiveFormParameters()
    val title = formParameter["title"].orEmpty()
    val content = formParameter["content"].orEmpty()

    val oldNote = notes.removeAt(noteIndex)
    val newNote = oldNote.copy(title = title, content = content)
    notes.add(noteIndex, newNote)

    respondHtml {
        section {
            noteCard(newNote)
        }
    }
}

private suspend fun CallContext.handleGetNote() {
    val id = parameters["id"]?.toLongOrNull()
    if (id == null) {
        respond(HttpStatusCode.BadRequest)
        return
    }

    val note = notes.firstOrNull { it.id == id }
    if (note == null) {
        respond(HttpStatusCode.NotFound)
        return
    }

    respondHtml {
        section {
            noteCard(note)
        }
    }
}

private suspend fun CallContext.handleDeleteNotes() {
    val deleteId = parameters["id"]?.toLongOrNull()
    if (deleteId == null) {
        respond(HttpStatusCode.BadRequest)
        return
    }

    val removed = notes.removeIf { it.id == deleteId }

    if (!removed) {
        respond(HttpStatusCode.NotFound)
        return
    }

    respond(HttpStatusCode.OK)
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

private suspend fun CallContext.handleEditNote() {
    val editId = parameters["id"]?.toLongOrNull()
    if (editId == null) {
        respond(HttpStatusCode.BadRequest)
        return
    }

    val editNote = notes.firstOrNull { it.id == editId }
    if (editNote == null) {
        respond(HttpStatusCode.NotFound)
        return
    }

    respondHtml {
        section {
            noteEditForm(editNote)
        }
    }
}

