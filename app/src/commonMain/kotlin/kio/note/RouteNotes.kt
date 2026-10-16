package kio.note

import io.ktor.http.HttpStatusCode
import kio.async.buffered
import kio.async.writeString
import kio.compression.file
import kio.compression.writeZip
import kio.http.CallContext
import kio.http.CallInterceptor
import kio.http.Route
import kio.http.delete
import kio.http.get
import kio.http.patch
import kio.http.post
import kio.http.receiveFormParameters
import kio.http.respond
import kio.http.respondHtml
import kio.http.respondWithSink
import kio.http.route
import kio.note.components.noteBlock
import kio.note.components.noteContent
import kio.note.components.noteFolders
import kio.note.components.noteItem
import kio.note.components.noteList
import kio.note.components.noteMainContentEmpty
import kio.note.domain.BlockType
import kio.note.domain.NoteBlock
import kio.note.domain.Repository
import kio.note.domain.imageFileName
import kio.note.domain.imageUrlToLocalPath
import kio.note.page.noteMainPage
import kio.note.util.fileSource
import kio.note.util.hxSwapOob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.html.div
import kotlinx.html.id
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

context(_: Repository)
fun Route.notesRoute() {
    route("/notes") {
        post { call -> call.handleNewNote() }

        route("recent") {
            get { call -> call.handleGetNoteList() }
        }

        route("folders") {
            get { call -> call.handleGetNoteFolders() }
        }

        route("/{id}") {
            inject(NoteId()) {
                get { call -> call.noteMainPage(noteId = requireNoteId()) }
                delete { call -> call.handleDeleteNote() }

                route("export") {
                    get { call -> call.handleExportNote()  }
                }

                route("editor") {
                    get { call -> call.handleGetNote() }
                }

                route("title") {
                    patch { call -> call.handleChangeTitle() }
                }

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
private suspend fun CallContext.handleGetNoteFolders() {
    val noteFolder = repo.getAllNoteFolder(requireSession().userId)
    respondHtml {
        noteFolders(noteFolder)
    }
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
            val baseFolder = note.title
                .replace(Regex("""[<>:"/\\|?*\x00-\x1F]"""), "_")
                .trim()
                .trimEnd('.')
                .ifEmpty { "note-${note.id}" }
            val imageUrls = mutableListOf<String>()
            file("${baseFolder}/content.md") { zipSink ->
                repo.getNoteBlocksFlow(note.id).collect { block ->
                    val markdownBlock = when (block) {
                        is NoteBlock.Image -> {
                            if (block.url != null) {
                                imageUrls.add(block.url)
                                "![${block.blockId}](./${imageFileName(block.url)})"
                            } else {
                                ""
                            }
                        }
                        is NoteBlock.TaskListItem -> """- [${if(block.checked) "x" else " " }] ${block.text}"""
                        is NoteBlock.Text.Content -> block.text
                        is NoteBlock.Text.H1 -> "# ${block.text}"
                        is NoteBlock.Text.H2 -> "## ${block.text}"
                        is NoteBlock.Text.H3 -> "### ${block.text}"
                        is NoteBlock.Text.H4 -> "#### ${block.text}"
                        is NoteBlock.Code -> """
```
${block.text}
```
                        """.trimIndent()
                    }

                    zipSink.writeString(markdownBlock)
                    zipSink.writeString("\n\n")
                }
            }

            imageUrls.forEach { url ->
                val fileName = imageFileName(url)
                val localPath = imageUrlToLocalPath(url)
                file("${baseFolder}/${fileName}") { sink ->
                    val source = fileSource(localPath).buffered()
                    sink.transferFrom(source)
                }
            }
        }
    }
}
