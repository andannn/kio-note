package kio.note

import io.ktor.http.HttpStatusCode
import kio.http.CallContext
import kio.http.CallInterceptor
import kio.http.requestCookies
import kio.http.respond
import kio.note.domain.Repository
import kio.note.domain.Session
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

suspend fun requireSession(): Session =
    checkNotNull(currentCoroutineContext()[CoroutineSession]?.session)

context(repo: Repository)
fun AuthSession() = CallInterceptor { call, proceed ->
    val session = call.getCurrentSession()
    if (session == null) {
        call.respond(
            HttpStatusCode.Found,
            configHeaders = {
                append("Location", "/login")
            }
        )
    } else {
        withContext(CoroutineSession(session)) {
            proceed(call)
        }
    }
}

private data class CoroutineSession(
    val session: Session
) : AbstractCoroutineContextElement(CoroutineSession) {
    companion object Key : CoroutineContext.Key<CoroutineSession>

    override fun toString(): String = "CoroutineSession(${session})"
}

context(repo: Repository)
private suspend fun CallContext.getCurrentSession(): Session? {
    val sessionId = requestCookies()["session"] ?: return null
    return repo.getSessionById(sessionId)
}