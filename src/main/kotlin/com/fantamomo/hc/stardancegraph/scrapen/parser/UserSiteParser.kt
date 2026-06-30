package com.fantamomo.hc.stardancegraph.scrapen.parser

import com.fantamomo.hc.stardancegraph.model.Project
import com.fantamomo.hc.stardancegraph.model.User
import com.fantamomo.hc.stardancegraph.model.UserProjects
import com.fantamomo.hc.stardancegraph.util.Logger
import io.ktor.http.*
import kotlinx.datetime.LocalDate
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.time.Duration

object UserSiteParser {
    private val logger = Logger()

    fun parse(html: Document, url: Url): User? {
        val main = html.selectFirst(".app-layout")

        if (main == null) {
            logger.error("Failed to find main element in $url")
            return null
        }

        val unverifiedSection = main.selectFirst(".profile-placeholder")
        return if (unverifiedSection != null) {
            parseUnverified(unverifiedSection, url)
        } else {
            parseVerified(main, url)
        }
    }

    private fun parseVerified(main: Element, url: Url): User.ScrapedUser? {
        val profileSection = main.selectFirst(".profile__card")
        if (profileSection == null) {
            logger.warn("Failed to find profile section in $url")
            return null
        }
        val avatarUrlElement = profileSection.selectFirst(".profile__avatar")
        if (avatarUrlElement == null) {
            logger.warn("Failed to find avatar URL element in $url")
            return null
        }
        val avatarUrl = try {
            Url(avatarUrlElement.attr("src"))
        } catch (e: Exception) {
            logger.warn("Failed to parse avatar URL in $url", e)
            return null
        }

        val bannerUrlElement = main.selectFirst(".profile__banner-image")
        val bannerUrlText = bannerUrlElement?.attr("src")
        val bannerUrl = if (bannerUrlText != null) parseUrl(bannerUrlText) else null
        if (bannerUrlElement != null && bannerUrl == null) logger.warn("Failed to parse banner URL in ${bannerUrlElement.cssSelector()} from $url")

        val usernameElement = profileSection.selectFirst(".profile__handle")
        if (usernameElement == null) {
            logger.warn("Failed to find username element in $url")
            return null
        }
        val usernameText = usernameElement.text().removePrefix("@")
        if (usernameText.isBlank()) {
            logger.warn("Failed to find username in $url")
            return null
        }

        val streakElement = usernameElement.selectFirst(".streak-badge")
        val streakText = streakElement?.attr("data-tooltip-message-value")?.ifBlank { null }
        val streak = streakText?.takeWhile { it.isDigit() }?.toIntOrNull()

        if (streakText != null && streak == null) {
            logger.warn("Failed to parse streak '$streakText' to streak in ${profileSection.cssSelector()} from $url")
        }

        val internalId = main.selectFirst(".profile")?.attr("action")?.removePrefix("/users/")?.toIntOrNull()
        if (internalId == null) {
            logger.warn("Failed to find internal ID in $url")
        }

        val joinedElement = profileSection.selectFirst(".profile__joined")
        if (joinedElement == null) {
            logger.warn("Failed to find joined date element in $url")
            return null
        }
        val joinedText = joinedElement.text()
        if (joinedText.isBlank()) {
            logger.warn("Failed to find joined date in $url")
            return null
        }
        val joinedDate = try {
            parseJoinedDate(joinedText)
        } catch (e: Exception) {
            logger.warn("Failed to parse joined date '$joinedText' in $url", e)
            return null
        }

        val statsSection = main.selectFirst(".profile__stats")
        if (statsSection == null) {
            logger.warn("Failed to find stats section in $url")
            return null
        }
        val stats = mutableMapOf<String, Int>()
        for (statElement in statsSection.select(".profile__stat")) {
            val value = statElement.selectFirst(".profile__stat-num")?.text()?.toInt()
            if (value == null) {
                logger.warn("Failed to find value for stat element in $url")
                continue
            }
            val label = statElement.selectFirst(".profile__stat-label")?.text()
            if (label == null) {
                logger.warn("Failed to find label for stat element in $url")
                continue
            }
            stats[label] = value
        }

        val devlogCount = stats.entries.firstOrNull { it.key.lowercase().contains("devlog") }?.value
        if (devlogCount == null) logger.warn("Failed to find devlog count in $url")
        val projectCount = stats.entries.firstOrNull { it.key.lowercase().contains("project") }?.value
        if (projectCount == null) logger.warn("Failed to find project count in $url")
        val shipCount = stats.entries.firstOrNull { it.key.lowercase().contains("ship") }?.value
        if (shipCount == null) logger.warn("Failed to find ship count in $url")
        val votesCount = stats.entries.firstOrNull { it.key.lowercase().contains("vote") }?.value
        if (votesCount == null) logger.warn("Failed to find vote count in $url")

        val bioElement = profileSection.selectFirst(".profile__bio")
        val bioText = bioElement?.text()

        val followElement = main.selectFirst(".profile__follow-counts")
        if (followElement == null) {
            logger.warn("Failed to find follow counts element in $url")
            return null
        }
        val followList = followElement.select(".profile__follow-count").map { it.text().trim() }
        val followingCount = followList.firstOrNull { it.lowercase().contains("following") }?.takeWhile { it.isDigit() }?.toIntOrNull()
        if (followingCount == null) logger.warn("Failed to find following count in $url")
        val followerCount = followList.firstOrNull { it.lowercase().contains("follower") }?.takeWhile { it.isDigit() }?.toIntOrNull()
        if (followerCount == null) logger.warn("Failed to find follower count in $url")

        val achievementElement = main.selectFirst(".achievements-widget__icons")
//        if (achievementElement == null) {
//            logger.warn("Failed to find achievements section in $url")
//            return null
//        }
        val achievements = achievementElement?.select(".achievements-widget__icon")?.mapNotNull { it.attr("data-tooltip-message-value").ifBlank { null } } ?: emptyList()

        val tabContent = main.selectFirst(".profile-tab-content")
        if (tabContent == null) {
            logger.warn("Failed to find posts section in $url")
            return null
        }
        val posts = (tabContent.selectFirst(".profile-feed-page") ?: tabContent)
            .select("> article")
            .mapNotNull { PostParser.parse(it, url) }

        // this completely brock my code
        val hasMorePages = main.getElementById("profile_feed_page_2") != null

        return User.ScrapedUser(
            name = usernameText,
            avatarUrl = avatarUrl.toString(),
            bannerUrl = bannerUrl,
            internalId = internalId,
            joinedDate = joinedDate,
            bio = bioText ?: "",
            devlogCount = devlogCount ?: 0,
            projectsCount = projectCount ?: 0,
            shipCount = shipCount ?: 0,
            votesCount = votesCount ?: 0,
            followerCount = followerCount ?: 0,
            followingCount = followingCount ?: 0,
            streak = streak,
            achievements = achievements,
            posts = posts,
            hasMorePages = hasMorePages,
        )
    }

