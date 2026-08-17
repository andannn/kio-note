package kio.note.page

import kio.http.CallContext
import kio.http.respondHtml
import kio.note.util.hxPost
import kio.note.util.hxSwap
import kio.note.util.hxTarget
import kotlinx.html.*

suspend fun CallContext.noteLoginPage() {
    respondHtml {
        kNoteHead("Login - Knote")

        body(classes = "app login-body") {
            noteLogin()
        }
    }
}

fun TagConsumer<*>.noteLogin() {
    main(classes = "login-page") {
        section(classes = "login-panel") {
            div(classes = "login-header") {
                h1(classes = "login-title") {
                    +"Knote"
                }

                p(classes = "login-subtitle") {
                    +"Sign in to continue"
                }
            }

            form(classes = "login-form") {
                hxPost = "/login"
                hxTarget = "#login-result"
                hxSwap = "innerHTML"

                div(classes = "login-field") {
                    label {
                        htmlFor = "username"
                        +"Username"
                    }

                    input(
                        type = InputType.text,
                        name = "username",
                        classes = "login-input",
                    ) {
                        id = "username"
                        autoComplete = "username"
                        autoFocus = true
                        required = true
                    }
                }

                div(classes = "login-field") {
                    label {
                        htmlFor = "password"
                        +"Password"
                    }

                    input(
                        type = InputType.password,
                        name = "password",
                        classes = "login-input",
                    ) {
                        id = "password"
                        autoComplete = "current-password"
                        required = true
                    }
                }

                div {
                    id = "login-result"
                }

                button(
                    type = ButtonType.submit,
                    classes = "login-submit",
                ) {
                    +"Sign in"
                }
            }
        }
    }
}
