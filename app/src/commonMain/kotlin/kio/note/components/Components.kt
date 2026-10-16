package kio.note.components

import kio.note.domain.Note
import kio.note.domain.NoteBlock
import kio.note.domain.NoteFolder
import kio.note.util.hxDelete
import kio.note.util.hxGet
import kio.note.util.hxInclude
import kio.note.util.hxPatch
import kio.note.util.hxPost
import kio.note.util.hxPushUrl
import kio.note.util.hxSwap
import kio.note.util.hxTarget
import kio.note.util.hxTrigger
import kio.note.util.hxVals
import kotlinx.html.*

fun TagConsumer<*>.noteMainContentEmpty() {
    p { +"Select a note" }
}

fun TagConsumer<*>.noteAsideMenu(selectedNoteId: Long? = null) {

    val noteListId = "note-list-items"

    div(classes = "sidebar-header") {
        h1(classes = "sidebar-title") { +"Knote" }

        button(classes = "new-note-button") {
            hxPost = "/notes"
            hxTarget = "#$noteListId"
            hxSwap = "afterbegin"
            attributes["hx-on:click"] =
                """
            document.querySelectorAll('.note-item.selected')
                .forEach(it => it.classList.remove('selected'))
            """.trimIndent()

            span {
                +"＋New note"
            }
        }

        section(classes = "sidebar-folders") {
            hxGet = "/notes/folders"
            hxTrigger = "load"
            val nodeFolderId = "folder_items"
            hxTarget = "#$nodeFolderId"

            ul {
                id = nodeFolderId
            }
        }

        section(classes = "sidebar-notes") {
            hxGet = "/notes/recent?selectedNoteId=${selectedNoteId?.toString().orEmpty()}"
            hxTrigger = "load"
            hxTarget = "#$noteListId"
            hxSwap = "innerHTML"

            ul {
                id = noteListId
            }
        }
    }
}

fun TagConsumer<*>.noteFolders(folders: List<NoteFolder>) {
    folders.forEach { folder ->
        li(classes = "note-folder") {
            details(classes = "note-folder-details") {
                summary(classes = "note-folder-summary") {
                    span(classes = "note-folder-name") { +folder.name }
                    span(classes = "note-folder-count") { +folder.notes.size.toString() }
                }

                ul(classes = "note-folder-notes") {
                    if (folder.notes.isEmpty()) {
                        li(classes = "note-folder-empty") { +"No notes" }
                    } else {
                        folder.notes.forEach { note -> noteItem(note, isInFolder = true) }
                    }
                }
            }
        }
    }
}

fun TagConsumer<*>.noteList(notes: List<Note>, selectedNoteId: String?) {
    notes.forEach { note ->
        noteItem(note, note.id.toString() == selectedNoteId)
    }
}

fun TagConsumer<*>.noteItem(note: Note, selected: Boolean = false, isInFolder: Boolean = false) {
    li(classes = "note-item") {
        val noteId = "note-${note.id}"
        id = noteId

        if (selected) classes += setOf("selected")

        button(classes = "note-item-open") {
            hxGet = "/notes/${note.id}/editor"
            hxTarget = "#note-content"
            hxSwap = "innerHTML"
            hxPushUrl = "/notes/${note.id}"
            attributes["hx-on:click"] =
                """
                document.querySelectorAll('.note-item.selected')
                    .forEach(it => it.classList.remove('selected'));
                this.closest('.note-item').classList.add('selected');
                """.trimIndent()
            +note.title
        }

        noteItemOptionsMenu(note.id, isInFolder)
    }
}

fun TagConsumer<*>.noteContent(note: Note, focusFirstBlock: Boolean) {
    article(classes = "note-document") {
        hiddenInput {
            id = "current-note-id"
            name = "currentNoteId"
            value = note.id.toString()
        }

        div(classes = "note-header") {
            textArea(classes = "note-title") {
                name = "title"
                rows = "1"
                hxPatch = "/notes/${note.id}/title"
                hxTrigger = "input changed delay:500ms"
                hxSwap = "none"
                attributes["oninput"] = "resizeTextBlock(this)"
                attributes["onkeydown"] = "handleTitleKeyDown(event, this)"
                attributes["data-note-id"] = note.id.toString()

                +note.title
            }

            a(
                href = "/notes/${note.id}/export",
                classes = "note-export"
            ) {
                +"Export"
            }
        }

        div(classes = "note-blocks") {
            id = "note-blocks-${note.id}"

            if (note.blocks.isEmpty()) {
                emptyNoteBlocks(note.id)
            } else {
                note.blocks.forEachIndexed { index, block ->
                    if (index == 0 && focusFirstBlock) {
                        noteBlock(note.id, block, autoFocus = true)
                    } else {
                        noteBlock(note.id, block, autoFocus = false)
                    }
                }
            }
        }
    }
}

