package com.jing.sakura.compose.screen

internal data class DetailTitleLayout(
    val fontSizeSp: Int,
    val lineHeightSp: Int,
    val maxLines: Int,
    val descriptionMaxLines: Int
)

internal object DetailTitleLayoutPolicy {
    fun forTitle(title: String): DetailTitleLayout {
        val length = title.codePointCount(0, title.length)
        return when {
            length <= 18 -> DetailTitleLayout(37, 43, 2, 3)
            length <= 30 -> DetailTitleLayout(34, 39, 2, 3)
            length <= 46 -> DetailTitleLayout(29, 34, 3, 2)
            length <= 66 -> DetailTitleLayout(25, 30, 3, 2)
            else -> DetailTitleLayout(22, 27, 4, 1)
        }
    }
}
