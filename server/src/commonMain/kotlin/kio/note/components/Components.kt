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
    h1 { +"Knote" }

    val noteListId = "note-list-items"

    button {
        hxPost = "/notes"
        hxTarget = "#$noteListId"
        hxSwap = "afterbegin"
        attributes["hx-on:click"] =
            """
            document.querySelectorAll('.note-item.selected')
                .forEach(it => it.classList.remove('selected'))
            """.trimIndent()
        +"New note"
    }

    section {
        hxGet = "/notes"
        hxTrigger = "load"
        hxTarget = "#$noteListId"
        hxSwap = "innerHTML"

        ul {
            id = noteListId
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

        button {
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

        button {
            hxDelete = "/notes/${note.id}"
            hxSwap = "delete"
            hxTarget = "#$noteId"
            hxInclude = "#current-note-id"

            +"Delete"
        }
    }
}

fun TagConsumer<*>.noteContent(note: Note) {
    article {
        hiddenInput {
            id = "current-note-id"
            name = "currentNoteId"
            value = note.id.toString()
        }

        input {
            name = "title"
            value = note.title

            hxPatch = "/notes/${note.id}/title"
            hxTrigger = "input changed delay:500ms"
            hxSwap = "none"
        }

        note.blocks.forEach { block ->
            noteBlock(note.id, block)
        }
    }
}

fun TagConsumer<*>.noteBlock(noteId: Long, block: NoteBlock, isNewAdded: Boolean = false) {
    div {
        val blockContainerId = "block-${block.blockId}"
        id = blockContainerId
        when (block) {
            is NoteBlock.Text -> {
                textNoteBlock(blockContainerId, noteId, block, isNewAdded)
            }

            is NoteBlock.Image -> {
                imageNoteBlock(blockContainerId, noteId, block)
            }
        }

        addBlockMenu(blockContainerId, noteId, block.blockId)
    }
}


private fun TagConsumer<*>.imageNoteBlock(
    blockContainerId: String,
    noteId: Long,
    block: NoteBlock.Image
) {
    if (block.url == null) {
        input {
            type = InputType.file
            name = "image"
            accept = "image/*"

            hxPost = "/notes/$noteId/blocks/${block.blockId}/image"
            hxTrigger = "change"
            hxTarget = "#block-${block.blockId}"
            hxSwap = "outerHTML"

            attributes["hx-encoding"] = "multipart/form-data"
        }
    } else {
        img(
            src = block.url,
        )

        input {
            type = InputType.file
            name = "image"
            accept = "image/*"

            hxPost = "/notes/$noteId/blocks/${block.blockId}/image"
            hxTrigger = "change"
            hxTarget = "#$blockContainerId"
            hxSwap = "outerHTML"

            attributes["hx-encoding"] = "multipart/form-data"
        }

        button {
            hxDelete = "/notes/$noteId/blocks/${block.blockId}"
            hxTarget = "#$blockContainerId"
            hxSwap = "delete"

            +"Delete block"
        }
    }
}

private fun TagConsumer<*>.textNoteBlock(
    blockContainerId: String,
    noteId: Long,
    block: NoteBlock.Text,
    autoFocus: Boolean = false,
) {
    p {
        val textAreaId = "block-input-${block.blockId}"

        textArea {
            id = textAreaId
            this.autoFocus = autoFocus
            name = "text"
            hxPost = "/notes/$noteId/blocks/${block.blockId}/text"
            hxTrigger = "input changed delay:1s"
            hxSwap = "none"

            attributes["hx-on:keydown"] = """
                if (event.key === 'Backspace' && this.value === '') {
                    event.preventDefault()
                    console.log(event.key)
                    
                    const currentBlock = document.querySelector('#$blockContainerId')
                    const previousBlock = currentBlock.previousElementSibling

                   htmx.ajax(
                        'DELETE',
                        '/notes/$noteId/blocks/${block.blockId}',
                        {
                            target: '#$blockContainerId',
                            swap: 'delete'
                        }
                    ).then(() => {
                        previousBlock?.querySelector('textarea')?.focus()
                    })
                }

                if (event.key === 'Enter' && !event.shiftKey) {
                    event.preventDefault()
                    htmx.ajax(
                        'POST',
                        '/notes/$noteId/blocks/${block.blockId}/after?type=text',
                        {
                            target: '#$blockContainerId',
                            swap: 'afterend'
                        }
                    )
                }

                if (
                    event.key === 'ArrowUp' &&
                    this.selectionStart === 0
                ) {
                    const currentBlock = document.querySelector('#$blockContainerId')
                    const previousInput =
                        currentBlock.previousElementSibling?.querySelector('textarea')
            
                    if (previousInput) {
                        event.preventDefault()
                        previousInput.focus()
                        previousInput.selectionStart = previousInput.value.length
                        previousInput.selectionEnd = previousInput.value.length
                    }
                }
            
                if (
                    event.key === 'ArrowDown' &&
                    this.selectionStart === this.value.length
                ) {
                    const currentBlock = document.querySelector('#$blockContainerId')
                    const nextInput =
                        currentBlock.nextElementSibling?.querySelector('textarea')
            
                    if (nextInput) {
                        event.preventDefault()
                        nextInput.focus()
                        nextInput.selectionStart = 0
                        nextInput.selectionEnd = 0
                    }
                }
            """.trimIndent()

            +block.text
        }
    }
}

private fun TagConsumer<*>.addBlockMenu(
    blockContainerId: String,
    noteId: Long,
    blockId: Long
) {
    details {
        summary {
            +"+"
        }

        button {
            hxPost = "/notes/$noteId/blocks/${blockId}/after?type=text"
            hxTarget = "#$blockContainerId"
            hxSwap = "afterend"
            +"Text"
        }

        button {
            hxPost = "/notes/$noteId/blocks/${blockId}/after?type=image"
            hxTarget = "#$blockContainerId"
            hxSwap = "afterend"
            +"Image"
        }
    }
}
