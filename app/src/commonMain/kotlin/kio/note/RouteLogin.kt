package kio.note

import io.ktor.http.*
import kio.http.*
import kio.note.domain.Repository
import kio.note.page.noteLoginPage
import kotlinx.html.div

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


