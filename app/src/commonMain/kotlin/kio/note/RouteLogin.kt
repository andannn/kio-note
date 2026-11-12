package kio.note

import io.ktor.http.*
import kio.http.*
import kio.note.domain.Repository
import kio.note.page.noteLoginPage
import kio.note.page.noteRegisterPage
import kotlinx.html.div

context(_: Repository)
fun Route.notesLogin() {
    route("register") {
        get { call -> call.noteRegisterPage() }
        post { call -> call.handleRegister() }
    }

    post("logout") { call -> call.handleLogout() }

    route("login") {
        get { call -> call.noteLoginPage() }
        post { call -> call.handleLogin() }
    }
}

context(repo: Repository)
private suspend fun CallContext.handleRegister() {
    val params = receiveFormParameters()
    val username = params["username"]
    val password = params["password"]
    val confirmPw = params["confirmPassword"]

    if (username.isNullOrEmpty() || password.isNullOrEmpty() || confirmPw.isNullOrEmpty()) {
        respondError("Please fill in all fields.")
        return
    }

    if (password != confirmPw) {
        respondError("Passwords do not match.")
        return
    }

    val user = repo.createUser(username, password)
    if (user == null) {
        respondError("This username is already taken.")
        return
    }

    val sessionId = repo.createSession(user.id)

    respond(
        HttpStatusCode.OK,
        configHeaders = {
            append("HX-Redirect", "/")
            appendCookie(
                sessionCookie(sessionId)
            )
        }
    )
}

context(repo: Repository)
private suspend fun CallContext.handleLogin() {
    val params = receiveFormParameters()

    val username = params["username"]
    val password = params["password"]

    if (username.isNullOrBlank() || password.isNullOrBlank()) {
        respondError("Username and password are required.")
        return
    }

    val user = repo.findUserByUsername(username)

    if (user == null || !repo.verifyPassword(user, password)) {
        currentLogger().info("Invalid username or password: user=$user, username=$username, password=$password")
        respondError("Invalid username or password.")
        return
    }

    val sessionId = repo.createSession(user.id)

    respond(
        HttpStatusCode.OK,
        configHeaders = {
            append("HX-Redirect", "/")
            appendCookie(
                sessionCookie(sessionId)
            )
        }
    )
}

context(repo: Repository)
private suspend fun CallContext.handleLogout() {
    requestCookies()["session"]?.let { repo.deleteSession(it) }
    respond(HttpStatusCode.OK, configHeaders = {
        appendCookie(sessionCookie("").copy(maxAge = 0))
        append("HX-Redirect", "/login")
    })
}

private suspend fun CallContext.respondError(message: String) {
    respondHtml {
        div {
            +message
        }
    }
}


