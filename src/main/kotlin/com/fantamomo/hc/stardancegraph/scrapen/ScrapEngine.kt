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

    private var totalFound = 0
    private var foundUsers = 0
    private var foundUserFollowers = 0
    private var foundUserFollowing = 0
    private var foundProjects = 0
    private var foundProjectFollowers = 0
    private var foundDevlogs = 0

    private var totalUnique = 0
    private var uniqueUsers = 0
    private var uniqueUserFollowers = 0
    private var uniqueUserFollowing = 0
    private var uniqueProjects = 0
    private var uniqueProjectFollowers = 0
    private var uniqueDevlogs = 0

    suspend fun run(): Result = coroutineScope {
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
        for (projectId in 1..100) {
            currentWork.incrementAndFetch()
            toScrapeChannel.send(Scrapable.Project(projectId))
        }

        waitForStop()

        return@coroutineScope Result(
            totalFound = totalFound,
            foundUsers = foundUsers,
            foundUserFollowers = foundUserFollowers,
            foundUserFollowing = foundUserFollowing,
            foundProjects = foundProjects,
            foundProjectFollowers = foundProjectFollowers,
            foundDevlogs = foundDevlogs,
            totalUnique = totalUnique,
            uniqueUsers = uniqueUsers,
            uniqueUserFollowers = uniqueUserFollowers,
            uniqueUserFollowing = uniqueUserFollowing,
            uniqueProjects = uniqueProjects,
            uniqueProjectFollowers = uniqueProjectFollowers,
            uniqueDevlogs = uniqueDevlogs
        )
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
                    updateStatsFound(link)
                    if (scrapedLinks.add(link.url)) {
                        updateStatsUnique(link)
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

    @Suppress("DuplicatedCode")
    private fun updateStatsFound(scrapable: Scrapable) {
        totalFound++
        when (scrapable) {
            is Scrapable.User, is Scrapable.PagedUser -> foundUsers++
            is Scrapable.UserFollowers -> foundUserFollowers++
            is Scrapable.UserFollowing -> foundUserFollowing++
            is Scrapable.Project -> foundProjects++
            is Scrapable.ProjectFollowers -> foundProjectFollowers++
            is Scrapable.Devlog -> foundDevlogs++
        }
    }

    @Suppress("DuplicatedCode")
    private fun updateStatsUnique(scrapable: Scrapable) {
        totalUnique++
        when (scrapable) {
            is Scrapable.User, is Scrapable.PagedUser -> uniqueUsers++
            is Scrapable.UserFollowers -> uniqueUserFollowers++
            is Scrapable.UserFollowing -> uniqueUserFollowing++
            is Scrapable.Project -> uniqueProjects++
            is Scrapable.ProjectFollowers -> uniqueProjectFollowers++
            is Scrapable.Devlog -> uniqueDevlogs++
        }
    }

    class Result(
        val totalFound: Int,
        val foundUsers: Int,
        val foundUserFollowers: Int,
        val foundUserFollowing: Int,
        val foundProjects: Int,
        val foundProjectFollowers: Int,
        val foundDevlogs: Int,
        val totalUnique: Int,
        val uniqueUsers: Int,
        val uniqueUserFollowers: Int,
        val uniqueUserFollowing: Int,
        val uniqueProjects: Int,
        val uniqueProjectFollowers: Int,
        val uniqueDevlogs: Int,
    )

    companion object {
        private val logger = LoggerFactory.getLogger(ScrapEngine::class.java)

        // testing only: limits the scrap-engine to a specific number of scrapes
        private const val LIMIT_SCRAPES = Int.MAX_VALUE / 2
    }
}