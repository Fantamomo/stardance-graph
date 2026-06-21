package com.fantamomo.hc.stardancegraph.scrapen.site

import com.fantamomo.hc.stardancegraph.model.Comment
import com.fantamomo.hc.stardancegraph.model.Devlog
import com.fantamomo.hc.stardancegraph.model.User
import com.fantamomo.hc.stardancegraph.util.Logger
import com.fantamomo.hc.stardancegraph.util.toMarkdown
import io.ktor.http.*
import org.jsoup.nodes.Element
import kotlin.time.Duration
import kotlin.time.Instant

object DevlogParser {
    private val logger = Logger()

    fun parse(originalHtml: Element, url: Url): Devlog? {
        val html = if (originalHtml.tagName() != "article") {
            val article = originalHtml.selectFirst("article")
            if (article == null) {
                logger.warn("Failed to find article element in ${originalHtml.cssSelector()} from $url")
                return null
            }
            article.selectFirst("> article") ?: article
        } else originalHtml

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
        val body = bodyElement.toMarkdown()

        val attachmentsElement = html.selectFirst(".feed-post-card__media")
        val attachments = attachmentsElement?.select(".feed-post-card__image")?.map { it.attr("src") }?.filter { it.isNotBlank() } ?: emptyList()

        val commentsCountElement = originalHtml.getElementById("comments_count_post_devlog_${devlogId}")
        if (commentsCountElement == null) {
            logger.warn("Failed to find comments count element in ${html.cssSelector()} from $url")
            return null
        }
        val commentsCount = commentsCountElement.text().toIntOrNull()
        if (commentsCount == null) {
            logger.warn("Failed to parse comments count in ${commentsCountElement.cssSelector()} from $url")
        }

        val actionsElement = html.selectFirst(".feed-post-card__actions")

        if (actionsElement == null) {
            logger.warn("Failed to find actions element in ${html.cssSelector()} from $url")
            return null
        }

        val feedPostActions = html.select(".feed-post-card__action").filter { it.tagName().lowercase() == "span" }
        val repostCountElement = feedPostActions.firstOrNull { it.attr("aria-label").lowercase().contains("repost") }?.selectFirst("span")
        if (repostCountElement == null) {
            logger.warn("Failed to find repost count element in ${actionsElement.cssSelector()} from $url")
            return null
        }
        val repostCount = repostCountElement.text().toIntOrNull()
        if (repostCount == null) {
            logger.warn("Failed to parse repost count in ${repostCountElement.cssSelector()} from $url")
        }

        val likesCountElement = html.selectFirst(".feed-post-card__like")?.selectFirst(".like-button__count")
        if (likesCountElement == null) {
            logger.warn("Failed to find likes count element in ${actionsElement.cssSelector()} from $url")
            return null
        }
        val likesCount = likesCountElement.text().toIntOrNull()
        if (likesCount == null) {
            logger.warn("Failed to parse likes count in ${likesCountElement.cssSelector()} from $url")
        }
        val viewsCountElement = feedPostActions.firstOrNull { it.attr("title").lowercase().contains("view") }?.selectFirst("span")
        if (viewsCountElement == null) {
            logger.warn("Failed to find views count element in ${actionsElement.cssSelector()} from $url")
            return null
        }
        val viewsCount = viewsCountElement.text().toIntOrNull()
        if (viewsCount == null) {
            logger.warn("Failed to parse views count in ${viewsCountElement.cssSelector()} from $url")
        }

        val commentsContainer = originalHtml.getElementById("comments")
        val comments: List<Comment>
        if (commentsContainer != null && commentsContainer.classNames().contains("devlog-detail__comments")) {
            val commentsList = commentsContainer.select(".devlog-detail__comments-list")
            val result = mutableListOf<Comment>()
            for (comment in commentsList.select(".devlog-comment")) {
                val commentId = comment.id().removePrefix("comment_").toIntOrNull()
                if (commentId == null) {
                    logger.warn("Failed to find comment number in ${comment.cssSelector()} from $url")
                    continue
                }
                val avatarUrlElement = comment.selectFirst(".devlog-comment__avatar")
                if (avatarUrlElement == null) {
                    logger.warn("Failed to find avatar URL element in ${comment.cssSelector()} from $url")
                    continue
                }
                val avatarUrl = try {
                    Url(avatarUrlElement.attr("src"))
                } catch (e: Exception) {
                    logger.warn("Failed to parse avatar URL in ${avatarUrlElement.cssSelector()} from $url", e)
                    continue
                }
                val authorElement = comment.selectFirst(".devlog-comment__author")
                if (authorElement == null) {
                    logger.warn("Failed to find author element in ${comment.cssSelector()} from $url")
                    continue
                }
                val author = authorElement.text().removePrefix("@")
                if (author.isBlank()) {
                    logger.warn("Failed to find author in ${authorElement.cssSelector()} from $url")
                    continue
                }
                val createdAtElement = comment.selectFirst(".devlog-comment__time")
                if (createdAtElement == null) {
                    logger.warn("Failed to find created at element in ${comment.cssSelector()} from $url")
                    continue
                }
                val createdAtText = createdAtElement.attr("datetime")
                if (createdAtText.isBlank()) {
                    logger.warn("Failed to find created at in ${createdAtElement.cssSelector()} from $url")
                    continue
                }
                val createdAt = try {
                    Instant.parse(createdAtText)
                } catch (e: Exception) {
                    logger.warn("Failed to parse created at in ${createdAtElement.cssSelector()} from $url", e)
                    continue
                }
                val bodyElement = comment.selectFirst(".devlog-comment__body")
                if (bodyElement == null) {
                    logger.warn("Failed to find body element in ${comment.cssSelector()} from $url")
                    continue
                }
                val body = bodyElement.toMarkdown()
                if (body.isBlank()) {
                    logger.warn("Failed to find body in ${bodyElement.cssSelector()} from $url")
                    continue
                }
                result.add(
                    Comment(
                        project = projectId,
                        number = commentId,
                        author = User.FoundUser(
                            name = author,
                            avatarUrl = avatarUrl.toString()
                        ),
                        body = body,
                        createdAt = createdAt
                    )
                )
            }
            comments = result
        } else {
            comments = emptyList()
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
            commentsCount = commentsCount ?: 0,
            repostsCount = repostCount ?: 0,
            likesCount = likesCount ?: 0,
            viewsCount = viewsCount ?: 0,
            comments = comments
        )
    }
}