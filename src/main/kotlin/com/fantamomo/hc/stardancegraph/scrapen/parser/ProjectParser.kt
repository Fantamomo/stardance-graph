package com.fantamomo.hc.stardancegraph.scrapen.parser

import com.fantamomo.hc.stardancegraph.model.Project
import com.fantamomo.hc.stardancegraph.model.User
import com.fantamomo.hc.stardancegraph.util.Logger
import io.ktor.http.*
import org.jsoup.nodes.Element

object ProjectParser {

    private val logger = Logger()

    fun parse(html: Element, url: Url, projectId: Int): Project.ScrapedProject? {

        val main = html.selectFirst(".app-layout")
        if (main == null) {
            logger.error("Failed to find main element in $url")
            return null
        }

        val panel = main.selectFirst(".project-show__panel")
        if (panel == null) {
            logger.error("Failed to find panel element in $url")
            return null
        }

        val titleElement = panel.selectFirst(".project-show__title")
        if (titleElement == null) {
            logger.error("Failed to find title element in $url")
            return null
        }
        val title = titleElement.text()

        val isHardware = panel.selectFirst(".project-show__tag--hardware") != null

        val authorAvatarElement = panel.selectFirst(".project-show__avatar")
        if (authorAvatarElement == null) {
            logger.error("Failed to find author avatar element in $url")
            return null
        }
        val authorAvatarUrlText =
            // didn't know this, but some users don't have avatars, which completely breaks my code,
            // because I thought every user has an avatar, so instead of rewriting like everything,
            // we just return a placeholder avatar URL
            if (authorAvatarElement.hasClass("project-show__avatar--placeholder")) "https://stardance.hackclub.com/assets/avatars/guest_star_3-3ac50924.png"
            else authorAvatarElement.attr("src")
        if (authorAvatarUrlText.isBlank()) {
            logger.error("Failed to find author avatar URL in $url")
            return null
        }
        val authorAvatarUrl = try {
            Url(authorAvatarUrlText)
        } catch (e: IllegalArgumentException) {
            logger.error("Failed to parse author avatar URL in $url: ${e.message}")
            return null
        }

        val authorElement = panel.selectFirst(".project-show__author")
        if (authorElement == null) {
            logger.error("Failed to find author element in $url")
            return null
        }
        val author = authorElement.text().removePrefix("@")
        if (author.isBlank()) {
            logger.error("Failed to find author name in $url")
            return null
        }

        val statsElement = panel.selectFirst(".project-show__stats")
        if (statsElement == null) {
            logger.error("Failed to find stats element in $url")
            return null
        }
        val stats = mutableMapOf<String, Int>()
        for (statElement in statsElement.select(".project-show__stats-item")) {
            val numberElement = statElement.selectFirst(".project-show__stats-num")
            if (numberElement == null) {
                logger.error("Failed to find number element in $url")
                continue
            }
            val number = numberElement.text().toIntOrNull()
            if (number == null) {
                logger.error("Failed to parse number in $url")
                continue
            }
            val labelElement = statElement.selectFirst(".project-show__stats-label")
            if (labelElement == null) {
                logger.error("Failed to find label element in $url")
                continue
            }
            val label = labelElement.text()
            if (label.isBlank()) {
                logger.error("Failed to parse label in $url")
                continue
            }
            stats[label.lowercase()] = number
        }

        val devlogCount = stats.entries.firstOrNull { it.key.contains("devlog") }?.value
        val hourCount = stats.entries.firstOrNull { it.key.contains("hour") }?.value

        val descriptionElement = panel.selectFirst(".project-show__description")
        val description = descriptionElement?.text() ?: ""

        val followersElement = panel.selectFirst(".project-show__followers")?.selectFirst("strong")
        if (followersElement == null) {
            logger.error("Failed to find followers element in $url")
            return null
        }
        val followers = followersElement.text().toIntOrNull()
        if (followers == null) logger.warn("Failed to parse followers count in $url")

        val superstar = panel.selectFirst(".project-show__badge--fire") != null

        val missionElement = panel.selectFirst(".mission-panel")
        val missionData = if (missionElement != null) {
            val isMissionShipped = missionElement.hasClass("mission-panel--shipped")
            val mission = missionElement.selectFirst(".mission-panel__title")?.text()
            if (mission == null) {
                logger.warn("Failed to find mission name in $url")
                return null
            }
            mission to isMissionShipped
        } else null

        val postContainer = html.selectFirst(".project-show__feed")
        val posts = postContainer?.select("> article")?.mapNotNull { PostParser.parse(it, url) } ?: emptyList()

        return Project.ScrapedProject(
            id = projectId,
            owner = User.FoundUser(
                name = author,
                avatarUrl = authorAvatarUrl.toString()
            ),
            title = title,
            description = description,
            superstar = superstar,
            followerCount = followers ?: 0,
            devlogCount = devlogCount ?: 0,
            hourCount = hourCount ?: 0,
            isHardware = isHardware,
            attachedMission = missionData?.first,
            missionShipped = missionData?.second,
            posts = posts
        )
    }
}