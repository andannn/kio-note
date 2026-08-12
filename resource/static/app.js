document.addEventListener('htmx:afterSwap', event => {
    resizeTextBlocks(event.target)
    focusAutoFocusBlock(event.target)
})

document.addEventListener('click', event => {
    if (event.target.closest('.block-menu')) {
        return
    }

    document.querySelectorAll('.block-menu[open]').forEach(menu => {
        menu.removeAttribute('open')
    })
})

function getBlockContainer(element) {
    return element.closest('.note-block')
}

function getBlockInput(block) {
    if (!block) {
        return null
    }

    return block.querySelector(
        '.text-block, .image-block-content'
    )
}

function focusBlock(block, cursorPosition = null) {
    const input = getBlockInput(block)

    if (!input) {
        return
    }

    input.focus()

    if (!(input instanceof HTMLTextAreaElement)) {
        return
    }

    if (cursorPosition === 'start') {
        input.selectionStart = 0
        input.selectionEnd = 0
    }

    if (cursorPosition === 'end') {
        const end = input.value.length

        input.selectionStart = end
        input.selectionEnd = end
    }
}

function focusPreviousBlock(element) {
    const currentBlock = getBlockContainer(element)

    if (!currentBlock) {
        return
    }

    focusBlock(
        currentBlock.previousElementSibling,
        'end'
    )
}

function focusNextBlock(element) {
    const currentBlock = getBlockContainer(element)

    if (!currentBlock) {
        return
    }

    focusBlock(
        currentBlock.nextElementSibling,
        'start'
    )
}

function addTextBlockAfter(element, noteId, blockId) {
    const currentBlock = getBlockContainer(element)

    if (!currentBlock) {
        return
    }

    return htmx.ajax(
        'POST',
        `/notes/${noteId}/blocks/${blockId}/after?type=text`,
        {
            target: `#${currentBlock.id}`,
            swap: 'afterend'
        }
    )
}

function deleteBlock(element, noteId, blockId) {
    const currentBlock = getBlockContainer(element)

    if (!currentBlock) {
        return
    }

    const previousBlock = currentBlock.previousElementSibling

    return htmx.ajax(
        'DELETE',
        `/notes/${noteId}/blocks/${blockId}`,
        {
            target: `#${currentBlock.id}`,
            swap: 'delete'
        }
    ).then(() => {
        focusBlock(previousBlock, 'end')
    })
}

function handleTextBlockKeyDown(event, input) {
    const noteId = input.dataset.noteId
    const blockId = input.dataset.blockId

    if (
        event.key === 'Backspace' &&
        input.value === ''
    ) {
        event.preventDefault()

        deleteBlock(
            input,
            noteId,
            blockId
        )

        return
    }

    if (
        event.key === 'Enter' && event.shiftKey
    ) {
        event.preventDefault()

        addTextBlockAfter(
            input,
            noteId,
            blockId
        )

        return
    }

    if (
        event.key === 'ArrowUp' &&
        input.selectionStart === 0
    ) {
        const currentBlock = getBlockContainer(input)

        if (currentBlock?.previousElementSibling) {
            event.preventDefault()
            focusPreviousBlock(input)
        }

        return
    }

    if (
        event.key === 'ArrowDown' &&
        input.selectionStart === input.value.length
    ) {
        const currentBlock = getBlockContainer(input)

        if (currentBlock?.nextElementSibling) {
            event.preventDefault()
            focusNextBlock(input)
        }
    }
}

function handleImageBlockKeyDown(event, input) {
    const noteId = input.dataset.noteId
    const blockId = input.dataset.blockId

    if (event.key === 'Enter') {
        event.preventDefault()

        addTextBlockAfter(
            input,
            noteId,
            blockId
        )

        return
    }

    if (event.key === 'ArrowUp') {
        const currentBlock = getBlockContainer(input)

        if (currentBlock?.previousElementSibling) {
            event.preventDefault()
            focusPreviousBlock(input)
        }

        return
    }

    if (event.key === 'ArrowDown') {
        const currentBlock = getBlockContainer(input)

        if (currentBlock?.nextElementSibling) {
            event.preventDefault()
            focusNextBlock(input)
        }
    }

    if (event.key === 'Backspace') {
        event.preventDefault()

        deleteBlock(
            input,
            noteId,
            blockId
        )
    }
}

function closeBlockMenu(element) {
    element.closest('details')?.removeAttribute('open')
}

function resizeTextBlock(textarea) {
    textarea.style.height = 'auto'
    textarea.style.height = `${textarea.scrollHeight}px`
}

function resizeTextBlocks(root = document) {
    root.querySelectorAll('.text-block').forEach(resizeTextBlock)
}

function focusAutoFocusBlock(root = document) {
    const element = root.matches?.('[data-autofocus="true"]')
        ? root
        : root.querySelector?.('[data-autofocus="true"]')

    if (!element) {
        return
    }

    element.focus()
    element.removeAttribute('data-autofocus')
}
