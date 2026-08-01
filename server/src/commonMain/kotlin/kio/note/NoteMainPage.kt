package kio.note

import kio.note.components.noteForm
import kio.note.components.noteList
import kio.note.domain.Note
import kotlinx.html.*

fun TagConsumer<*>.noteMainPage() {
    head {
        script(src = "/static/htmx.min.js") { }
        link(
            rel = "stylesheet",
            href = "/static/css/app.css",
        )
        title { +"Knote" }
    }

    body {
        main(classes = "page") {
            h1 { +"Knote" }
            noteForm()
            noteList(
                listOf(
                    Note(
                        id = 1,
                        title = "Learn HTML",
                        content = "Today I learned HTML components.",
                    ),
                    Note(
                        id = 1,
                        title = "Learn HTML",
                        content = "Today I learned HTML components.",
                    ),
                )
            )
        }
    }
    footer { }
}
