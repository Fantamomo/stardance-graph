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
import org.jsoup.nodes.Document
import kotlin.time.Duration.Companion.seconds

class SiteScraper(
    val engine: ScrapEngine,
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
            extractDevFooter(html)
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

    private fun extractDevFooter(html: Document) {
        try {
            val footer = html.selectFirst(".dev-footer")
            val text = footer?.text()
            if (text != null) {
                val match = devFooterRegex.matchEntire(text)
                if (match != null) {
                    val (_, time, dbQueries, dbCached, cacheHits, cacheMisses, reqPerSec, activeUsers, visitors) = match.destructured
                    val siteStats = engine.siteStats
                    siteStats.dbQueries.addAndFetch(dbQueries.toInt())
                    siteStats.dbCached.addAndFetch(dbCached.toInt())
                    siteStats.cacheHits.addAndFetch(cacheHits.toInt())
                    siteStats.cacheMisses.addAndFetch(cacheMisses.toInt())
                } else {
                    logger.warn("Found dev footer, but failed to extract: '$text'")
                }
            } else {
                logger.warn("Failed to extract dev footer")
            }
        } catch (e: Exception) {
            logger.error("Failed to extract dev footer", e)
        }
    }

    companion object {
        private val logger = Logger()

        private val devFooterRegex = Regex(
            """Build .*? from (?:about )?(\d+) ([a-z]+) ago\. \(DB: (\d+) queries?, (\d+) cached\) \(CACHE: (\d+) hits?, (\d+) misses?\) \(([\d.]+) req/sec\) \(Active: (\d+) signed in, (\d+) visitors\)"""
        )
    }
}