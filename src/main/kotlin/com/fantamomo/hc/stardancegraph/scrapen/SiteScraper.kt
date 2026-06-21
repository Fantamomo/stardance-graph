package com.fantamomo.hc.stardancegraph.scrapen

import com.fantamomo.hc.stardancegraph.data.SharedValues
import com.fantamomo.hc.stardancegraph.model.Scrapable
import com.fantamomo.hc.stardancegraph.model.Sendable
import com.fantamomo.hc.stardancegraph.scrapen.site.DevlogParser
import com.fantamomo.hc.stardancegraph.scrapen.site.FollowParser
import com.fantamomo.hc.stardancegraph.scrapen.site.ProjectParser
import com.fantamomo.hc.stardancegraph.scrapen.site.UserSiteParser
import com.fantamomo.hc.stardancegraph.util.statistics.delay.waitingDelay
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.time.Duration.Companion.seconds

class SiteScraper(
    val engine: ScrapEngine,
    val scraped: SendChannel<Sendable>,
    val toScrap: ReceiveChannel<Scrapable>
) {
    suspend fun start() {
        var scraped = 0
        for (element in toScrap) {
            scraped++
            logger.info("[$scraped] Scraping ${element.url}")
            scrape(element)
            if (scraped % 100 == 0) {
                logger.info("Waiting for 30 seconds")
                waitingDelay(30.seconds)
            } else if (scraped % 50 == 0) {
                logger.info("Waiting for 10 seconds")
                waitingDelay(10.seconds)
            }
        }
    }

    private suspend fun scrape(element: Scrapable) {
        val response = try {
            SharedValues.client.get(element.url)
        } catch (e: Exception) {
            logger.error("Failed to scrape ${element.url}", e)
            engine.currentWork.decrementAndFetch() // we failed to scrape, so we need to decrement the work counter
            return
        }
        val body = try {
            response.bodyAsText()
        } catch (e: Exception) {
            logger.error("Failed to read response body from ${element.url}", e)
            engine.currentWork.decrementAndFetch()
            return
        }
        val html = try {
            Jsoup.parse(body)
        } catch (e: Exception) {
            logger.error("Failed to parse HTML from ${element.url}", e)
            return
        }
        extractDevFooter(html)
        val result: Sendable? = try {
            when (element) {
                is Scrapable.Devlog -> DevlogParser.parse(html, element.url)
                is Scrapable.Project -> ProjectParser.parse(html, element.url, element.id)
                is Scrapable.ProjectFollowers -> FollowParser.parseProjectFollowers(
                    html,
                    element.url,
                    element.id,
                    element.owner
                )

                is Scrapable.User -> UserSiteParser.parse(html, element.url)
                is Scrapable.PagedUser -> UserSiteParser.parsePagedUser(html, element.url, element.original, element.page)
                is Scrapable.UserFollowers -> FollowParser.parseUserFollowers(html, element.url, element.user)
                is Scrapable.UserFollowing -> FollowParser.parseUserFollowing(html, element.url, element.user)
            }
        } catch (e: Exception) {
            logger.error("Failed to analyze ${element.url}", e)
            engine.currentWork.decrementAndFetch()
            return
        }
        if (result != null) {
            scraped.send(result)
            logger.info("Successfully scraped ${element.url}, result: ${result::class.java.name}")
        } else {
            logger.warn("Failed to analyze ${element.url}")
            engine.currentWork.decrementAndFetch()
        }
    }

    private fun extractDevFooter(html: Document) {
        // followers/following requests don't have a footer, so we just ignore them
        if (html.selectFirst("body")?.selectFirst("> .follow-list") != null) return
        if (html.selectFirst("body")
                ?.selectFirst("follow-list__empty") != null
        ) return // also this should never happen, but just in case

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
        private val logger = LoggerFactory.getLogger(SiteScraper::class.java)

        private val devFooterRegex = Regex(
            """Build .*? from (?:about )?(\d+) ([a-z]+) ago\. \(DB: (\d+) quer(?:y|ies)?, (\d+) cached\) \(CACHE: (\d+) hits?, (\d+) misses?\) \(([\d.]+) req/sec\) \(Active: (\d+) signed in, (\d+) visitors\)"""
        )
    }
}