package com.fantamomo.hc.stardancegraph.scrapen.site

import com.fantamomo.hc.stardancegraph.model.*
import com.fantamomo.hc.stardancegraph.util.Logger
import io.ktor.http.*
import org.jsoup.nodes.Element

object PostParser {
    private val logger = Logger()

    fun parse(html: Element, url: Url): Post? {
        val type = html.attr("data-feed-engagement-post-type-value")
        if (type.isBlank()) {
            logger.warn("Failed to find post type in ${html.cssSelector()} from $url")
            return null
        }

        val projectId = html.attr("data-feed-engagement-project-id-value").toIntOrNull()
        if (projectId == null) {
            logger.warn("Failed to find project ID in ${html.cssSelector()} from $url")
            return null
        }

        val internalId = html.attr("data-feed-engagement-post-id-value").toIntOrNull()
        if (internalId == null) {
            logger.warn("Failed to find internal ID in ${html.cssSelector()} from $url")
            return null
        }

        val authorElement = html.selectFirst(".feed-post-card__author")
        if (authorElement == null) {
            logger.warn("Failed to find author element in ${html.cssSelector()} from $url")
            return null
        }
        val author = authorElement.text()
        if (author.isBlank()) {
            logger.warn("Failed to find author in ${html.cssSelector()} from $url")
            return null
        }

        return when (type) {
            "Post::Repost" -> parseRepost(html, url, projectId, internalId, author)
            "Post::Devlog" -> {
                // parseDevlog(html, url) // if it would be that easy, a repost without a message is actually a devlog with another author and another id
                parseDevlogOrRepost(html, url, projectId, internalId, author)
            }

            "Post::ShipEvent" -> parseShipEvent(html, url, projectId, internalId, author)
            "Post::FireEvent" -> parseSuperStar(html, url, projectId, internalId, author)
            else -> {
                logger.warn("Unknown post type $type in ${html.cssSelector()} from $url")
                null
            }
        }
    }

    private fun parseDevlogOrRepost(html: Element, url: Url, projectId: Int, internalId: Int, author: String): Post? {
        if (html.selectFirst(".feed-post-card__reposted-by") != null) {
            return parseRepost(html, url, projectId, internalId, author)
        }
        return parseDevlog(html, url, projectId, internalId, author)
    }

    private fun parseRepost(html: Element, url: Url, projectId: Int, internalId: Int, author: String): Repost? {
        val repostedByElement = html.selectFirst(".feed-post-card__reposted-by-link")
        if (repostedByElement == null) {
            logger.warn("Failed to find reposted by element in ${html.cssSelector()} from $url")
            return null
        }
        val repostedBy = repostedByElement.text().removePrefix("@")
        if (repostedBy.isBlank()) {
            logger.warn("Failed to find reposted by in ${html.cssSelector()} from $url")
            return null
        }
        TODO()
    }

    private fun parseDevlog(html: Element, url: Url, projectId: Int, internalId: Int, author: String): Devlog? {
        // delegate to DevlogParser
        return DevlogParser.parse(html, url)
    }

    private fun parseShipEvent(html: Element, url: Url, projectId: Int, internalId: Int, author: String): ShipEvent? {
        TODO()
    }

    private fun parseSuperStar(html: Element, url: Url, projectId: Int, internalId: Int, author: String): SuperStar? {
        TODO()
    }
}