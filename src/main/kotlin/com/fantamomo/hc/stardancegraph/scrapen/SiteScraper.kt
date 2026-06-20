package com.fantamomo.hc.stardancegraph.scrapen

import com.fantamomo.hc.stardancegraph.data.SharedValues
import com.fantamomo.hc.stardancegraph.model.Scrapable
import com.fantamomo.hc.stardancegraph.model.Sendable
import com.fantamomo.hc.stardancegraph.util.Logger
import com.fantamomo.hc.stardancegraph.util.statistics.delay.waitingDelay
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import org.jsoup.Jsoup
import kotlin.time.Duration.Companion.seconds

class SiteScraper(
    val scraped: SendChannel<Sendable>,
    val toScrap: ReceiveChannel<Scrapable>
) {
    suspend fun start() {
        for (element in toScrap) {
            waitingDelay(1.seconds)
            logger.info("Scraping ${element.url}")
            val response = try {
                SharedValues.client.get(element.url)
            } catch (e: Exception) {
                logger.error("Failed to scrape ${element.url}", e)
                continue
            }
            val body = try {
                response.bodyAsText()
            } catch (e: Exception) {
                logger.error("Failed to read response body from ${element.url}", e)
                continue
            }
            val html = try {
                Jsoup.parse(body)
            } catch (e: Exception) {
                logger.error("Failed to parse HTML from ${element.url}", e)
                continue
            }
            try {
                when (element) {
                    is Scrapable.Devlog -> TODO()
                    is Scrapable.Project -> TODO()
                    is Scrapable.ProjectFollowers -> TODO()
                    is Scrapable.User -> TODO()
                    is Scrapable.UserFollowers -> TODO()
                    is Scrapable.UserFollowing -> TODO()
                }
            } catch (e: Exception) {
                logger.error("Failed to analyze ${element.url}", e)
            }
        }
    }

    companion object {
        private val logger = Logger()
    }
}