package kio.note

import io.ktor.http.HttpStatusCode
import kio.http.CallContext
import kio.http.CallInterceptor
import kio.http.Route
import kio.http.delete
import kio.http.get
import kio.http.post
import kio.http.respond
import kio.http.receiveFormParameters
import kio.http.respondHtml
import kio.http.route
import kio.note.components.folderItem
import kio.note.components.folderNameEditor
import kio.note.components.noteFolders
import kio.note.domain.Repository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

context(_: Repository)
fun Route.notesFolders() {
    route("folders") {
        post { call -> call.handleNewFolder() }
        get { call -> call.handleGetNoteFolders() }

        route("/{id}") {
            inject(FolderId()) {
                delete { call -> call.handleDeleteFolder() }
                post("name-editing") { call -> call.handleEditName() }
                post("name-commit") { call -> call.handleCommitChangeName() }
            }
        }
    }
}

suspend fun requireFolderId(): Long {
    return currentCoroutineContext()[CoroutineFolderId]?.folderId ?: error("no noteId")
}

private fun FolderId() = CallInterceptor { call, proceed ->
    val noteId = call.requestParameters["id"]?.toLongOrNull()
    if (noteId == null) {
        call.respond(
            HttpStatusCode.BadRequest,
            message = "request note id parameter but get ${call.requestParameters["id"]}"
        )
    } else {
        withContext(CoroutineFolderId(noteId)) {
            proceed(call)
        }
    }
}

private data class CoroutineFolderId(
    val folderId: Long
) : AbstractCoroutineContextElement(CoroutineFolderId) {
    companion object Key : CoroutineContext.Key<CoroutineFolderId>

    override fun toString(): String = "CoroutineFolderId(${folderId})"
}

context(repo: Repository)
private suspend fun CallContext.handleCommitChangeName() {
    val name = receiveFormParameters()["name"]
    if (name == null) {
        respond(HttpStatusCode.BadRequest)
        return
    }

    val folder = repo.changeFolderName(requireFolderId(), name)

    respondHtml {
        folderItem(folder)
    }
}


context(repo: Repository)
private suspend fun CallContext.handleDeleteFolder() {
    val deleteId = requireFolderId()
    val deleted = repo.deleteNoteFolder(deleteId)
    if (deleted == null) {
        respond(HttpStatusCode.NotFound)
        return
    }

    respondHtml {}
}

context(repo: Repository)
private suspend fun CallContext.handleEditName() {
    val folderId = requireFolderId()
    val folder = repo.getNoteFolderById(folderId)
    if (folder == null) {
        respond(HttpStatusCode.NotFound)
        return
    }

    respondHtml {
        folderNameEditor(folder)
    }
}

context(repo: Repository)
private suspend fun CallContext.handleNewFolder() {
    val folder = repo.newFolder()
    respondHtml {
        folderItem(folder)
    }
}

context(repo: Repository)
private suspend fun CallContext.handleGetNoteFolders() {
    val noteFolder = repo.getAllNoteFolder(requireSession().userId)
    respondHtml {
        noteFolders(noteFolder)
    }
}
