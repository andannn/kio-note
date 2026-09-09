package kio.note.components

import kio.note.domain.Note
import kio.note.domain.NoteBlock
import kio.note.util.hxDelete
import kio.note.util.hxGet
import kio.note.util.hxInclude
import kio.note.util.hxPatch
import kio.note.util.hxPost
import kio.note.util.hxSwap
import kio.note.util.hxTarget
import kio.note.util.hxTrigger
import kotlinx.html.*

fun TagConsumer<*>.noteMainContentEmpty() {
    p { +"Select a note" }
}

fun TagConsumer<*>.noteAsideMenu() {

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
                +"＋"
            }

            span {
                +"New note"
            }
        }

        section(classes = "sidebar-notes") {
            hxGet = "/notes"
            hxTrigger = "load"
            hxTarget = "#$noteListId"
            hxSwap = "innerHTML"

            ul {
                id = noteListId
            }
        }
    }
}

fun TagConsumer<*>.noteList(notes: List<Note>) {
    notes.forEach { note ->
        noteItem(note)
    }
}

fun TagConsumer<*>.noteItem(note: Note, selected: Boolean = false) {
    li(classes = "note-item") {
        val noteId = "note-${note.id}"
        id = noteId

        if (selected) classes += setOf("selected")

        button(classes = "note-item-open") {
            hxGet = "/notes/${note.id}"
            hxTarget = "#note-content"
            hxSwap = "innerHTML"
            attributes["hx-on:click"] =
                """
                document.querySelectorAll('.note-item.selected')
                    .forEach(it => it.classList.remove('selected'));
                this.closest('.note-item').classList.add('selected');
                """.trimIndent()
            +note.title
        }

        button(classes = "note-item-delete") {
            hxDelete = "/notes/${note.id}"
            hxSwap = "delete"
            hxTarget = "#$noteId"
            hxInclude = "#current-note-id"

            +"×"
        }
    }
}

fun TagConsumer<*>.noteContent(note: Note) {
    article(classes = "note-document") {
        hiddenInput {
            id = "current-note-id"
            name = "currentNoteId"
            value = note.id.toString()
        }

        input(classes = "note-title") {
            name = "title"
            value = note.title

            hxPatch = "/notes/${note.id}/title"
            hxTrigger = "input changed delay:500ms"
            hxSwap = "none"
        }

        div(classes = "note-blocks") {
            note.blocks.forEach { block ->
                noteBlock(note.id, block)
            }
        }
    }
}

fun TagConsumer<*>.noteBlock(noteId: Long, block: NoteBlock, isNewAdded: Boolean = false) {
    div(classes = "note-block") {
        val blockContainerId = "block-${block.blockId}"
        id = blockContainerId

        when (block) {
            is NoteBlock.Text -> {
                textNoteBlock(blockContainerId, noteId, block, isNewAdded)
            }

            is NoteBlock.Image -> {
                imageNoteBlock(blockContainerId, noteId, block, isNewAdded)
            }
        }

        addBlockMenu(blockContainerId, noteId, block.blockId)
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

        attributes["onkeydown"] =
            "handleImageBlockKeyDown(event, this)"

        if (autoFocus) {
            attributes["data-autofocus"] = "true"
        }

        if (block.url == null) {
            label(classes = "image-upload") {
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

        div(classes = "image-block-actions") {
            if (block.url != null) {
                label(classes = "image-block-action") {
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

            button(classes = "image-block-action") {
                hxDelete = "/notes/$noteId/blocks/${block.blockId}"
                hxTarget = "#$blockContainerId"
                hxSwap = "delete"

                +"Delete"
            }
        }
    }
}

private fun TagConsumer<*>.textNoteBlock(
    blockContainerId: String,
    noteId: Long,
    block: NoteBlock.Text,
    autoFocus: Boolean = false,
) {
    val textAreaId = "block-input-${block.blockId}"
    val blockClass = when (block) {
        is NoteBlock.Text.Content -> "text-block text-content"
        is NoteBlock.Text.H1 -> "text-block text-t1"
        is NoteBlock.Text.H2 -> "text-block text-t2"
        is NoteBlock.Text.H3 -> "text-block text-t3"
        is NoteBlock.Text.H4 -> "text-block text-t4"
    }
    textArea(classes = blockClass) {
        id = textAreaId
        this.autoFocus = autoFocus

        rows = "1"
        name = "text"
        hxPost = "/notes/$noteId/blocks/${block.blockId}/text"
        hxTrigger = "input changed delay:1s"
        hxSwap = "none"

        attributes["data-note-id"] = noteId.toString()
        attributes["data-block-id"] = block.blockId.toString()
        attributes["data-block-container-id"] = blockContainerId

        attributes["oninput"] = "resizeTextBlock(this)"
        attributes["onkeydown"] = "handleTextBlockKeyDown(event, this)"

        +block.text
    }
}

private val blockMenu = listOf(
    "h1",
    "h2",
    "h3",
    "h4",
    "text",
    "image",
)

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
            blockMenu.forEach { item ->
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
                    }
                }
            }
        }
    }
}
