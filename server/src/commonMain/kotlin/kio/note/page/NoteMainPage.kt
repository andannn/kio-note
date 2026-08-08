package kio.note.page

import kio.http.CallContext
import kio.http.respondHtml
import kio.note.components.noteList
import kio.note.domain.Repository
import kio.note.util.Config
import kotlinx.html.*

context(repository: Repository)
suspend fun CallContext.noteMainPage() {
    val notes = repository.getAllNoteMetaData()
    respondHtml {
        head {
            script(src = Config.HTMX_MIN_JS) { }
            link(rel = "stylesheet", href = Config.APP_CSS)
            title { +"Knote" }
        }

        body {
            style = "display: flex; min-height: 100vh;"
            aside {
                style = "width: 280px;"

                h1 { +"Knote" }
                noteList(notes)
            }

            main(classes = "page") {
                style = "flex: 1;"
                id = "note-content"

                p { +"Select a note" }
            }
        }
        footer { }
    }
}
