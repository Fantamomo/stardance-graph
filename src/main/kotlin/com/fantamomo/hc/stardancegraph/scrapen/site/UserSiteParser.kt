package com.fantamomo.hc.stardancegraph.scrapen.site

import com.fantamomo.hc.stardancegraph.model.User
import com.fantamomo.hc.stardancegraph.util.Logger
import io.ktor.http.*
import kotlinx.datetime.LocalDate
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.*

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


        TODO()
    }

    private fun parseUnverified(unverifiedSection: Element, url: Url): User.UnverifiedUser? {
        val username = unverifiedSection.selectFirst(".profile-placeholder__handle")
        if (username == null) {
            logger.error("Failed to find username element in $url")
            return null
        }
        val usernameText = username.text()

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

        return User.UnverifiedUser(usernameText, avatarUrl.toString())
    }

    fun parseJoinedDate(text: String): LocalDate {
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
}