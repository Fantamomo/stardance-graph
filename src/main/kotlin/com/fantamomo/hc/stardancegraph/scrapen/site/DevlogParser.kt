package com.fantamomo.hc.stardancegraph.scrapen.site

import com.fantamomo.hc.stardancegraph.model.Devlog
import com.fantamomo.hc.stardancegraph.model.User
import com.fantamomo.hc.stardancegraph.util.Logger
import io.ktor.http.*
import org.jsoup.nodes.Element
import kotlin.time.Duration
import kotlin.time.Instant

object DevlogParser {
    private val logger = Logger()

    fun parse(html: Element, url: Url): Devlog? {

        val internalIdText = html.attr("data-feed-engagement-post-id-value")
        if (internalIdText.isBlank()) {
            logger.warn("Failed to find internal ID in ${html.cssSelector()} from $url")
            return null
        }
        val internalId = internalIdText.toIntOrNull()
        if (internalId == null) {
            logger.warn("Failed to parse internal ID in ${html.cssSelector()} from $url")
            return null
        }

        val projectIdText = html.attr("data-feed-engagement-project-id-value")
        if (projectIdText.isBlank()) {
            logger.warn("Failed to find project ID in ${html.cssSelector()} from $url")
            return null
        }
        val projectId = projectIdText.toIntOrNull()
        if (projectId == null) {
            logger.warn("Failed to parse project ID in ${html.cssSelector()} from $url")
            return null
        }

        val linkText = html.attr("data-card-link-url-value")
        if (linkText.isBlank()) {
            logger.warn("Failed to find link in ${html.cssSelector()} from $url")
            return null
        }
        val link = try {
            Url(linkText)
        } catch (e: Exception) {
            logger.warn("Failed to parse link '$linkText' in ${html.cssSelector()} from $url", e)
            return null
        }
        if (!link.encodedPath.startsWith("/projects/$projectId/devlogs/")) {
            logger.warn("Link '$linkText' does not match project ID '$projectId' in ${html.cssSelector()} from $url")
            return null
        }
        val devlogId = link.encodedPath.removePrefix("/projects/$projectId/devlogs/").toIntOrNull()
        if (devlogId == null) {
            logger.warn("Failed to find devlog ID in ${html.cssSelector()} from $url")
            return null
        }

        val avatarUrlElement = html.selectFirst(".feed-post-card__avatar")
        if (avatarUrlElement == null) {
            logger.warn("Failed to find avatar URL element in ${html.cssSelector()} from $url")
            return null
        }
        val avatarUrl = try {
            Url(avatarUrlElement.attr("src"))
        } catch (e: Exception) {
            logger.warn("Failed to parse avatar URL in ${avatarUrlElement.cssSelector()} from $url", e)
            return null
        }
        val authorElement = html.selectFirst(".feed-post-card__author")
        if (authorElement == null) {
            logger.warn("Failed to find author element in ${html.cssSelector()} from $url")
            return null
        }
        val author = authorElement.text().removePrefix("@")
        if (author.isBlank()) {
            logger.warn("Failed to find author in ${authorElement.cssSelector()} from $url")
            return null
        }

        val createdAtElement = html.selectFirst(".feed-post-card__time")
        if (createdAtElement == null) {
            logger.warn("Failed to find created at element in ${html.cssSelector()} from $url")
            return null
        }
        val createdAtText = createdAtElement.attr("datetime")
        if (createdAtText.isBlank()) {
            logger.warn("Failed to find created at in ${createdAtElement.cssSelector()} from $url")
            return null
        }
        val createdAt = try {
            Instant.parse(createdAtText)
        } catch (e: Exception) {
            logger.warn("Failed to parse created at in ${createdAtElement.cssSelector()} from $url", e)
            return null
        }

        val timeLoggedElement = html.selectFirst(".feed-post-card__duration")
        if (timeLoggedElement == null) {
            logger.warn("Failed to find time logged element in ${html.cssSelector()} from $url")
            return null
        }
        val timeLoggedText = timeLoggedElement.text().removeSuffix(" logged")
        if (timeLoggedText.isBlank()) {
            logger.warn("Failed to find time logged in ${timeLoggedElement.cssSelector()} from $url")
            return null
        }
        val timeLogged = try {
            Duration.parse(timeLoggedText)
        } catch (e: Exception) {
            logger.warn("Failed to parse time logged in ${timeLoggedElement.cssSelector()} from $url", e)
            return null
        }

        val bodyElement = html.selectFirst(".feed-post-card__body")
        if (bodyElement == null) {
            logger.warn("Failed to find body element in ${html.cssSelector()} from $url")
            return null
        }
        val body = bodyElement.text()

        val attachmentsElement = html.selectFirst(".feed-post-card__media")
        val attachments = attachmentsElement?.select(".feed-post-card__image")?.map { it.attr("src") }?.filter { it.isNotBlank() } ?: emptyList()

        val commentsCountElement = html.getElementById("comments_count_post_devlog_${devlogId}")
        if (commentsCountElement == null) {
            logger.warn("Failed to find comments count element in ${html.cssSelector()} from $url")
            return null
        }
        val commentsCount = commentsCountElement.text().toIntOrNull()
        if (commentsCount == null) {
            logger.warn("Failed to parse comments count in ${commentsCountElement.cssSelector()} from $url")
            return null
        }

        return Devlog(
            id = devlogId,
            internalId = internalId,
            author = User.FoundUser(
                name = author,
                avatarUrl = avatarUrl.toString()
            ),
            body = body,
            projectId = projectId,
            createdAt = createdAt,
            timeLogged = timeLogged,
            attachments = attachments,
            commentsCount = commentsCount,
            repostsCount = TODO(),
            likesCount = TODO(),
            viewsCount = TODO(),
            comments = TODO()
        )
    }
}