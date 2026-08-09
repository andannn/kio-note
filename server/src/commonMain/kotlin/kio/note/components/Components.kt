package kio.note.components

import kio.note.domain.Note
import kio.note.domain.NoteBlock
import kio.note.util.hxDelete
import kio.note.util.hxGet
import kio.note.util.hxPost
import kio.note.util.hxSwap
import kio.note.util.hxTarget
import kio.note.util.hxTrigger
import kotlinx.html.*

fun TagConsumer<*>.noteList(notes: List<Note>) {
    section {
        id = "note-list"
        classes = setOf("note-list")

        if (notes.isEmpty()) {
            p(classes = "note-list__empty") {
                +"No notes yet."
            }
        } else {
            ul {
                notes.forEach { note ->
                    li {
                        noteItem(note)
                    }
                }
            }
        }
    }
}

private fun TagConsumer<*>.noteItem(note: Note) {
    button {
        hxGet = "/notes/${note.id}"
        hxTarget = "#note-content"
        hxSwap = "innerHTML"

        +note.title
    }
}

fun TagConsumer<*>.noteContent(note: Note) {
    article {
        h1 {
            +note.title
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


private fun TagConsumer<*>.imageNoteBlock(blockContainerId: String, noteId: Long, block: NoteBlock.Image) {
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
            alt = block.alt,
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
