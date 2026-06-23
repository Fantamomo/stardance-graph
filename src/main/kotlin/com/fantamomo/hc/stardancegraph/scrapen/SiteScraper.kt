package com.fantamomo.hc.stardancegraph.scrapen

import com.fantamomo.hc.stardancegraph.data.SharedValues
import com.fantamomo.hc.stardancegraph.model.Scrapable
import com.fantamomo.hc.stardancegraph.model.ScrapedObject
import com.fantamomo.hc.stardancegraph.model.Sendable
import com.fantamomo.hc.stardancegraph.scrapen.parser.DevlogParser
import com.fantamomo.hc.stardancegraph.scrapen.parser.FollowParser
import com.fantamomo.hc.stardancegraph.scrapen.parser.ProjectParser
import com.fantamomo.hc.stardancegraph.scrapen.parser.UserSiteParser
import com.fantamomo.hc.stardancegraph.util.delay.waitingDelay
import com.fantamomo.hc.stardancegraph.util.plugins.network.RECEIVE_BYTES_KEY
import com.fantamomo.hc.stardancegraph.util.plugins.network.SEND_BYTES_KEY
import com.fantamomo.hc.stardancegraph.util.plugins.requests.RequestType
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

class SiteScraper(
    val engine: ScrapEngine,
    val scraped: SendChannel<ScrapedObject>,
    val toScrap: ReceiveChannel<Scrapable>
) {
    suspend fun start() {
        var scraped = 0
        for (element in toScrap) {
            scraped++
            logger.info("[$scraped] Scraping ${element.url}")

            val scrapedObject = scrape(element)

            this.scraped.send(scrapedObject)

            if (scraped % 100 == 0) {
                logger.info("Waiting for 5 seconds")
                waitingDelay(5.seconds)
            } /*else if (scraped % 50 == 0) {
                logger.info("Waiting for 10 seconds")
                waitingDelay(10.seconds)
            }*/
        }
    }

    private suspend fun scrape(element: Scrapable): ScrapedObject {
        val scrapedObject = ScrapedObject.Builder()
        scrapedObject.url = element.url
        scrapedObject.type = RequestType.parse(element.url)
        scrapedObject.method = HttpMethod.Get
        scrapedObject.requestedAt = Clock.System.now()

        val (response, duration) = measureTimedValue {
            try {
                SharedValues.client.get(element.url)
            } catch (e: Exception) {
                logger.error("Failed to scrape ${element.url}", e)
                null
            }
        }
        scrapedObject.duration = duration
        if (response == null) {
            scrapedObject.statusCode = -1
            return scrapedObject.build()
        }
        scrapedObject.statusCode = response.status.value

        if (element is Scrapable.Project && response.status == HttpStatusCode.NotFound) {
            logger.warn("Project not found: ${element.url}")
            engine.project404ErrorCount++
            return scrapedObject.build()
        }

        if (response.status != HttpStatusCode.OK) {
            if (response.status == HttpStatusCode.TooManyRequests) {
                logger.warn("Too many requests, waiting for 5 minutes")
            } else if (response.status.value in 500..599) {
                logger.warn(
                    "Server error, waiting for 1 minutes: ${
                        runCatching { response.bodyAsText() }.getOrNull()?.let { "\"$it\"" } ?: "no body"
                    }")
            } else {
                logger.warn("Failed to scrape ${element.url}, status code: ${response.status}, waiting for 1 minutes")
            }
            waitingDelay(1.minutes)
            return scrapedObject.build()
        }
        val body = try {
            response.bodyAsText()
        } catch (e: Exception) {
            logger.error("Failed to read response body from ${element.url}", e)
            null
        }
        scrapedObject.sendBytes = response.call.attributes.getOrNull(SEND_BYTES_KEY)?.toUInt()
        scrapedObject.receivedBytes = response.call.attributes.getOrNull(RECEIVE_BYTES_KEY)?.toUInt()
        if (body == null) return scrapedObject.build()
        val html = try {
            Jsoup.parse(body)
        } catch (e: Exception) {
            logger.error("Failed to parse HTML from ${element.url}", e)
            return scrapedObject.build()
        }
        scrapedObject.devFooter = extractDevFooter(html)
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
                is Scrapable.PagedUser -> UserSiteParser.parsePagedUser(
                    html,
                    element.url,
                    element.original,
                    element.page
                )

                is Scrapable.UserFollowers -> FollowParser.parseUserFollowers(html, element.url, element.user)
                is Scrapable.UserFollowing -> FollowParser.parseUserFollowing(html, element.url, element.user)
            }
        } catch (e: Exception) {
            logger.error("Failed to analyze ${element.url}", e)
            return scrapedObject.build()
        }
        if (result != null) {
            logger.info("Successfully scraped ${element.url}, result: ${result::class.java.name}")
            scrapedObject.sendable = result
        } else {
            logger.warn("Failed to analyze ${element.url}")
        }
        return scrapedObject.build()
    }

    private fun extractDevFooter(html: Document): ScrapedObject.DevFooter? {
        // followers/following requests don't have a footer, so we just ignore them
        if (html.selectFirst("body")?.selectFirst("> .follow-list") != null) return null
        if (html.selectFirst("body")
                ?.selectFirst("follow-list__empty") != null
        ) return null // also this should never happen, but just in case

        try {
            val footer = html.selectFirst(".dev-footer")
            val text = footer?.text()
            if (text != null) {
                val match = devFooterRegex.matchEntire(text)
                if (match != null) {
                    val (version, time, unit, dbQueries, dbCached, cacheHits, cacheMisses, reqPerSec, activeUsers, visitors) = match.destructured
                    val siteStats = engine.siteStats
                    siteStats.dbQueries.addAndFetch(dbQueries.toInt())
                    siteStats.dbCached.addAndFetch(dbCached.toInt())
                    siteStats.cacheHits.addAndFetch(cacheHits.toInt())
                    siteStats.cacheMisses.addAndFetch(cacheMisses.toInt())
                    return ScrapedObject.DevFooter(
                        build = version,
                        timeAgo = time.toInt().let { time ->
                            when (unit.lowercase()) {
                                "day", "days" -> time.days
                                "hour", "hours" -> time.hours
                                "minute", "minutes" -> time.minutes
                                "second", "seconds" -> time.seconds
                                else -> Duration.ZERO
                            }
                        },
                        dbQueries = dbQueries.toUShort(),
                        dbQueriesCached = dbCached.toUShort(),
                        cacheHits = dbCached.toUShort(),
                        cacheMisses = cacheMisses.toUShort(),
                        requestPerSecond = reqPerSec.toDouble(),
                        signedInUsers = activeUsers.toUShort(),
                        visitors = visitors.toUShort()
                    )
                } else {
                    logger.warn("Found dev footer, but failed to extract: '$text'")
                }
            } else {
                logger.warn("Failed to extract dev footer")
            }
        } catch (e: Exception) {
            logger.error("Failed to extract dev footer", e)
        }
        return null
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SiteScraper::class.java)

        private val devFooterRegex = Regex(
            """Build (.*?) from (?:about )?(\d+) ([a-z]+) ago\. \(DB: (\d+) quer(?:y|ies)?, (\d+) cached\) \(CACHE: (\d+) hits?, (\d+) misses?\) \(([\d.]+) req/sec\) \(Active: (\d+) signed in, (\d+) visitors\)"""
        )
    }
}