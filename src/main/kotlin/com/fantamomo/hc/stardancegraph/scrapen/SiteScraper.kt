package com.fantamomo.hc.stardancegraph.scrapen

import com.fantamomo.hc.stardancegraph.data.SharedValues
import com.fantamomo.hc.stardancegraph.model.Scrapable
import com.fantamomo.hc.stardancegraph.model.ScrapedObject
import com.fantamomo.hc.stardancegraph.model.Sendable
import com.fantamomo.hc.stardancegraph.scrapen.parser.*
import com.fantamomo.hc.stardancegraph.util.RateLimiter
import com.fantamomo.hc.stardancegraph.util.ServerLoadController
import com.fantamomo.hc.stardancegraph.util.delay.waitingDelay
import com.fantamomo.hc.stardancegraph.util.plugins.network.RECEIVE_BYTES_KEY
import com.fantamomo.hc.stardancegraph.util.plugins.network.SEND_BYTES_KEY
import com.fantamomo.hc.stardancegraph.util.plugins.requests.RequestType
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory
import java.net.SocketTimeoutException
import java.nio.channels.ClosedChannelException
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.measureTimedValue

class SiteScraper(
    val engine: ScrapEngine,
    val scraped: SendChannel<ScrapedObject>,
    val toScrap: ReceiveChannel<Scrapable>
) {
    // we have two systems for rate limiting
    // 1. the server has rate limits, so we can't scrape too fast, it allows 120 requests per minute
    private val rateLimiter = RateLimiter(SERVER_RATE_LIMIT_PER_MINUTE)

    // 2. if the server is overloaded, we check this by looking at the dev-footer, which contains the requests per seconds
    //    and if the server is for 10 seconds over 8 requests per second, we are waiting some time to avoid overloading the server
    private val serverLoadController = ServerLoadController(
        maxRps = 8.0,
        overloadWindow = 10.seconds,
        maxPause = 3.minutes
    )

    private val toScrapMutex = Mutex()

    // if we fail to scrape a scrapable, we put it in this channel
    // it is prioritized over toScrap, so that
    private val failedScrapable = Channel<Scrapable.WrappedScrapable<Retry>>(Channel.UNLIMITED)

    private data class Retry(val retryWeight: Int, var actuallyRetries: Int)

    private val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS)
    private val scrapedCount = AtomicInt(0)
    private val totalReceivedElements = AtomicInt(0)

    private val rateLimited = AtomicBoolean(false)
    private var rateLimitedUntil: Instant? = null

    suspend fun start() = coroutineScope {
        repeat(semaphore.availablePermits) { scraperId ->
            launch(CoroutineName("Scraper$$scraperId")) {
                semaphore.withPermit {
                    try {
                        // requesting a resource directly, so that the client can open MAX_CONCURRENT_REQUESTS simultaneously, for faster requests
                        SharedValues.client.get("https://stardance.hackclub.com/up")
                    } catch (_: Throwable) {
                        // ignore
                    }
                    progressSite(scraperId)
                }
            }
        }
    }

    private suspend fun progressSite(scraperId: Int) {
        var localRequests = 0
        var dbOverloaded = false
        while (true) {
            val element = try {
                nextScrapable()
            } catch (_: ClosedChannelException) {
                logger.info("no more work, scraper $scraperId stops its work")
                return
            }
            if (checkOrAddUser(element)) {
                @Suppress("Deprecation")
                scraped.send(ScrapedObject.EMPTY) // we need to send something so that we don't "coroutine lock" (???) (= thread lock) ourselves
                continue
            }

            val rateLimitedUntil = rateLimitedUntil
            if (rateLimitedUntil != null) {
                val duration = rateLimitedUntil - Clock.System.now()
                if (duration.isPositive()) {
//                    logger.warn("Too many requests, waiting for $duration") // no need to log this, it's already logged when we get rate limited
                    // we are rate limited, so we are waiting
                    waitingDelay(duration)
                } else {
                    // we are not rate limited anymore, so we can scrape again
                    if (rateLimited.compareAndSet(expectedValue = true, newValue = false)) {
                        this.rateLimitedUntil = null
                    }
                }
            }

            val dbChannelSize = engine.databaseChannelSize.load()
            if (dbChannelSize > (if (dbOverloaded) 100 else 500)) {
                dbOverloaded = true
                logger.warn("DatabaseWriter is overloaded, waiting for 30 seconds, to give it time to catch up")
                waitingDelay(30.seconds)
                continue
            } else {
                dbOverloaded = false
            }

            val waitTime = serverLoadController.getWaitTimeOrNull()
            if (waitTime != null) {
                logger.warn("RPS was too high for an extended period of time, waiting for $waitTime")
                delay(waitTime)
            }

            rateLimiter.acquire(::waitingDelay)

            val number = scrapedCount.incrementAndFetch()
            localRequests++
            withContext(CoroutineName("Scraper$$scraperId-$number")) {
                if (element is Scrapable.WrappedScrapable<*>) {
                    logger.info("Scraping ${element.unwrap().url} (${(element.data as? Retry)?.retryWeight?.let { "retry $it" } ?: "retry not available"})")
                } else {
                    logger.info("Scraping ${element.url}")
                }

                val scrapedObject = scrapeSite(element, scraperId)

                scraped.send(scrapedObject)
            }

            /*if (localRequests % 100 == 0) {
                logger.info("Waiting for 1 minutes")
                waitingDelay(1.minutes)
            }*/ /*else if (scraped % 50 == 0) {
                logger.info("Waiting for 10 seconds")
                waitingDelay(10.seconds)
            }*/
//            waitingDelay(1.seconds)
        }
    }

    private suspend fun nextScrapable(): Scrapable = toScrapMutex.withLock {
        while (true) {
            val result = select {
                failedScrapable.onReceive {
                    logger.warn("Retrying ${it.unwrap().url} (retry ${it.data.retryWeight})")
                    it
                }
                toScrap.onReceiveCatching {
                    if (it.isClosed) {
//                        logger.info("toScrap channel is closed, stopping") // no need to log it, the receiver of the exception will handle it
                        throw ClosedChannelException()
                    }
                    if (it.isFailure) {
                        logger.error("Error in toScrap channel", it.exceptionOrNull())
                        null
                    } else it.getOrNull()
                }
            }
            if (result == null) {
                continue
            }
            if (totalReceivedElements.incrementAndFetch() % 100 == 0) {
                logger.info("Status: total received elements: ${totalReceivedElements.load()}; scraped sites: ${scrapedCount.load()}; work to do: ${engine.currentWork.load()}")
            }
            return@withLock result
        }
        @Suppress("KotlinUnreachableCode") // well, it is unreachable, but if we remove the unreachable code, the compiler complains that withLock would return Unit, which is not possible
        throw IllegalStateException("unreachable code reached")
    }

    private fun checkOrAddUser(element: Scrapable): Boolean {
        return when (element) {
            is Scrapable.User -> !engine.scrapedUsers.add(element.name)
            is Scrapable.UserId -> !engine.scrapedUsers.add(element.id)
            else -> false
        }
    }

    private suspend fun scrapeSite(toScrap: Scrapable, scraperId: Int): ScrapedObject {
        val element = toScrap.unwrap()

        val scrapedObject = ScrapedObject.Builder()
        scrapedObject.scraperId = scraperId.toShort()
        scrapedObject.url = element.url
        scrapedObject.type = RequestType.parse(element.url)
        scrapedObject.method = HttpMethod.Get
        scrapedObject.requestedAt = Clock.System.now()

        var socketTimeoutException = false

        val (response, duration) = measureTimedValue {
            try {
                SharedValues.client.get(element.url)
            } catch (_: SocketTimeoutException) {
                logger.warn("Socket timed out while scraping ${element.url}") // this mostly happens if we're running the program but then pause it via the debugger
                socketTimeoutException = true
                null
            } catch (e: Exception) {
                logger.error("Failed to scrape ${element.url}", e)
                null
            }
        }

        ((toScrap as? Scrapable.WrappedScrapable<*>)?.data as? Retry)?.let { it.actuallyRetries++ }

        scrapedObject.duration = duration
        if (response == null) {
            scrapedObject.statusCode = -1
            if (socketTimeoutException) {
                toScrap.retry(weight = 1)
            } else {
                toScrap.retry()
            }
            return scrapedObject.build()
        }
        scrapedObject.statusCode = response.status.value

        val body = try {
            response.bodyAsText()
        } catch (e: Exception) {
            logger.error("Failed to read response body from ${element.url}", e)
            null
        }
        scrapedObject.sendBytes = response.call.attributes.getOrNull(SEND_BYTES_KEY)?.toUInt()
        scrapedObject.receivedBytes = response.call.attributes.getOrNull(RECEIVE_BYTES_KEY)?.toUInt()

        if (body == null) {
            toScrap.retry()
            return scrapedObject.build()
        }

        var htmlParseException: Exception? = null
        val html = try {
            Jsoup.parse(body)
        } catch (e: Exception) {
            logger.error("Failed to parse HTML from ${element.url}", e)
            htmlParseException = e
            null
        }
        if (html != null) {
            val devFooter = extractDevFooter(html)
            if (devFooter != null) {
                serverLoadController.update(devFooter.requestPerSecond)
            }
            scrapedObject.devFooter = devFooter
        }

        // we are actually checking this only after the parsing of the html, because some error pages still are html and contains important infos like the dev-footer
        if (element is Scrapable.Project && response.status == HttpStatusCode.NotFound) {
            logger.warn("Project not found: ${element.url}")
            engine.project404ErrorCount.incrementAndFetch()
            return scrapedObject.build()
        }

        if (response.status != HttpStatusCode.OK) {
            if (response.status == HttpStatusCode.TooManyRequests) {
                if (rateLimited.compareAndSet(expectedValue = false, newValue = true)) {
                    val retryAfter = response.headers[HttpHeaders.RetryAfter]?.toLongOrNull()?.seconds ?: 1.minutes
                    rateLimitedUntil = Clock.System.now() + retryAfter
                    logger.warn("Too many requests, waiting for $retryAfter")
                } // else we are already rate limited, so we don't need to do anything
//                failedScrapable.add(element)
                toScrap.retry(weight = 1) // if it is only rate-limited, we set the weight to 1, so that we are going to try it much more often
                return scrapedObject.build()
            } else if (response.status.value in 500..599) {
                var bodyContent = runCatching { response.bodyAsText() }.getOrNull()?.let { "\"$it\"" } ?: "no body"

                if (bodyContent.contains("Something went wrong on our end.") &&
                    bodyContent.contains("If you need help, share this reference code:") &&
                    bodyContent.contains("If you feel like this was a mistake, let us know in")
                ) {
                    // the default stardance 500 error page
                    bodyContent = "Stardance 500 error page"
                }

                if (bodyContent.length > 1000) {
                    bodyContent = bodyContent
                        .substring(0, 1000) +
                            "... (truncated to 1000, original length: ${bodyContent.length})"
                }

                logger.warn("Server error, waiting for 1 minutes: $bodyContent")
            } else if (response.status.value == 404) {
                logger.warn("Requesting ${element.url} returned ${HttpStatusCode.NotFound}")
                return scrapedObject.build()
            } else {
                logger.warn("Failed to scrape ${element.url}, status code: ${response.status}, waiting for 1 minutes")
            }
            waitingDelay(1.minutes)
            toScrap.retry() // only add it after the delay, so that another scraper dont scrape it directly again
            return scrapedObject.build()
        }

        @Suppress("KotlinConstantConditions") // I don't know why intellij says that
        if (html == null || htmlParseException != null) {
            toScrap.retry()
            return scrapedObject.build()
        }

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
                is Scrapable.UserId -> UserSiteParser.parse(html, element.url)
                is Scrapable.PagedUser -> UserSiteParser.parsePagedUser(
                    html,
                    element.url,
                    element.original,
                    element.page
                )

                is Scrapable.UserFollowers -> FollowParser.parseUserFollowers(html, element.url, element.user)
                is Scrapable.UserFollowing -> FollowParser.parseUserFollowing(html, element.url, element.user)
                is Scrapable.RngPage -> RngParser.parse(html, element.url, element.page, element.date)
                is Scrapable.WrappedScrapable<*> -> throw IllegalStateException("Unexpected wrapped scrapable: $element") // should never happen
                is Scrapable.UserProjects -> UserSiteParser.parseUserProjects(html, element.url)
            }
        } catch (e: Exception) {
            logger.error("Failed to analyze ${element.url}", e)
            toScrap.retry()
            return scrapedObject.build()
        }
        if (result != null) {
            logger.info("Successfully scraped ${element.url}, result: ${runCatching { result.printable() }.getOrNull() ?: result::class.simpleName} (found ${result.getScrapable().size} new scrapable's)")
            scrapedObject.sendable = result
        } else {
            logger.warn("Failed to analyze ${element.url}")
            toScrap.retry()
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
                        timeAgo = if (time.isEmpty()) Duration.ZERO else time.toInt().let { time ->
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
                        cacheHits = cacheHits.toUShort(), // all request after 715 are invalid in cacheHits
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

    private suspend fun Scrapable.retry(weight: Int = 10) {
        val retry = ((this as? Scrapable.WrappedScrapable<*>)?.data as? Retry)
        val retryWeight = retry?.retryWeight ?: 0

        if (retryWeight > 30) {
            logger.warn("Failed to scrape ${unwrap().url} after ${retry!!.actuallyRetries} retries")
            return
        }

        val wrapped = Scrapable.WrappedScrapable(
            scrapable = unwrap(),
            data = Retry(
                retryWeight = retryWeight + weight,
                actuallyRetries = retry?.actuallyRetries ?: 0
            )
        )

//        yield()
//        Scraper.scope.launch {
        failedScrapable.send(wrapped)
//        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SiteScraper::class.java)

        private const val MAX_CONCURRENT_REQUESTS = 5

        const val SERVER_RATE_LIMIT_PER_MINUTE = 120
        private const val SERVER_RATE_LIMIT_PER_5_MINUTES = 600

        private val devFooterRegex = Regex(
            """Build (.*?) from (?:(?:about )?(\d+) ([a-z]+)|less than a minute) ago\. \(DB: (\d+) quer(?:y|ies), (\d+) cached\) \(CACHE: (\d+) hits?, (\d+) misses?\) \(([\d.]+) req/sec\) \(Active: (\d+) signed in, (\d+) visitors\)"""
        )
    }
}