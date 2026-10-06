package kio.note.page

import kio.http.CallContext
import kio.http.respondHtml
import kotlinx.html.TagConsumer
import kotlinx.html.body
import kotlinx.html.div

suspend fun CallContext.note404Page() {
    respondHtml {
        kNoteHead("Not Found - Knote")

        body(classes = "app") {
            note404()
        }
    }
}

fun TagConsumer<*>.note404() {
    div {
        +"Not found"
    }
}
