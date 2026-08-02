package kio.note

import kio.async.io.ServerSocket
import kio.http.get
import kio.http.httpServer
import kio.http.staticResource
import kio.note.page.noteMainPage

suspend fun noteServer(serverSocket: ServerSocket) {
    httpServer(serverSocket) {
        get("/") { call -> call.noteMainPage(notes) }

        notesRoute()

        staticResource("/", "resource")
    }
}
