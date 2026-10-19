package kio.note

import io.ktor.http.*
import kio.compression.writeZip
import kio.http.*
import kio.note.components.*
import kio.note.domain.BlockType
import kio.note.domain.Repository
import kio.note.page.noteMainPage
import kio.note.util.hxSwapOob
import kio.note.util.writeMarkdownFolder
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.ul
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

context(_: Repository)
fun Route.notesRoute() {
    route("/notes") {
        post { call -> call.handleNewNote() }

        route("recent") {
            get { call -> call.handleGetNoteList() }
        }

        notesFolders()

        route("/{id}") {
            inject(NoteId()) {
                get { call -> call.noteMainPage(noteId = requireNoteId()) }
                delete { call -> call.handleDeleteNote() }

                post("change_folder") { call -> call.handleChangeFolder() }
                get("target_folder_list") { call -> call.handleGetMoveTargetFolders() }
                get("export") { call -> call.handleExportNote() }
                get("editor") { call -> call.handleGetNote() }
                patch("title") { call -> call.handleChangeTitle() }

                route("blocks") {
                    post { call -> call.handleAddFirstBlock() }

                    noteBlockOperations()
                }
            }
        }
    }
}

suspend fun requireNoteId(): Long {
    return currentCoroutineContext()[CoroutineNoteId]?.noteId ?: error("no noteId")
}

private fun NoteId() = CallInterceptor { call, proceed ->
    val noteId = call.requestParameters["id"]?.toLongOrNull()
    if (noteId == null) {
        call.respond(
            HttpStatusCode.BadRequest,
            message = "request note id parameter but get ${call.requestParameters["id"]}"
        )
    } else {
        withContext(CoroutineNoteId(noteId)) {
            proceed(call)
        }
    }
}

private data class CoroutineNoteId(
    val noteId: Long
) : AbstractCoroutineContextElement(CoroutineNoteId) {
    companion object Key : CoroutineContext.Key<CoroutineNoteId>

    override fun toString(): String = "CoroutineNoteId(${noteId})"
}

context(repo: Repository)
private suspend fun CallContext.handleGetNoteList() {
    val notes = repo.getAllNoteMetaData(requireSession().userId)
    val selectedNoteId = requestParameters["selectedNoteId"]
    respondHtml {
        noteList(notes, selectedNoteId)
    }
}

context(repo: Repository)
private suspend fun CallContext.handleNewNote() {
    val note = repo.createNewNoteForUser(requireSession().userId)
    val noteBlock = repo.addBlockAfter(noteId = note.id, blockId = null, type = BlockType.TEXT)!!
    val newNote = note.copy(blocks = mutableListOf(noteBlock))
    respondHtml(
        configHeaders = {
            append("HX-Push-Url", "/notes/${newNote.id}")
        }
    ) {
        noteItem(newNote, true)

        div {
            this.id = "note-content"
            hxSwapOob = "innerHTML"

            noteContent(newNote, focusFirstBlock = true)
        }
    }
}

context(repo: Repository)
private suspend fun CallContext.handleChangeTitle() {
    val id = requireNoteId()

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
    val idToDelete = requireNoteId()
    val currentNoteId = requestParameters["currentNoteId"]?.toLongOrNull()

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
    val id = requireNoteId()

    val note = repo.getNoteById(id)
    if (note == null) {
        respond(HttpStatusCode.NotFound)
        return
    }

    respondHtml {
        noteContent(note, focusFirstBlock = false)
    }
}

context(repo: Repository)
private suspend fun CallContext.handleChangeFolder() {
    val folderId = requestParameters["folderId"]?.toLongOrNull()
    val note = repo.changeNoteFolder(requireNoteId(), folderId)
    if (note == null) {
        respond(HttpStatusCode.NotFound)
        return
    }

    val userId = requireSession().userId
    val folders = repo.getAllNoteFolder(userId)
    respondHtml {
        ul {
            id = "folder_items"
            hxSwapOob = "innerHTML"
            noteFolders(folders, expandedFolderId = folderId)
        }
    }
}

context(repo: Repository)
private suspend fun CallContext.handleGetMoveTargetFolders() {
    val noteId = requireNoteId()
    val note = repo.getNoteById(noteId)
    if (note == null) {
        respond(HttpStatusCode.NotFound)
        return
    }
    val noteFolder = repo.getNoteFolderByNoteId(noteId)
    val excludedFolderIds = noteFolder?.id?.let { listOf(it) } ?: emptyList()
    val folders = repo.getAllNoteFolder(requireSession().userId, excludedFolderIds)
    respondHtml { moveTargetFolderList(noteId, folders) }
}

context(repo: Repository)
private suspend fun CallContext.handleAddFirstBlock() {
    val noteId = requireNoteId()
    val type = requestParameters["type"]?.let { BlockType.parse(it) }

    if (type == null) {
        respond(HttpStatusCode.BadRequest)
        return
    }

    val block = repo.addBlockAfter(
        noteId = noteId,
        blockId = null,
        type = type,
    )

    if (block == null) {
        respond(HttpStatusCode.NotFound)
        return
    }

    respondHtml {
        noteBlock(noteId, block, autoFocus = true)
    }
}

context(repo: Repository)
private suspend fun CallContext.handleExportNote() {
    val note = repo.getNoteById(requireNoteId())
    if (note == null) {
        respond(HttpStatusCode.NotFound)
        return
    }

    respondWithSink(
        HttpStatusCode.OK,
        configHeaders = {
            append("Content-Disposition", "attachment; filename=${note.title}.zip")
        }
    ) { sink ->
        sink.writeZip {
            writeMarkdownFolder(null, note, blocksGetter = { repo.getNoteBlocksFlow(it) })
        }
    }
}