    private fun parseUnverified(unverifiedSection: Element, url: Url): User.UnverifiedUser? {
        val username = unverifiedSection.selectFirst(".profile-placeholder__handle")
        if (username == null) {
            logger.error("Failed to find username element in $url")
            return null
        }
        val usernameText = username.text().removePrefix("@")
        if (usernameText.isBlank()) {
            logger.error("Failed to find username in $url")
            return null
        }

        val avatarUrlElement = unverifiedSection.selectFirst(".profile-placeholder__avatar")
        if (avatarUrlElement == null) {
            logger.error("Failed to find avatar URL element in $url")
            return null
        }
        val avatarUrl = try {
            Url(avatarUrlElement.attr("src"))
        } catch (e: Exception) {
            logger.error("Failed to parse avatar URL from $url", e)
            return null
        }

        val path = url.encodedPath
        val internalId = if (path.startsWith("/users/")) {
            path.removePrefix("/users/").toIntOrNull()
        } else {
            null
        }

        return User.UnverifiedUser(usernameText, avatarUrl.toString(), internalId)
    }

    private fun parseJoinedDate(text: String): LocalDate {
        val datePart = text
            .removePrefix("Joined ")
            .replace(Regex("(st|nd|rd|th)"), "")

        val formatter = DateTimeFormatter.ofPattern(
            "MMMM d, yyyy",
            Locale.ENGLISH
        )

        val javaDate = java.time.LocalDate.parse(datePart, formatter)

        return LocalDate(
            javaDate.year,
            javaDate.monthValue,
            javaDate.dayOfMonth
        )
    }

    fun parsePagedUser(html: Document, url: Url, original: User.ScrapedUser, page: Int): User.PagedUser? {
        val main = html.selectFirst(".app-layout")

        if (main == null) {
            logger.error("Failed to find main element in $url")
            return null
        }

        val tabContent = main.selectFirst(".profile-tab-content")
        if (tabContent == null) {
            logger.warn("Failed to find posts section in $url")
            return null
        }
        val posts = (tabContent.selectFirst(".profile-feed-page") ?: tabContent)
            .select("> article")
            .mapNotNull { PostParser.parse(it, url) }

        // this is more a suspicion, because i couldn't find a user with more than 2 pages
        val hasMorePages = main.getElementById("profile_feed_page_${page + 1}") != null

        return User.PagedUser(
            original = original,
            page = page,
            posts = posts,
            hasMorePages = hasMorePages
        )
    }

