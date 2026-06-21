package com.fantamomo.hc.stardancegraph.scrapen

import com.fantamomo.hc.stardancegraph.model.Scrapable
import com.fantamomo.hc.stardancegraph.model.Sendable
import com.fantamomo.hc.stardancegraph.scrapen.data.SiteStats
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.slf4j.LoggerFactory
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.measureTime

class ScrapEngine {
    // elements that are sent to the database so that they can be written to the database
    // SENDER: progress, RECEIVER: databaseWriter
    val databaseChannel = Channel<Sendable>(Channel.UNLIMITED)

    // elements that are found on the site and should be processed
    // SENDER: siteScraper, RECEIVER: progress
    val foundChannel = Channel<Sendable>(Channel.RENDEZVOUS)

    // elements that are to be scraped
    // SENDER: progress, RECEIVER: siteScraper
    val toScrapeChannel = Channel<Scrapable>(Channel.UNLIMITED)

    // links that have already been scraped, so that we don't scrape them again
    val scrapedLinks: MutableSet<Url> = mutableSetOf()

    val databaseWriter = DatabaseWriter(this, databaseChannel)
    val siteScraper = SiteScraper(this, scraped = foundChannel, toScrap = toScrapeChannel)

    val siteStats = SiteStats()

    // changing this value from the outside will completely break this code
    internal val currentWork = AtomicInt(0)

    private val stopping: CompletableDeferred<Unit> = CompletableDeferred()

    suspend fun run() = coroutineScope {
        launch {
            // starting the database writer
            databaseWriter.start()
        }
        launch {
            // starting the mapper which reads from foundChannel(siteScraper) and writes to databaseWriter and toScapeChannel
            progress()
        }
        launch {
            // starting the site scraper
            siteScraper.start()
        }

        // the database writer needs to be ready before we start scraping
        databaseWriter.waitForReady()

        // starting the monster
        currentWork.incrementAndFetch()
        toScrapeChannel.send(Scrapable.User("Fantamomo"))

        waitForStop()
    }

    private suspend fun CoroutineScope.waitForStop() {
        stopping.await()
        var dispatched = true
        val job = launch(Dispatchers.Unconfined) {
            databaseWriter.waitForFinished()
            dispatched = false
        }
        if (dispatched) logger.info("Waiting for databaseWriter to finish")
        val waitingDuration = measureTime { job.join() }
        if (dispatched) logger.info("DatabaseWriter finished in $waitingDuration")
        logger.info("Finished")
    }

    private suspend fun progress() {
        var sendToToScrape = 0
        for (element in foundChannel) {
            databaseChannel.send(element)
            val scrapable = element.getScrapable()
            if (scrapable.isNotEmpty()) {
                for (link in scrapable) {
                    if (scrapedLinks.add(link.url)) {
                        sendToToScrape++
                        if (sendToToScrape <= LIMIT_SCRAPES) {
                            currentWork.incrementAndFetch()
                            toScrapeChannel.send(link)
                        }
                    }
                }
            }
            if (currentWork.decrementAndFetch() == 0) {
                logger.info("No more work, stopping")
                try {
                    foundChannel.close()
                } catch (e: Exception) {
                    logger.error("Error closing foundChannel", e)
                }
                try {
                    toScrapeChannel.close()
                } catch (e: Exception) {
                    logger.error("Error closing toScrapeChannel", e)
                }
                databaseWriter.stopSignal()
                stopping.complete(Unit)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ScrapEngine::class.java)

        // testing only: limits the scrap-engine to 100 scrapes (excluding the initial scrape)
        private const val LIMIT_SCRAPES = 100
    }
}