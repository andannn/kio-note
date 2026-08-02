package kio.note.components

import kio.note.domain.model.Note
import kio.note.util.hxConfirm
import kio.note.util.hxDelete
import kio.note.util.hxGet
import kio.note.util.hxOnAfterRequest
import kio.note.util.hxPatch
import kio.note.util.hxPost
import kio.note.util.hxSwap
import kio.note.util.hxTarget
import kotlinx.html.*

fun FlowContent.noteList(notes: List<Note>) {
    section {
        id = "note-list"
        classes = setOf("note-list")

        if (notes.isEmpty()) {
            p(classes = "note-list__empty") {
                +"No notes yet."
            }
        } else {
            notes.forEach { note ->
                noteCard(note)
            }
        }
    }
}

fun FlowContent.noteCard(note: Note) {
    article(classes = "note-card") {
        id = "note-${note.id}"

        h2(classes = "note-card__title") {
            +note.title
        }

        p(classes = "note-card__content") {
            +note.content
        }

        div(classes = "note-card__actions") {
            button(
                type = ButtonType.button,
                classes = "button button--secondary",
            ) {

                hxGet = "/notes/${note.id}/edit"
                hxTarget = "closest article"
                hxSwap = "outerHTML"

                +"Edit"
            }

            button(
                type = ButtonType.button,
                classes = "button button--danger",
            ) {
                hxDelete = "/notes/${note.id}"
                hxSwap = "delete"
                hxTarget = "#note-${note.id}"
                hxConfirm = "Delete this note?"

                +"Delete"
            }
        }
    }
}

fun FlowContent.noteTopForm() {
    form(classes = "note-form") {
        hxPost = "/notes"
        hxTarget = "#note-list"
        hxSwap = "beforeend"
        hxOnAfterRequest = "if (event.detail.successful) this.reset()"

        div(classes = "form-field") {
            label {
                htmlFor = "note-title"
                +"Title"
            }

            textInput {
                id = "note-title"
                name = "title"
                placeholder = "Note title"
                required = true
            }
        }

        div(classes = "form-field") {
            label {
                htmlFor = "note-content"
                +"Content"
            }

            textArea {
                id = "note-content"
                name = "content"
                placeholder = "Write something..."
                rows = "6"
            }
        }

        div(classes = "note-form__actions") {
            button(
                type = ButtonType.submit,
                classes = "button button--primary",
            ) {
                +"Add note"
            }
        }
    }
}

fun FlowContent.noteEditForm(note: Note) {
    form(classes = "note-form note-form--inline") {
        id = "note-${note.id}"

        hxPatch = "/notes/${note.id}"
        hxTarget = "this"
        hxSwap = "outerHTML"

        div(classes = "form-field") {
            label {
                htmlFor = "note-title-${note.id}"
                +"Title"
            }

            textInput {
                id = "note-title-${note.id}"
                name = "title"
                value = note.title
                required = true
            }
        }

        div(classes = "form-field") {
            label {
                htmlFor = "note-content-${note.id}"
                +"Content"
            }

            textArea {
                id = "note-content-${note.id}"
                name = "content"
                rows = "6"

                +note.content
            }
        }

        div(classes = "note-form__actions") {
            button(
                type = ButtonType.submit,
                classes = "button button--primary",
            ) {
                +"Save"
            }

            button(
                type = ButtonType.button,
                classes = "button button--secondary",
            ) {
                hxGet = "/notes/${note.id}"
                hxTarget = "closest form"
                hxSwap = "outerHTML"

                +"Cancel"
            }
        }
    }
}
