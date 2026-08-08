package kio.note

import io.ktor.http.HttpStatusCode
import kio.async.readString
import kio.http.CallContext
import kio.http.Route
import kio.http.delete
import kio.http.get
import kio.http.post
import kio.http.respond
import kio.http.respondHtml
import kio.http.route
import kio.note.components.noteBlock
import kio.note.components.noteContent
import kio.note.domain.BlockType
import kio.note.domain.Repository

context(_: Repository)
fun Route.notesRoute() {
    route("/notes") {
        route("/{id}") {
            get { call -> call.handleGetNote() }

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
    val noteId = parameters["id"]?.toLongOrNull()
    val noteBlockId = parameters["blockId"]?.toLongOrNull()
    if (noteId == null || noteBlockId == null) {
        respond(HttpStatusCode.BadRequest)
        return
    }

    repo.deleteBlock(noteId, noteBlockId)
    respond(HttpStatusCode.OK)
}

context(repo: Repository)
private suspend fun CallContext.handleUploadImageBlock() {
    respond(HttpStatusCode.OK)
}

context(repo: Repository)
private suspend fun CallContext.handleChangeTextBlock() {
    respond(HttpStatusCode.OK)
}

context(repo: Repository)
private suspend fun CallContext.handleAddBlockAfter() {
    val type = parameters["type"]
    val noteId = parameters["id"]?.toLongOrNull()
    val noteBlockId = parameters["blockId"]?.toLongOrNull()
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
private suspend fun CallContext.handleGetNote() {
    val id = parameters["id"]?.toLongOrNull()
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