fun TagConsumer<*>.noteBlock(noteId: Long, block: NoteBlock, autoFocus: Boolean = false) {
    div(classes = "note-block") {
        val blockContainerId = "block-${block.blockId}"
        id = blockContainerId

        when (block) {
            is NoteBlock.Text -> {
                textNoteBlock(noteId, block, autoFocus)
            }

            is NoteBlock.Image -> {
                imageNoteBlock(blockContainerId, noteId, block, autoFocus)
            }

            is NoteBlock.TaskListItem -> {
                taskListItemBlock(noteId, block, autoFocus)
            }

            is NoteBlock.Code -> {
                codeNoteBlock(noteId, block, autoFocus)
            }
        }

        addBlockMenu(blockContainerId, noteId, block.blockId)
    }
}

private fun TagConsumer<*>.emptyNoteBlocks(noteId: Long) {
    button(classes = "empty-note-add-block") {
        hxPost = "/notes/$noteId/blocks?type=text"
        hxTarget = "#note-blocks-$noteId"
        hxSwap = "innerHTML"

        +"＋ Add block"
    }
}

private fun TagConsumer<*>.imageNoteBlock(
    blockContainerId: String,
    noteId: Long,
    block: NoteBlock.Image,
    autoFocus: Boolean = false,
) {
    div(classes = "image-block-content") {
        attributes["tabindex"] = "-1"
        attributes["onclick"] = "this.focus()"

        attributes["data-note-id"] = noteId.toString()
        attributes["data-block-id"] = block.blockId.toString()

        attributes["onkeydown"] = "handleImageBlockKeyDown(event, this)"

        if (autoFocus) {
            attributes["data-autofocus"] = "true"
        }

        if (block.url == null) {
            label(classes = "image-upload") {
                input(classes = "image-upload-input") {
                    type = InputType.file
                    name = "image"
                    accept = "image/*"

                    hxPost = "/notes/$noteId/blocks/${block.blockId}/upload-image"
                    hxTrigger = "change"
                    hxTarget = "#$blockContainerId"
                    hxSwap = "outerHTML"

                    attributes["hx-encoding"] = "multipart/form-data"
                }

                span(classes = "image-upload-icon") {
                    +"＋"
                }

                span(classes = "image-upload-text") {
                    +"Add image"
                }
            }
        } else {
            img(
                classes = "image-block-image",
                src = block.url,
            )
        }

        div(classes = "block-toolbar") {
            if (block.url != null) {
                label(classes = "block-toolbar-action") {
                    input(classes = "image-upload-input") {
                        type = InputType.file
                        name = "image"
                        accept = "image/*"

                        hxPost = "/notes/$noteId/blocks/${block.blockId}/image"
                        hxTrigger = "change"
                        hxTarget = "#$blockContainerId"
                        hxSwap = "outerHTML"

                        attributes["hx-encoding"] = "multipart/form-data"
                    }

                    +"Replace"
                }
            }

            button(classes = "block-toolbar-action") {
                hxDelete = "/notes/$noteId/blocks/${block.blockId}"
                hxTarget = "#$blockContainerId"
                hxSwap = "delete"

                +"Delete"
            }
        }
    }
}

private fun TagConsumer<*>.taskListItemBlock(
    noteId: Long,
    block: NoteBlock.TaskListItem,
    autoFocus: Boolean = false,
) {
    div(classes = "task-list-item") {
        input(type = InputType.checkBox, classes = "task-list-checkbox") {
            hxPost = "/notes/$noteId/blocks/${block.blockId}/checkbox"
            hxTrigger = "change"
            hxVals = "js:{check: this.checked}"
            hxSwap = "none"

            checked = block.checked
            attributes["aria-label"] = "Mark task as completed"
        }

        textArea(classes = "text-block text-content task-list-text") {
            id = "block-input-${block.blockId}"
            this.autoFocus = autoFocus
            rows = "1"
            name = "text"
            hxPost = "/notes/$noteId/blocks/${block.blockId}/text"
            hxTrigger = "input changed delay:1s"
            hxSwap = "none"

            attributes["data-note-id"] = noteId.toString()
            attributes["data-block-id"] = block.blockId.toString()

            attributes["oninput"] = "resizeTextBlock(this)"
            attributes["onkeydown"] = "handleTextBlockKeyDown(event, this)"

            +block.text
        }
    }
}

