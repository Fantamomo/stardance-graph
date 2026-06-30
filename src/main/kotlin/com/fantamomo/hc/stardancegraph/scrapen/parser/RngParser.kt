package com.fantamomo.hc.stardancegraph.scrapen.parser

import com.fantamomo.hc.stardancegraph.model.RngPage
import com.fantamomo.hc.stardancegraph.model.User
import com.fantamomo.hc.stardancegraph.util.Logger
import io.ktor.http.*
import kotlinx.datetime.LocalDate
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object RngParser {
    private val logger = Logger()

    fun parse(html: Document, url: Url, page: Int, date: LocalDate): RngPage {
        val entries = mutableListOf<RngPage.RngEntry>()
        val podium = html.selectFirst(".rng-board__podium")
        if (podium != null) {
            parsePodium(podium, url, entries)
        }

        val leaderboard = html.selectFirst(".rng-board__list")
        if (leaderboard != null) {
            parseLeaderboard(leaderboard, url, entries)
        } else {
            logger.warn("Failed to find leaderboard in $url")
        }

        var hasNextPage = false

        val pageLinks = html.selectFirst(".rng-board__pages")
        if (pageLinks != null) {
            val links = pageLinks.select(".rng-board__page-link")
            if (links.size != 2) {
                logger.warn("Links in page links element in $url are not 2, but ${links.size}")
            } else {
                val next = links.last()!!
                hasNextPage = !next.hasClass("rng-board__page-link--disabled")
            }
        }

        return RngPage(date, page, hasNextPage, entries)
    }

    private fun parseLeaderboard(
        leaderboard: Element,
        url: Url,
        entries: MutableList<RngPage.RngEntry>
    ) {
        val entriesList = leaderboard.select(".rng-board__row")
        for (entry in entriesList) {
            val rankElement = entry.selectFirst(".rng-board__rank")
            if (rankElement == null) {
                logger.warn("Failed to find rank element in ${entry.cssSelector()} from $url")
                continue
            }
            val rankText = rankElement.text().removePrefix("#")
            val rank = rankText.toIntOrNull()
            if (rank == null) {
                logger.warn("Failed to parse rank in ${rankElement.cssSelector()} from $url")
                continue
            }

            val avatarElement = entry.selectFirst(".rng-board__avatar")
            if (avatarElement == null) {
                logger.warn("Failed to find avatar element in ${entry.cssSelector()} from $url")
                continue
            }
            val avatarUrl = try {
                Url(avatarElement.attr("src"))
            } catch (e: Exception) {
                logger.warn("Failed to parse avatar URL in ${avatarElement.cssSelector()} from $url", e)
                continue
            }
            val usernameElement = entry.selectFirst(".rng-board__name")
            if (usernameElement == null) {
                logger.warn("Failed to find username element in ${entry.cssSelector()} from $url")
                continue
            }
            val username = usernameElement.text()
            if (username.isBlank()) {
                logger.warn("Failed to parse username in ${usernameElement.cssSelector()} from $url")
                continue
            }
            val scoreElement = entry.selectFirst(".rng-board__value")
            if (scoreElement == null) {
                logger.warn("Failed to find score element in ${entry.cssSelector()} from $url")
                continue
            }
            val scoreText = scoreElement.text().replace(",", "")
            val score = scoreText.toIntOrNull()
            if (score == null) {
                logger.warn("Failed to parse score in ${scoreElement.cssSelector()} from $url")
                continue
            }
            entries.add(RngPage.RngEntry(rank, User.FoundUser(username, avatarUrl.toString()), score))
        }
    }

    private fun parsePodium(
        podium: Element,
        url: Url,
        entries: MutableList<RngPage.RngEntry>
    ) {
        val places = podium.select(".rng-board__pedestal")
        for (place in places) {
            val medalElement = place.selectFirst(".rng-board__medal")
            if (medalElement == null) {
                logger.warn("Failed to find medal element in ${place.cssSelector()} from $url")
                continue
            }
            val medal = when (medalElement.text().removePrefix("#")) {
                "*", "★", "1" -> 1
                "2" -> 2
                "3" -> 3
                else -> {
                    logger.warn("Failed to parse medal in ${medalElement.cssSelector()} from $url")
                    continue
                }
            }
            val avatarElement = place.selectFirst(".rng-board__pedestal-avatar")
            if (avatarElement == null) {
                logger.warn("Failed to find avatar element in ${place.cssSelector()} from $url")
                continue
            }
            val avatarUrl = try {
                Url(avatarElement.attr("src"))
            } catch (e: Exception) {
                logger.warn("Failed to parse avatar URL in ${avatarElement.cssSelector()} from $url", e)
                continue
            }
            val usernameElement = place.selectFirst(".rng-board__pedestal-name")
            if (usernameElement == null) {
                logger.warn("Failed to find username element in ${place.cssSelector()} from $url")
                continue
            }
            val username = usernameElement.text()
            if (username.isBlank()) {
                logger.warn("Failed to parse username in ${usernameElement.cssSelector()} from $url")
                continue
            }

            val scoreElement = place.selectFirst(".rng-board__pedestal-value")
            if (scoreElement == null) {
                logger.warn("Failed to find score element in ${place.cssSelector()} from $url")
                continue
            }
            val scoreText = scoreElement.text().replace(",", "")
            val score = scoreText.toIntOrNull()
            if (score == null) {
                logger.warn("Failed to parse score in ${scoreElement.cssSelector()} from $url")
                continue
            }
            entries.add(RngPage.RngEntry(medal, User.FoundUser(username, avatarUrl.toString()), score))
        }
    }
}