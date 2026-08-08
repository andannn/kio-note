package kio.note

import kio.async.io.ServerSocket
import kio.http.get
import kio.http.httpServer
import kio.http.staticResource
import kio.note.domain.Repository
import kio.note.page.noteMainPage

suspend fun noteServer(serverSocket: ServerSocket) {
    val repo = Repository()
    with(repo) {
        httpServer(serverSocket) {
            get("/") { call -> call.noteMainPage() }
            notesRoute()
            staticResource("/", "resource")
        }
    }
}
