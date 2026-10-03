package kio.note.page

import kio.http.CallContext
import kio.http.respondHtml
import kio.note.util.hxPost
import kio.note.util.hxSwap
import kio.note.util.hxTarget
import kotlinx.html.*

suspend fun CallContext.noteRegisterPage() {
    respondHtml {
        kNoteHead("Register - Knote")

        body(classes = "app login-body register-body") {
            noteRegister()
        }
    }
}

fun TagConsumer<*>.noteRegister() {
    main(classes = "login-page") {
        section(classes = "login-panel") {
            div(classes = "login-header") {
                h1(classes = "login-title") {
                    +"Create an account"
                }
                p(classes = "login-subtitle") {
                    +"Start taking notes with Knote"
                }
            }

            form(classes = "login-form") {
                hxPost = "/register"
                hxTarget = "#register-result"
                hxSwap = "innerHTML"

                div(classes = "login-field") {
                    label {
                        htmlFor = "register-username"
                        +"Username"
                    }
                    input(
                        type = InputType.text,
                        name = "username",
                        classes = "login-input",
                    ) {
                        id = "register-username"
                        autoComplete = "username"
                        autoFocus = true
                        required = true
                        attributes["autocapitalize"] = "none"
                        attributes["spellcheck"] = "false"
                    }
                }

                div(classes = "login-field") {
                    label {
                        htmlFor = "register-password"
                        +"Password"
                    }
                    input(
                        type = InputType.password,
                        name = "password",
                        classes = "login-input",
                    ) {
                        id = "register-password"
                        autoComplete = "new-password"
                        required = true
                    }
                }

                div(classes = "login-field") {
                    label {
                        htmlFor = "register-confirm-password"
                        +"Confirm password"
                    }
                    input(
                        type = InputType.password,
                        name = "confirmPassword",
                        classes = "login-input",
                    ) {
                        id = "register-confirm-password"
                        autoComplete = "new-password"
                        required = true
                    }
                }

                div(classes = "register-result") {
                    id = "register-result"
                    attributes["role"] = "status"
                    attributes["aria-live"] = "polite"
                }

                button(
                    type = ButtonType.submit,
                    classes = "login-submit",
                ) {
                    +"Create account"
                }
            }

            p(classes = "auth-footer") {
                +"Already have an account? "
                a(href = "/login") {
                    +"Sign in"
                }
            }
        }
    }
}