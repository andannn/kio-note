package kio.note.page

import kio.http.CallContext
import kio.http.respondHtml
import kio.note.components.noteList
import kio.note.components.noteTopForm
import kio.note.domain.model.Note
import kio.note.util.Resource
import kotlinx.html.body
import kotlinx.html.footer
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.script
import kotlinx.html.title

suspend fun CallContext.noteMainPage(notes: List<Note>) {
    respondHtml {
        head {
            script(src = Resource.HTMX_MIN_JS) { }
            link(rel = "stylesheet", href = Resource.APP_CSS)
            title { +"Knote" }
        }

        body {
            main(classes = "page") {
                h1 { +"Knote" }
                noteTopForm()
                noteList(notes)
            }
        }
        footer { }
    }
}
