package com.fantamomo.hc.stardancegraph.scrapen.site

import com.fantamomo.hc.stardancegraph.model.*
import com.fantamomo.hc.stardancegraph.util.Logger
import com.fantamomo.hc.stardancegraph.util.toMarkdown
import io.ktor.http.*
import org.jsoup.nodes.Element
import kotlin.time.Instant

object PostParser {
    private val logger = Logger()

    fun parse(html: Element, url: Url): Post? {
        val type = html.attr("data-feed-engagement-post-type-value")
        if (type.isBlank()) {
            logger.warn("Failed to find post type in ${html.cssSelector()} from $url")
            return null
        }

        val projectId = html.attr("data-feed-engagement-project-id-value").toIntOrNull()
        if (projectId == null && type != "Post::Repost") {
            // reposts don't have a project id
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
        val author = authorElement.text().removePrefix("@")
        if (author.isBlank()) {
            logger.warn("Failed to find author in ${html.cssSelector()} from $url")
            return null
        }

        return when (type) {
            "Post::Repost" -> parseRepost(html, url, null,internalId, author, true)
            "Post::Devlog" -> {
                // parseDevlog(html, url) // if it would be that easy, a repost without a message is actually a devlog with another author and another id
                parseDevlogOrRepost(html, url, projectId!!, internalId, author)
            }

            "Post::ShipEvent" -> parseShipEvent(html, url, projectId!!, internalId, author)
            "Post::FireEvent" -> parseSuperStar(html, url, projectId!!, internalId, author)
            else -> {
                logger.warn("Unknown post type $type in ${html.cssSelector()} from $url")
                null
            }
        }
    }

    private fun parseDevlogOrRepost(html: Element, url: Url, projectId: Int, internalId: Int, author: String): Post? {
        if (html.selectFirst(".feed-post-card__reposted-by") != null) {
            return parseRepost(html, url, projectId, internalId, author, false)
        }
        return parseDevlog(html, url, projectId, internalId, author)
    }

    private fun parseRepost(html: Element, url: Url, projectId: Int?, internalId: Int, author: String, quoted: Boolean): Repost? {
        val devlogContentElement = html.selectFirst(".feed-post-card__repost-preview")?.selectFirst("> article") ?: html

        val repostedByElement = html.selectFirst(".feed-post-card__reposted-by-link") ?: if (quoted) html.selectFirst(".feed-post-card__author") else null
        if (repostedByElement == null) {
            logger.warn("Failed to find reposted by element in ${html.cssSelector()} from $url")
            return null
        }
        val repostedBy = repostedByElement.text().removePrefix("@")
        if (repostedBy.isBlank()) {
            logger.warn("Failed to find reposted by in ${html.cssSelector()} from $url")
            return null
        }
        val projectId = projectId ?: devlogContentElement.attr("data-feed-engagement-project-id-value").toIntOrNull()
        if (projectId == null) {
            if (html.attr("data-card-link-url-value").isNotEmpty()) { // the data-card-link-url-value does not exists for reposts from devlgos where the author hasn't verified there identity yet
                logger.warn("Failed to find project ID in ${html.cssSelector()} from $url")
            }
            return null
        }

        val createdAtElement = html.selectFirst(".feed-post-card__time")
        if (createdAtElement == null) {
            logger.warn("Failed to find created at element in ${html.cssSelector()} from $url")
            return null
        }
        val createdAtText = createdAtElement.attr("datetime")
        if (createdAtText.isBlank()) {
            logger.warn("Failed to find created at in ${html.cssSelector()} from $url")
            return null
        }
        val createdAt = try {
            Instant.parse(createdAtText)
        } catch (e: Exception) {
            logger.warn("Failed to parse created at '$createdAtText' in ${html.cssSelector()} from $url", e)
            return null
        }

        val originalAuthorElement = devlogContentElement.selectFirst(".feed-post-card__author")
        if (originalAuthorElement == null) {
            logger.warn("Failed to find original author element in ${devlogContentElement.cssSelector()} from $url")
            return null
        }
        val originalAuthor = originalAuthorElement.text().removePrefix("@")
        if (originalAuthor.isBlank()) {
            logger.warn("Failed to find original author in ${devlogContentElement.cssSelector()} from $url")
            return null
        }

        val originalAuthorAvatarElement = devlogContentElement.selectFirst(".feed-post-card__avatar")
        if (originalAuthorAvatarElement == null) {
            logger.warn("Failed to find original author avatar element in ${devlogContentElement.cssSelector()} from $url")
            return null
        }
        val originalAuthorAvatarUrl = originalAuthorAvatarElement.attr("src")
        if (originalAuthorAvatarUrl.isBlank()) {
            logger.warn("Failed to find original author avatar URL in ${devlogContentElement.cssSelector()} from $url")
            return null
        }

        val devlogUrlText = html.attr("data-card-link-url-value")
        if (devlogUrlText.isBlank()) {
            logger.warn("Failed to find devlog URL in ${html.cssSelector()} from $url")
            return null
        }
        val devlogUrl = try {
            Url(devlogUrlText)
        } catch (e: Exception) {
            logger.warn("Failed to parse devlog URL '$devlogUrlText' in ${html.cssSelector()} from $url", e)
            return null
        }
        if (!devlogUrl.encodedPath.startsWith("/projects/$projectId/devlogs/")) {
            logger.warn("Devlog URL '$devlogUrlText' does not match project ID '$projectId' in ${html.cssSelector()} from $url")
            return null
        }
        val devlogId = devlogUrl.encodedPath.removePrefix("/projects/$projectId/devlogs/").toIntOrNull()
        if (devlogId == null) {
            logger.warn("Failed to find devlog ID in ${html.cssSelector()} from $url")
            return null
        }

        val message = if (quoted) {
            val postBody = html.selectFirst(".feed-post-card__body")
            if (postBody == null) {
                logger.warn("Failed to find post body in ${html.cssSelector()} from $url, but was quoted")
                null
            } else {
                val message = postBody.toMarkdown()
                message.ifBlank {
                    logger.warn("Failed to find post body in ${html.cssSelector()} from $url, but was quoted")
                    null
                }
            }
        } else null

        // we have the devlog element which has been reposted, so we could parse it,
        // the problem is that the stats on the reposted devlog are not correct, so we don't use it

        return Repost(
            projectId = projectId,
            author = User.FoundUser(
                name = repostedBy,
                avatarUrl = "" // the problem is that a repost which isn't quoted has no reposter avatar url, so we can't find it, but the author should be already in the database, so we hope that this makes no problems
            ),
            createdAt = createdAt,
            devlogAuthor = User.FoundUser(
                name = originalAuthor,
                avatarUrl = originalAuthorAvatarUrl
            ),
            devlogId = devlogId,
            message = message
        )
    }

    private fun parseDevlog(html: Element, url: Url, projectId: Int, internalId: Int, author: String): Devlog? {
        // delegate to DevlogParser
        return DevlogParser.parse(html, url)
    }

    private fun parseShipEvent(html: Element, url: Url, projectId: Int, internalId: Int, author: String): ShipEvent? {

        val shipNumberElement = html.selectFirst(".project-show__latest-ship-label")?.selectFirst("span")
        if (shipNumberElement == null) {
            logger.warn("Failed to find ship number element in ${html.cssSelector()} from $url")
            return null
        }
        val text = shipNumberElement.text()
        val shipNumber = text.removePrefix("Ship #").takeWhile { it.isDigit() }.toIntOrNull()
        if (shipNumber == null && text != "Ship") {
            logger.warn("Failed to find ship number in ${shipNumberElement.cssSelector()} from $url")
        }

        val avatarUrlElement = html.selectFirst(".feed-post-card__avatar")
        if (avatarUrlElement == null) {
            logger.warn("Failed to find avatar url element in ${html.cssSelector()} from $url")
            return null
        }
        val avatarUrl = avatarUrlElement.attr("src")
        if (avatarUrl.isBlank()) {
            logger.warn("Failed to find avatar url in ${avatarUrlElement.cssSelector()} from $url")
            return null
        }

        val createdAtElement = html.selectFirst(".feed-post-card__time")
        if (createdAtElement == null) {
            logger.warn("Failed to find created at element in ${html.cssSelector()} from $url")
            return null
        }
        val createdAtText = createdAtElement.attr("datetime")
        if (createdAtText.isBlank()) {
            logger.warn("Failed to find created at text in ${createdAtElement.cssSelector()} from $url")
            return null
        }

        val createdAt = try {
            Instant.parse(createdAtText)
        } catch (e: Exception) {
            logger.warn("Failed to parse created at '$createdAtText' from '$createdAtText' in ${createdAtElement.cssSelector()} from $url", e)
            return null
        }

        val bodyElement = html.selectFirst(".project-show__latest-ship-text")
        if (bodyElement == null) {
            logger.warn("Failed to find body element in ${html.cssSelector()} from $url")
            return null
        }
        val body = bodyElement.toMarkdown()

        val statsElement = html.selectFirst(".project-show__latest-ship-stats")
        if (statsElement == null) {
            logger.warn("Failed to find stats element in ${html.cssSelector()} from $url")
            return null
        }
        val stats = statsElement.select(".profile-project-card__meta-item").associateWith { it.text() }
        if (stats.size !in 2..3) {
            logger.warn("Expected 2 stats in ${statsElement.cssSelector()} from $url, but found ${stats.size} instead")
            return null
        }
        val devlogCount = stats.entries.firstOrNull { !it.key.hasClass("profile-project-card__meta-item--time") }?.value?.takeWhile { it.isDigit() }?.toIntOrNull()
        if (devlogCount == null) logger.warn("Failed to find devlog count in ${statsElement.cssSelector()} from $url")
        val hourCount = stats.entries.firstOrNull { it.key.hasClass("profile-project-card__meta-item--time") }?.value?.takeWhile { it.isDigit() }?.toIntOrNull()
        if (hourCount == null) logger.warn("Failed to find hour count in ${statsElement.cssSelector()} from $url")
        val mission = stats.entries.firstOrNull { it.key.hasClass("profile-project-card__meta-item--mission") }?.value

        val linkElement = html.selectFirst(".project-show__latest-ship-cta")
        if (linkElement == null) {
            logger.warn("Failed to find link element in ${html.cssSelector()} from $url")
            return null
        }
        val links = linkElement.select(".project-show__latest-ship-btn").associateBy { it.text() }
        val demoUrlText = links.entries.firstOrNull { it.key.lowercase().run { contains("try") || contains("project") } }?.value?.attr("href")
        if (demoUrlText == null) {
            logger.warn("Failed to find demo URL in ${linkElement.cssSelector()} from $url")
            return null
        }
        val repoUrlText = links.entries.firstOrNull { it.key.lowercase().run { contains("source") || contains("code") } }?.value?.attr("href")
        if (repoUrlText == null) {
            logger.warn("Failed to find repo URL in ${linkElement.cssSelector()} from $url")
            return null
        }
        val demoUrl = try {
            Url(demoUrlText)
        } catch (e: Exception) {
            logger.warn("Failed to parse demo URL '$demoUrlText' in ${linkElement.cssSelector()} from $url", e)
            return null
        }
        val repoUrl = try {
            Url(repoUrlText)
        } catch (e: Exception) {
            logger.warn("Failed to parse repo URL '$repoUrlText' in ${linkElement.cssSelector()} from $url", e)
            return null
        }

        return ShipEvent(
            projectId = projectId,
            author = User.FoundUser(
                name = author,
                avatarUrl = avatarUrl
            ),
            createdAt = createdAt,
            internalId = internalId,
            shipNumber = shipNumber,
            body = body,
            demoUrl = demoUrl,
            repoUrl = repoUrl,
            devlogCount = devlogCount ?: 0,
            hourCount = hourCount ?: 0,
            mission = mission
        )
    }

    private fun parseSuperStar(html: Element, url: Url, projectId: Int, internalId: Int, author: String): SuperStar? {

        val authorElement = html.selectFirst(".feed-post-card__author")
        if (authorElement == null) {
            logger.warn("Failed to find author element in ${html.cssSelector()} from $url")
            return null
        }
        val authorName = authorElement.text().removePrefix("@")
        if (authorName.isBlank()) {
            logger.warn("Failed to find author name in ${html.cssSelector()} from $url")
            return null
        }

        val avatarUrlElement = html.selectFirst(".feed-post-card__avatar")
        if (avatarUrlElement == null) {
            logger.warn("Failed to find avatar URL element in ${html.cssSelector()} from $url")
            return null
        }
        val avatarUrl = avatarUrlElement.attr("src")
        if (avatarUrl.isBlank()) {
            logger.warn("Failed to find avatar URL in ${avatarUrlElement.cssSelector()} from $url")
            return null
        }

        val createdAtElement = html.selectFirst(".feed-post-card__time")
        if (createdAtElement == null) {
            logger.warn("Failed to find created at element in ${html.cssSelector()} from $url")
            return null
        }
        val createdAtText = createdAtElement.attr("datetime")
        if (createdAtText.isBlank()) {
            logger.warn("Failed to find created at text in ${createdAtElement.cssSelector()} from $url")
            return null
        }
        val createdAt = try {
            Instant.parse(createdAtText)
        } catch (e: Exception) {
            logger.warn("Failed to parse created at text in ${createdAtElement.cssSelector()} from $url: ${e.message}")
            return null
        }

        val footerElement = html.selectFirst(".feed-post-card__actions")
        if (footerElement == null) {
            logger.warn("Failed to find footer element in ${html.cssSelector()} from $url")
            return null
        }
        val stats = footerElement.select(".feed-post-card__action").map { it.selectFirst("span")?.text()?.toIntOrNull() }
        if (stats.size != 2) {
            logger.warn("Expected 2 stats in ${footerElement.cssSelector()} from $url, but found ${stats.size} instead")
            return null
        }
        val reposts = stats[0]
        if (reposts == null) {
            logger.warn("Failed to find reposts in ${footerElement.cssSelector()} from $url")
        }
        val views = stats[1]
        if (views == null) {
            logger.warn("Failed to find views in ${footerElement.cssSelector()} from $url")
        }

        return SuperStar(
            projectId = projectId,
            author = User.FoundUser(
                name = authorName,
                avatarUrl = avatarUrl
            ),
            createdAt = createdAt,
            internalId = internalId,
            views = views ?: 0,
            reposts = reposts ?: 0
        )
    }
}