    fun parseUserProjects(html: Document, url: Url): UserProjects? {
        val tabContent = html.selectFirst(".profile-tab-content")
        if (tabContent == null) {
            logger.warn("Failed to find projects section in $url")
            return null
        }

        val userPanel = html.selectFirst(".profile__header")
        if (userPanel == null) {
            logger.warn("Failed to find user panel in ${tabContent.cssSelector()} from $url")
            return null
        }
        val avatarUrlElement = userPanel.selectFirst(".profile__avatar")
        if (avatarUrlElement == null) {
            logger.warn("Failed to find avatar URL element in ${userPanel.cssSelector()} from $url")
            return null
        }
        val avatarUrl = try {
            Url(avatarUrlElement.attr("src"))
        } catch (e: Exception) {
            logger.warn("Failed to parse avatar URL in ${userPanel.cssSelector()} from $url", e)
            return null
        }
        val usernameElement = userPanel.selectFirst(".profile__handle")
        if (usernameElement == null) {
            logger.warn("Failed to find username element in ${userPanel.cssSelector()} from $url")
            return null
        }
        val usernameText = usernameElement.text().removePrefix("@")
        if (usernameText.isBlank()) {
            logger.warn("Failed to find username in ${userPanel.cssSelector()} from $url")
            return null
        }
        val user = User.FoundUser(usernameText, avatarUrl.toString())

        val projectsElement = tabContent.select(".profile-project-card")

        val projects = mutableListOf<Project.UserProjectPageProject>()
        for (projectElement in projectsElement) {
            val projectLink = projectElement.attr("href")
            if (projectLink.isBlank()) {
                logger.warn("Failed to find project link in ${projectElement.cssSelector()} from $url")
                continue
            }
            val id = projectLink.removePrefix("/projects/").toIntOrNull()
            if (id == null) {
                logger.warn("Failed to parse project id from $projectLink in ${projectElement.cssSelector()} from $url")
                continue
            }
            val imageElement = projectElement.selectFirst(".profile-project-card__banner-img")
            if (imageElement == null) {
                logger.warn("Failed to find project image in ${projectElement.cssSelector()} from $url")
                continue
            }
            val imageUrl = try {
                Url(imageElement.attr("src"))
            } catch (e: Exception) {
                logger.warn("Failed to parse project image URL in ${projectElement.cssSelector()} from $url", e)
                continue
            }
            val body = projectElement.selectFirst(".profile-project-card__body")
            if (body == null) {
                logger.warn("Failed to find project body in ${projectElement.cssSelector()} from $url")
                continue
            }
            val titleElement = body.selectFirst(".profile-project-card__title")
            if (titleElement == null) {
                logger.warn("Failed to find project title in ${body.cssSelector()} from $url")
                continue
            }
            val title = titleElement.text()
            if (title.isBlank()) {
                logger.warn("Failed to parse project title in ${titleElement.cssSelector()} from $url")
                continue
            }
            val metaStats = body.select(".profile-project-card__meta-item")
            val rawStats = mutableSetOf<String>()
            for (statElement in metaStats) {
                val label = statElement.selectFirst("span")?.text()
                if (label == null) {
                    logger.warn("Failed to find project stat label in ${statElement.cssSelector()} from $url")
                    continue
                }
                if (label.isBlank()) {
                    logger.warn("Failed to parse project stat label in ${statElement.cssSelector()} from $url")
                    continue
                }
                rawStats.add(label)
            }
            val devlogCount = rawStats.firstOrNull { it.lowercase().contains("devlog") }?.trim()?.takeWhile { it.isDigit() }?.toIntOrNull()
            val hourCount = rawStats.firstOrNull { it.lowercase().endsWith('h') }?.trim()?.takeWhile { it.isDigit() }?.toIntOrNull()

            val descriptionElement = body.selectFirst(".profile-project-card__description")
            val description = descriptionElement?.text() ?: ""

            val lastUpdatedElement = body.selectFirst(".profile-project-card__updated")
            if (lastUpdatedElement == null) {
                logger.warn("Failed to find project last updated in ${body.cssSelector()} from $url")
                continue
            }
            val lastUpdatedText = lastUpdatedElement.text()
            if (lastUpdatedText.isBlank()) {
                logger.warn("Failed to parse project last updated in ${lastUpdatedElement.cssSelector()} from $url")
                continue
            }
            val lastUpdated = parseLastUpdated(lastUpdatedText, lastUpdatedElement.cssSelector(), url) ?: continue
            projects.add(
                Project.UserProjectPageProject(
                    id = id,
                    owner = user,
                    title = title,
                    bannerUrl = imageUrl,
                    description = description,
                    devlogCount = devlogCount ?: 0,
                    hoursCount = hourCount ?: 0,
                    lastUpdated = lastUpdated
                )
            )
        }
        return UserProjects(
            user = user,
            projects = projects.toList()
        )
    }

    private fun parseLastUpdated(text: String, element: String, url: Url): Duration? {
        val durationText = text
            .removePrefix("Last updated ")
            .removePrefix(" ago")
            .replace("days", "d")
            .replace("day", "d")
            .replace("hours", "h")
            .replace("hour", "h")
            .replace("minutes", "m")
            .replace("minute", "m")
        return try {
            Duration.parse(durationText)
        } catch (e: Exception) {
            logger.warn("Failed to parse last updated '$durationText'(raw: '$text') in $element in $url", e)
            null
        }
    }
}