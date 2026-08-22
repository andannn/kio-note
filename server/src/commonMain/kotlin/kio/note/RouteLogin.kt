package kio.note

import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import kio.async.AsyncRawSource
import kio.http.CallContext
import kio.http.Route
import kio.http.appendCookie
import kio.http.currentLogger
import kio.http.currentLoggerOrNull
import kio.http.delete
import kio.http.get
import kio.http.info
import kio.http.patch
import kio.http.post
import kio.http.receiveFormParameters
import kio.http.receiveMultipart
import kio.http.respond
import kio.http.respondHtml
import kio.http.respondText
import kio.http.route
import kio.note.components.noteBlock
import kio.note.components.noteContent
import kio.note.components.noteItem
import kio.note.components.noteList
import kio.note.components.noteMainContentEmpty
import kio.note.domain.BlockType
import kio.note.domain.Repository
import kio.note.page.noteLoginPage
import kio.note.util.hxSwapOob
import kotlinx.html.div
import kotlinx.html.id

context(_: Repository)
fun Route.notesLogin() {
    route("login") {
        get { call -> call.noteLoginPage() }
        post { call -> call.handleLogin() }
    }
}

context(repo: Repository)
private suspend fun CallContext.handleLogin() {
    val params = receiveFormParameters()

    val username = params["username"]
    val password = params["password"]

    if (username.isNullOrBlank() || password.isNullOrBlank()) {
        respondLoginError("Username and password are required.")
        return
    }

    val user = repo.findUserByUsername(username)

    if (user == null || !repo.verifyPassword(user, password)) {
        currentLogger().info("Invalid username or password: user=$user, username=$username, password=$password")
        respondLoginError("Invalid username or password.")
        return
    }

    val sessionId = repo.createSession(user.id)

    respond(
        HttpStatusCode.OK,
        configHeaders = {
            append("HX-Redirect", "/")
            appendCookie(
                Cookie(
                    "session",
                    sessionId,
                    httpOnly = true,
                    path = "/",
                    secure = true,
                    extensions = mapOf("SameSite" to "Lax")
                )
            )
        }
    )
}

private suspend fun CallContext.respondLoginError(message: String) {
    respondHtml {
        div {
            +message
        }
    }
}


