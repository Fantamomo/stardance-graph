package com.fantamomo.hc.stardancegraph.util

import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

private val WS = Regex("\\s+")

fun Element.toMarkdown(): String {

    fun render(node: Node): String = when (node) {

        is TextNode ->
            node.wholeText.replace(WS, " ")

        is Element -> {
            val tag = node.tagName()
            val children = node.childNodes()
                .joinToString("") { render(it) }

            when (tag) {
                "h1" -> "# ${children.trim()}\n"
                "h2" -> "## ${children.trim()}\n"
                "h3" -> "### ${children.trim()}\n"
                "h4" -> "#### ${children.trim()}\n"
                "h5" -> "##### ${children.trim()}\n"
                "h6" -> "###### ${children.trim()}\n"

                "p" -> "${children.trim()}\n"

                "strong", "b" -> "**${children.trim()}**"
                "em", "i" -> "*${children.trim()}*"
                "del", "s", "strike" -> "~~${children.trim()}~~"

                "br" -> "  \n"
                "hr" -> "\n---\n"

                "code" ->
                    if (tag == "code" && node.parent()?.tagName() != "pre")
                        "`${node.text()}`"
                    else
                        children

                "pre" ->
                    "```\n${node.text().trim()}\n```\n"

                "blockquote" ->
                    children.trim()
                        .lineSequence()
                        .joinToString("\n") { "> $it" }

                "a" -> {
                    val href = node.attr("href")
                    val text = children.trim()
                    if (href.isBlank() || node.hasClass("mention"))
                        text
                    else
                        "[$text]($href)"
                }

                "img" -> {
                    val src = node.attr("src")
                    if (src.isBlank())
                        ""
                    else if (node.hasClass("slack-emote"))
                        node.attr("title")
                    else
                        "![]($src)"
                }

                "ul" ->
                    children.trim()
                        .lineSequence()
                        .joinToString("\n") { "- $it" }

                "ol" ->
                    children.trim()
                        .lineSequence()
                        .mapIndexed { i, s -> "${i + 1}. $s" }
                        .joinToString("\n")

                "li" ->
                    children.trim()

                "table" ->
                    renderTable(node)

                else ->
                    children
            }
        }

        else -> ""
    }

    return render(this)
        .replace("\n\n\n+".toRegex(), "\n\n")
        .trim()
}


private fun renderTable(table: Element): String {
    val rows = table.select("tr")
        .map { row ->
            row.select("th,td")
                .map { it.text().trim() }
        }

    if (rows.size < 2) return ""

    val cols = rows.first().size
    if (cols == 0) return ""

    return buildString {
        append('|')
        append(rows.first().joinToString("|"))
        append("|\n|")
        append(List(cols) { "---" }.joinToString("|"))
        append("|\n")

        rows.drop(1).forEach { row ->
            append('|')
            append(row.joinToString("|"))
            append("|\n")
        }
    }
}