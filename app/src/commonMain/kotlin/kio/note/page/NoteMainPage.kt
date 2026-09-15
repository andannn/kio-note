package kio.note.page

import kio.http.CallContext
import kio.http.currentLogger
import kio.http.info
import kio.http.respondHtml
import kio.note.components.noteAsideMenu
import kio.note.components.noteContent
import kio.note.components.noteMainContentEmpty
import kio.note.util.Config
import kio.note.util.hxGet
import kio.note.util.hxPushUrl
import kio.note.util.hxSwap
import kio.note.util.hxTarget
import kio.note.util.hxTrigger
import kotlinx.coroutines.delay
import kotlinx.html.*
import kotlinx.html.head
import kotlinx.html.link
import kotlinx.html.script
import kotlinx.html.title
import kotlin.math.sign
import kotlin.time.Duration.Companion.seconds

fun TagConsumer<*>.kNoteHead(title: String) {
    head {
        script(src = Config.HTMX_MIN_JS) { }
        script(src = Config.APP_JS) { }
        link(rel = "stylesheet", href = Config.APP_CSS)
        title { +title }
    }
}

suspend fun CallContext.noteMainPage(noteId: String? = null) {
    currentLogger().info("respond Main Page: noteId=$noteId")
    respondHtml {
        kNoteHead("Knote")

        body(classes = "app") {
            aside (classes = "sidebar") {
                noteAsideMenu(noteId)
            }

            main(classes = "note-editor") {
                id = "note-content"

                if (noteId == null) {
                    noteMainContentEmpty()
                } else {
                    div {
                        hxGet = "/notes/${noteId}/editor"
                        hxTarget = "#note-content"
                        hxTrigger = "load"
                        hxSwap = "innerHTML"
                    }
                }
            }
        }
    }
}
