package com.jing.sakura.update

internal enum class TvReleaseNoteKind {
    Heading,
    Bullet,
    Paragraph
}

internal data class TvReleaseNoteItem(
    val kind: TvReleaseNoteKind,
    val text: String
)

internal fun parseTvReleaseNotes(markdown: String): List<TvReleaseNoteItem> {
    val items = mutableListOf<TvReleaseNoteItem>()
    markdown.lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        if (line.isBlank() || line.matches(HORIZONTAL_RULE)) return@forEach

        val heading = HEADING.matchEntire(line)
        val bullet = BULLET.matchEntire(line)
        val ordered = ORDERED.matchEntire(line)
        when {
            heading != null -> items += TvReleaseNoteItem(
                TvReleaseNoteKind.Heading,
                cleanInlineMarkdown(heading.groupValues[1])
            )

            bullet != null -> items += TvReleaseNoteItem(
                TvReleaseNoteKind.Bullet,
                cleanInlineMarkdown(bullet.groupValues[1])
            )

            ordered != null -> items += TvReleaseNoteItem(
                TvReleaseNoteKind.Bullet,
                cleanInlineMarkdown(ordered.groupValues[1])
            )

            else -> items += TvReleaseNoteItem(
                TvReleaseNoteKind.Paragraph,
                cleanInlineMarkdown(line.removePrefix("> ").removePrefix(">"))
            )
        }
    }
    return items.filter { it.text.isNotBlank() }
}

private fun cleanInlineMarkdown(value: String): String = value
    .replace(IMAGE_LINK) { match -> match.groupValues[1] }
    .replace(MARKDOWN_LINK) { match -> match.groupValues[1] }
    .replace(INLINE_MARKER, "")
    .replace(CHECKBOX, "")
    .trim()

private val HEADING = Regex("#{1,6}\\s+(.+)")
private val BULLET = Regex("[-*+]\\s+(.+)")
private val ORDERED = Regex("\\d+[.)]\\s+(.+)")
private val HORIZONTAL_RULE = Regex("(?:[-*_]\\s*){3,}")
private val MARKDOWN_LINK = Regex("\\[([^]]+)]\\([^)]+\\)")
private val IMAGE_LINK = Regex("!\\[([^]]*)]\\([^)]+\\)")
private val INLINE_MARKER = Regex("(?:\\*\\*|__|~~|`)")
private val CHECKBOX = Regex("^\\[[ xX]]\\s*")
