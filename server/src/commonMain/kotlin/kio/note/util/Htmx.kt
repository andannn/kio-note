package kio.note.util

import kotlinx.html.*

var Tag.hxGet: String
    get() = attributes["hx-get"].orEmpty()
    set(value) {
        attributes["hx-get"] = value
    }

var Tag.hxPost: String
    get() = attributes["hx-post"].orEmpty()
    set(value) {
        attributes["hx-post"] = value
    }

var Tag.hxTrigger: String
    get() = attributes["hx-trigger"].orEmpty()
    set(value) {
        attributes["hx-trigger"] = value
    }

var Tag.hxDelete: String
    get() = attributes["hx-delete"].orEmpty()
    set(value) {
        attributes["hx-delete"] = value
    }

var Tag.hxTarget: String
    get() = attributes["hx-target"].orEmpty()
    set(value) {
        attributes["hx-target"] = value
    }

var Tag.hxPatch: String
    get() = attributes["hx-patch"].orEmpty()
    set(value) {
        attributes["hx-patch"] = value
    }

var Tag.hxConfirm: String
    get() = attributes["hx-confirm"].orEmpty()
    set(value) {
        attributes["hx-confirm"] = value
    }

var Tag.hxSwap: String
    get() = attributes["hx-swap"].orEmpty()
    set(value) {
        attributes["hx-swap"] = value
    }

var Tag.hxOnAfterRequest: String
    get() = attributes["hx-on::after-request"].orEmpty()
    set(value) {
        attributes["hx-on::after-request"] = value
    }