private fun TagConsumer<*>.codeNoteBlock(
    noteId: Long,
    block: NoteBlock.Code,
    autoFocus: Boolean = false,
) {
    div {
        classes = if (autoFocus) {
            setOf("code-block", "is-editing")
        } else {
            setOf("code-block")
        }
        attributes["tabindex"] = "0"
        attributes["aria-label"] = "Code block"
        attributes["data-note-id"] = noteId.toString()
        attributes["data-block-id"] = block.blockId.toString()
        attributes["onkeydown"] = "handleCodeBlockKeyDown(event, this)"

        div(classes = "block-toolbar") {
            button(classes = "block-toolbar-action", type = ButtonType.button) {
                attributes["onclick"] =
                    "setClipboard(this.closest('.code-block').querySelector('.code-block-preview code'))"
                +"Copy"
            }
            button(classes = "block-toolbar-action", type = ButtonType.button) {
                attributes["onclick"] = "editCodeBlock(this.closest('.code-block'))"
                +"Edit"
            }
        }

        pre(classes = "code-block-preview") {
            code { +block.text }
        }
        textArea(classes = "code-block-editor") {
            id = "block-input-${block.blockId}"
            name = "text"
            rows = "3"
            attributes["aria-label"] = "Code"
            attributes["spellcheck"] = "false"
            attributes["autocapitalize"] = "off"
            attributes["autocomplete"] = "off"
            hxPost = "/notes/$noteId/blocks/${block.blockId}/text"
            hxTrigger = "input changed delay:1s, change"
            hxSwap = "none"
            attributes["oninput"] = "resizeTextBlock(this)"
            attributes["onblur"] = "previewCodeBlock(this)"
            +block.text
        }
    }
}

private fun TagConsumer<*>.textNoteBlock(
    noteId: Long,
    block: NoteBlock.Text,
    autoFocus: Boolean = false,
) {
    val blockClass = when (block) {
        is NoteBlock.Text.Content -> "text-block text-content"
        is NoteBlock.Text.H1 -> "text-block text-t1"
        is NoteBlock.Text.H2 -> "text-block text-t2"
        is NoteBlock.Text.H3 -> "text-block text-t3"
        is NoteBlock.Text.H4 -> "text-block text-t4"
    }
    textArea(classes = blockClass) {
        id = "block-input-${block.blockId}"
        this.autoFocus = autoFocus

        rows = "1"
        name = "text"
        hxPost = "/notes/$noteId/blocks/${block.blockId}/text"
        hxTrigger = "input changed delay:1s"
        hxSwap = "none"

        attributes["data-note-id"] = noteId.toString()
        attributes["data-block-id"] = block.blockId.toString()

        attributes["oninput"] = "resizeTextBlock(this)"
        attributes["onkeydown"] = "handleTextBlockKeyDown(event, this)"

        +block.text
    }
}

private val blockTypes = listOf(
    "h1",
    "h2",
    "h3",
    "h4",
    "text",
    "image",
    "task_list_item",
)

private fun TagConsumer<*>.noteItemOptionsMenu(noteId: Long, isInFolder: Boolean) {
    details(classes = "block-menu") {
        summary(classes = "block-menu-trigger") {
            attributes["aria-label"] = "Note options"
            +"+"
        }

        div(classes = "block-menu-popup") {
            button(classes = "block-menu-item") {
                +"Export"
            }
            button(classes = "block-menu-item") {
                hxDelete = "/notes/${noteId}"
                hxSwap = "delete"
                hxTarget = "#$noteId"
                hxInclude = "#current-note-id"

                +"Delete"
            }
            if (isInFolder) {
                button(classes = "block-menu-item") {
                    +"Delete from folder"
                }
            }
        }
    }
}

private fun TagConsumer<*>.addBlockMenu(
    blockContainerId: String,
    noteId: Long,
    blockId: Long
) {
    details(classes = "block-menu") {
        summary(classes = "block-menu-trigger") {
            attributes["aria-label"] = "Add block"
            +"+"
        }

        div(classes = "block-menu-popup") {
            blockTypes.forEach { item ->
                button(classes = "block-menu-item") {
                    hxPost = "/notes/$noteId/blocks/$blockId/after?type=$item"

                    hxTarget = "#$blockContainerId"
                    hxSwap = "afterend"
                    attributes["onclick"] = "closeBlockMenu(this)"

                    when (item) {
                        "h1" -> +"H1"
                        "h2" -> +"H2"
                        "h3" -> +"H3"
                        "h4" -> +"H4"
                        "text" -> +"Text"
                        "image" -> +"Image"
                        "task_list_item" -> +"TODO List"
                    }
                }
            }
        }
    }
}
