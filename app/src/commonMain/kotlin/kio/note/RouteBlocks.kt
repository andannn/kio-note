package kio.note

import io.ktor.http.HttpStatusCode
import kio.async.AsyncRawSource
import kio.http.CallContext
import kio.http.CallInterceptor
import kio.http.Route
import kio.http.currentLogger
import kio.http.delete
import kio.http.info
import kio.http.post
import kio.http.receiveFormParameters
import kio.http.receiveMultipart
import kio.http.respond
import kio.http.respondHtml
import kio.http.route
import kio.note.components.noteBlock
import kio.note.domain.BlockType
import kio.note.domain.Repository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

context(_: Repository)
fun Route.noteBlockOperations() {
    route("{blockId}") {
        inject(BlockId()) {
            delete { call -> call.handleDeleteBlock() }
            post("text") { call -> call.handleChangeTextBlock() }
            post("upload-image") { call -> call.handleUploadImageBlock() }
            post("paste-image") { call -> call.handlePasteImageToTextBlock() }
            post("after") { call -> call.handleAddBlockAfter() }
            post("type") { call -> call.handleChangeTextBlockType() }
            post("checkbox") { call -> call.handleChangeCheckBox() }
        }
    }
}

private fun BlockId() = CallInterceptor { call, proceed ->
    val noteBlockId = call.requestParameters["blockId"]?.toLongOrNull()
    if (noteBlockId == null) {
        call.respond(
            HttpStatusCode.BadRequest,
            message = "request blockId parameter but get ${call.requestParameters["blockId"]}"
        )
    } else {
        withContext(CoroutineBlockId(noteBlockId)) {
            proceed(call)
        }
    }
}

private suspend fun requireBlockId(): Long {
    return currentCoroutineContext()[CoroutineBlockId]?.blockId ?: error("no block id")
}

private data class CoroutineBlockId(
    val blockId: Long
) : AbstractCoroutineContextElement(CoroutineBlockId) {
    companion object Key : CoroutineContext.Key<CoroutineBlockId>

    override fun toString(): String = "CoroutineBlockId(${blockId})"
}

context(repo: Repository)
private suspend fun CallContext.handleDeleteBlock() {
    val noteId = requireNoteId()
    val noteBlockId = requireBlockId()

    repo.deleteBlock(noteId, noteBlockId)
    respond(HttpStatusCode.OK)
}

context(repo: Repository)
private suspend fun CallContext.handleChangeTextBlock() {
    val noteId = requireNoteId()
    val noteBlockId = requireBlockId()

    val content = receiveFormParameters()["text"]
    if (content == null) {
        respond(HttpStatusCode.BadRequest)
        return
    }

    repo.saveTextToTextBlock(noteId, noteBlockId, content)
    respond(HttpStatusCode.OK)
}

context(repo: Repository)
private suspend fun CallContext.handleUploadImageBlock() {
    val noteId = requireNoteId()
    val noteBlockId = requireBlockId()

    val reader = receiveMultipart()
    var imageFileSource: AsyncRawSource? = null
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
private suspend fun CallContext.handlePasteImageToTextBlock() {
    val noteId = requireNoteId()
    val noteBlockId = requireBlockId()

    val reader = receiveMultipart()
    var imageFileSource: AsyncRawSource? = null
    while (true) {
        val part = reader.nextPart() ?: break
        if (part.contentDisposition?.name == "image") {
            imageFileSource = part.body
            break
        }
    }

    if (imageFileSource == null) {
        respond(HttpStatusCode.BadRequest, "no image file source.")
        return
    }

    val image = repo.saveImageAndChangeBlockTypeToImage(noteId, noteBlockId, imageFileSource)

    if (image == null) {
        respond(HttpStatusCode.BadRequest, "image save failed")
        return
    }

    respondHtml {
        noteBlock(noteId, image, isNewAdded = true)
    }
}


context(repo: Repository)
private suspend fun CallContext.handleAddBlockAfter() {
    val type = requestParameters["type"]
    val noteId = requireNoteId()
    val noteBlockId = requireBlockId()
    currentLogger().info("Trying to add block after noteBlockId=$noteBlockId for note=$noteId.")
    if (type == null) {
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
private suspend fun CallContext.handleChangeCheckBox() {
    val noteId = requireNoteId()
    val noteBlockId = requireBlockId()
    val checked = receiveFormParameters()["check"].toBoolean()
    val block = repo.saveCheckedToTaskListItemBlock(noteId, noteBlockId, checked)
    if (block == null) {
        respond(HttpStatusCode.NotFound)
        return
    }
    respondHtml {
        noteBlock(noteId, block)
    }
}

context(repo: Repository)
private suspend fun CallContext.handleChangeTextBlockType() {
    val formParams = receiveFormParameters()
    val textContent = formParams["text"]
    val noteId = requireNoteId()
    val noteBlockId = requireBlockId()

    currentLogger().info("trying to change text block type for noteId=$noteId, noteBlock=$noteBlockId")

    if (textContent == null) {
        respond(HttpStatusCode.BadRequest)
        return
    }
    val (blockType, content, extra) = parseBlockTypeAndTextContent(textContent)
    if (blockType == null || !blockType.isTextBlock()) {
        respond(HttpStatusCode.BadRequest)
        return
    }


    val block = repo.updateNoteBlock(noteId, noteBlockId, blockType, content, extra)
    if (block == null) {
        respond(HttpStatusCode.NotFound)
        return
    }

    respondHtml {
        noteBlock(noteId, block)
    }
}

private fun parseBlockTypeAndTextContent(text: String): Triple<BlockType?, String, Any?> {
    return when {
        text.startsWith("####") -> Triple(BlockType.H4, text.removePrefix("####"), null)
        text.startsWith("###") -> Triple(BlockType.H3, text.removePrefix("###"), null)
        text.startsWith("##") -> Triple(BlockType.H2, text.removePrefix("##"), null)
        text.startsWith("#") -> Triple(BlockType.H1, text.removePrefix("#"), null)
        text.startsWith("[x]") -> Triple(BlockType.TASK_LIST_ITEM, text.removePrefix("[x]"), true)
        text.startsWith("[ ]") -> Triple(BlockType.TASK_LIST_ITEM, text.removePrefix("[ ]"), false)
        else -> Triple(null, text, null)
    }
}
