package kio.note.page

import kio.http.CallContext
import kio.http.currentLogger
import kio.http.info
import kio.http.respondHtml
import kio.note.components.noteAsideMenu
import kio.note.components.noteMainContentEmpty
import kio.note.util.Config
import kotlinx.html.*
import kotlinx.html.head
import kotlinx.html.link
import kotlinx.html.script
import kotlinx.html.title

fun TagConsumer<*>.kNoteHead(title: String) {
    head {
        script(src = Config.HTMX_MIN_JS) { }
        script(src = Config.APP_JS) { }
        link(rel = "stylesheet", href = Config.APP_CSS)
        title { +title }
    }
}

suspend fun CallContext.noteMainPage() {
    currentLogger().info("respond Main Page")
    respondHtml {
        kNoteHead("Knote")

        body(classes = "app") {
            aside (classes = "sidebar") {
                noteAsideMenu()
            }

            main(classes = "note-editor") {
                id = "note-content"

                noteMainContentEmpty()
            }
        }
    }
}
