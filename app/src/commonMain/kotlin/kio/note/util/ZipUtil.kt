package kio.note.util

import kio.async.buffered
import kio.async.writeString
import kio.compression.WriteZipScope
import kio.compression.file
import kio.note.domain.Note
import kio.note.domain.NoteBlock
import kio.note.domain.imageFileName
import kio.note.domain.imageUrlToLocalPath
import kotlinx.coroutines.flow.Flow

fun validName(source: String): String {
    return source
        .replace(Regex("""[<>:"/\\|?*\s\x00-\x1F]+"""), "-")
        .trim('-')
        .trimEnd('.')
        .ifEmpty { "untitled" }
}

suspend fun WriteZipScope.writeMarkdownFolder(parentFolder: String?, note: Note, blocksGetter: suspend (noteId: Long) -> Flow<NoteBlock>) {
    val folderName = validName(note.title)

    val folderPath = if (parentFolder == null) {
        folderName
    } else {
        "$parentFolder/$folderName"
    }
    val imageUrls = mutableListOf<String>()
    file("${folderPath}/content.md") { zipSink ->
        blocksGetter(note.id).collect { block ->
            val markdownBlock = when (block) {
                is NoteBlock.Image -> {
                    if (block.url != null) {
                        imageUrls.add(block.url)
                        "![${block.blockId}](./${imageFileName(block.url)})"
                    } else {
                        ""
                    }
                }
                is NoteBlock.TaskListItem -> """- [${if(block.checked) "x" else " " }] ${block.text}"""
                is NoteBlock.Text.Content -> block.text
                is NoteBlock.Text.H1 -> "# ${block.text}"
                is NoteBlock.Text.H2 -> "## ${block.text}"
                is NoteBlock.Text.H3 -> "### ${block.text}"
                is NoteBlock.Text.H4 -> "#### ${block.text}"
                is NoteBlock.Code -> """
```
${block.text}
```
                        """.trimIndent()
            }

            zipSink.writeString(markdownBlock)
            zipSink.writeString("\n\n")
        }
    }

    imageUrls.forEach { url ->
        val fileName = imageFileName(url)
        val localPath = imageUrlToLocalPath(url)
        file("${folderPath}/${fileName}") { sink ->
            val source = fileSource(localPath).buffered()
            sink.transferFrom(source)
        }
    }
}