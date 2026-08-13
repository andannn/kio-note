package kio.note

import io.ktor.http.HttpStatusCode
import kio.async.AsyncRawSource
import kio.http.CallContext
import kio.http.Route
import kio.http.delete
import kio.http.get
import kio.http.patch
import kio.http.post
import kio.http.receiveFormParameters
import kio.http.receiveMultipart
import kio.http.respond
import kio.http.respondHtml
import kio.http.route
import kio.note.components.noteBlock
import kio.note.components.noteContent
import kio.note.components.noteItem
import kio.note.components.noteList
import kio.note.components.noteMainContentEmpty
import kio.note.domain.BlockType
import kio.note.domain.Repository
import kio.note.util.hxSwapOob
import kotlinx.html.div
import kotlinx.html.id

context(_: Repository)
fun Route.notesRoute() {
    route("/notes") {
        get { call -> call.handleGetAllNotes() }
        post { call -> call.handleNewNote() }

        route("/{id}") {
            get { call -> call.handleGetNote() }
            delete { call -> call.handleDeleteNote() }

            route("title") {
                patch { call -> call.handleChangeTitle() }
            }

            route("blocks/{blockId}") {
                delete { call -> call.handleDeleteBlock() }
                post("text") { call -> call.handleChangeTextBlock() }
                post("image") { call -> call.handleUploadImageBlock() }
                post("/after") { call -> call.handleAddBlockAfter() }
            }
        }
    }
}

context(repo: Repository)
private suspend fun CallContext.handleDeleteBlock() {
    val noteId = requestParameters["id"]?.toLongOrNull()
    val noteBlockId = requestParameters["blockId"]?.toLongOrNull()
    if (noteId == null || noteBlockId == null) {
        respond(HttpStatusCode.BadRequest)
        return
    }

    repo.deleteBlock(noteId, noteBlockId)
    respond(HttpStatusCode.OK)
}

context(repo: Repository)
private suspend fun CallContext.handleUploadImageBlock() {
    val noteId = requestParameters["id"]?.toLongOrNull()
    val noteBlockId = requestParameters["blockId"]?.toLongOrNull()
    if (noteId == null || noteBlockId == null) {
        respond(HttpStatusCode.BadRequest)
        return
    }

    val reader = receiveMultipart()
    var imageFileSource : AsyncRawSource? = null
    while (true) {
        val part = reader.nextPart() ?: break
        if (part.contentDisposition?.name == "image") {
            imageFileSource = part.body
            break
        }
    }

    if (imageFileSource == null) {
        respond(HttpStatusCode.BadRequest)
        return
    }
    val image = repo.saveImageToImageBlock(noteId, noteBlockId, imageFileSource)

    if (image == null) {
        respond(HttpStatusCode.BadRequest)
        return
    }

    respondHtml {
        noteBlock(noteId, image, isNewAdded = true)
    }
}

context(repo: Repository)
private suspend fun CallContext.handleChangeTextBlock() {
    val noteId = requestParameters["id"]?.toLongOrNull()
    val noteBlockId = requestParameters["blockId"]?.toLongOrNull()
    if (noteId == null || noteBlockId == null) {
        respond(HttpStatusCode.BadRequest)
        return
    }

    val content = receiveFormParameters()["text"]
    if (content == null) {
        respond(HttpStatusCode.BadRequest)
        return
    }

    repo.saveTextToTextBlock(noteId, noteBlockId, content)
    respond(HttpStatusCode.OK)
}

context(repo: Repository)
private suspend fun CallContext.handleAddBlockAfter() {
    val type = requestParameters["type"]
    val noteId = requestParameters["id"]?.toLongOrNull()
    val noteBlockId = requestParameters["blockId"]?.toLongOrNull()
    if (type == null || noteId == null || noteBlockId == null) {
        respond(HttpStatusCode.BadRequest)
        return
    }

    val blockType = BlockType.parse(type)
    if (blockType == null) {
        respond(HttpStatusCode.BadRequest)
        return
    }

    val newBlock = repo.addBlockAfter(noteId, noteBlockId, blockType)
    if (newBlock == null) {
        respond(HttpStatusCode.NotFound)
        return
    }

    respondHtml {
        noteBlock(noteId, newBlock, isNewAdded = true)
    }
}

context(repo: Repository)
private suspend fun CallContext.handleGetAllNotes() {
    val notes = repo.getAllNoteMetaData()
    respondHtml {
        noteList(notes)
    }
}

context(repo: Repository)
private suspend fun CallContext.handleNewNote() {
    val note = repo.createNewNote()
    val noteBlock = repo.addBlockAfter(noteId = note.id, blockId = null, type = BlockType.TEXT)!!
    val newNote = note.copy(blocks = mutableListOf(noteBlock))
    respondHtml {
        noteItem(newNote, true)

        div {
            this.id = "note-content"
            hxSwapOob = "innerHTML"

            noteContent(newNote)
        }
    }
}

context(repo: Repository)
private suspend fun CallContext.handleChangeTitle() {
    val id = requestParameters["id"]?.toLongOrNull()
    if (id == null) {
        respond(HttpStatusCode.BadRequest)
        return
    }

    val newTitle = receiveFormParameters()["title"]
    if (newTitle == null) {
        respond(HttpStatusCode.BadRequest)
        return
    }

    val note = repo.changeNoteTitleById(id, newTitle)
    if (note == null) {
        respond(HttpStatusCode.NotFound)
        return
    }

    respondHtml {
        div {
            this.id = "note-${note.id}"
            hxSwapOob = "innerHTML"

            noteItem(note)
        }
    }
}

context(repo: Repository)
private suspend fun CallContext.handleDeleteNote() {
    val idToDelete = requestParameters["id"]?.toLongOrNull()
    val currentNoteId = requestParameters["currentNoteId"]?.toLongOrNull()
    if (idToDelete == null) {
        respond(HttpStatusCode.BadRequest)
        return
    }

    repo.deleteNoteById(idToDelete)
    if (currentNoteId != idToDelete) {
        respond(HttpStatusCode.OK)
        return
    }

    respondHtml {
        div {
            id = "note-content"
            hxSwapOob = "innerHTML"

            noteMainContentEmpty()
        }
    }
}

context(repo: Repository)
private suspend fun CallContext.handleGetNote() {
    val id = requestParameters["id"]?.toLongOrNull()
    if (id == null) {
        respond(HttpStatusCode.BadRequest)
        return
    }

    val note = repo.getNoteById(id)
    if (note == null) {
        respond(HttpStatusCode.NotFound)
        return
    }

    respondHtml {
        noteContent(note)
    }
}
