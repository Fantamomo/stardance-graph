package com.fantamomo.hc.stardancegraph.scrapen

import com.fantamomo.hc.stardancegraph.model.Project
import com.fantamomo.hc.stardancegraph.model.Scrapable
import com.fantamomo.hc.stardancegraph.model.ScrapedObject
import com.fantamomo.hc.stardancegraph.model.User
import com.fantamomo.hc.stardancegraph.scrapen.data.SiteStats
import io.ktor.http.*
import io.ktor.util.collections.*
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
    val databaseChannel = Channel<ScrapedObject>(Channel.UNLIMITED)
    val databaseChannelSize = AtomicInt(0)

    // elements that are found on the site and should be processed
    // SENDER: siteScraper, RECEIVER: progress
    val foundChannel = Channel<ScrapedObject>(Channel.RENDEZVOUS)

    // elements that are to be scraped
    // SENDER: progress, RECEIVER: siteScraper
    val toScrapeChannel = Channel<Scrapable>(Channel.UNLIMITED)

    // links that have already been scraped, so that we don't scrape them again
    val scrapedLinks: MutableSet<Url> = ConcurrentSet()

    // only contains usernames and user ids
    internal val scrapedUsers: MutableSet<Any> = ConcurrentSet()

    val databaseWriter = DatabaseWriter(this, databaseChannel)
    val siteScraper = SiteScraper(this, scraped = foundChannel, toScrap = toScrapeChannel)

    val siteStats = SiteStats()

    // changing this value from the outside will completely break this code
    internal val currentWork = AtomicInt(0)

    // we use these 3 values to fully scrap all projects until 20 consecutive projects fail to scrape with 404
    internal var biggestProjectId = 0
        private set
    internal var biggestSuccessfullyScrapedProjectId = 0
        private set
    internal val project404ErrorCount = AtomicInt(0)

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

    suspend fun run(): Result = supervisorScope {
        launch(CoroutineName("DatabaseWriter")) {
            // starting the database writer
            databaseWriter.start()
        }
        launch(CoroutineName("ScrapProgress")) {
            // starting the mapper which reads from foundChannel(siteScraper) and writes to databaseWriter and toScapeChannel
            progress()
        }
        launch(CoroutineName("SiteScraper")) {
            // starting the site scraper
            siteScraper.start()
        }

        // the database writer needs to be ready before we start scraping
        databaseWriter.waitForReady()
        logger.info("DatabaseWriter is ready")


        logger.info("Starting scraping")
        biggestProjectId = INIT_PROJECTS_SCRAP

        // starting the monster
        for (i in 1..maxOf(INIT_PROJECTS_SCRAP, INIT_USERS_SCRAP)) {
            if (i <= INIT_PROJECTS_SCRAP) {
                val project = Scrapable.Project(i)
                sendToScrapUnique(project)
            }
            if (i <= INIT_USERS_SCRAP) {
                val user = Scrapable.UserId(i)
                sendToScrapUnique(user)
            }
        }

        waitForStop()

        logger.info("Finished scraping")

        return@supervisorScope Result(
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
        var sendLimitError = false
        for (element in foundChannel) {
            databaseChannel.send(element)
            databaseChannelSize.incrementAndFetch()

            val sendable = element.sendable
            if (sendable != null) {
                if (sendable is Project.ScrapedProject) {
                    biggestSuccessfullyScrapedProjectId = maxOf(biggestSuccessfullyScrapedProjectId, sendable.id)
//                logger.info("biggestSuccessfullyScrapedProjectId = $biggestSuccessfullyScrapedProjectId")
                    project404ErrorCount.store(0)
                }

                if (sendable is User.ScrapedUser) {
                    scrapedUsers.add(sendable.name)
                    sendable.internalId?.let { scrapedUsers.add(it) }
                } else if (sendable is User.UnverifiedUser) {
                    scrapedUsers.add(sendable.name)
                    sendable.internalId?.let { scrapedUsers.add(it) }
                }

                val scrapable = sendable.getScrapable()
                if (scrapable.isNotEmpty()) {
                    for (link in scrapable) {
                        if (link is Scrapable.Project) {
                            biggestProjectId = maxOf(biggestProjectId, link.id)
                        }

                        updateStatsFound(link)

                        if (scrapedLinks.add(link.url)) {
                            updateStatsUnique(link)
                            sendToToScrape++
                            if (sendToToScrape <= LIMIT_SCRAPES) {
                                sendtoScrapWithUserCheck(link)
                            } else if (!sendLimitError) {
                                logger.warn("Limit of $LIMIT_SCRAPES scrapes reached, no new 'to scrape' element will be added to the queue")
                                sendLimitError = true
                            }
                        }
                    }
                }
            }

            if (currentWork.decrementAndFetch() <= 0) {
//                logger.info("currentWork = 0, biggestProjectId = $biggestProjectId, biggestSuccessfullyScrapedProjectId = $biggestSuccessfullyScrapedProjectId, project404ErrorCount = $project404ErrorCount")
                var success = false
                val currentProject404ErrorCount = project404ErrorCount.load()
                if (currentProject404ErrorCount <= 100) {
                    for (i in 1..20) {
                        val nextProjectId = maxOf(biggestProjectId, biggestSuccessfullyScrapedProjectId) + 1
                        val project = Scrapable.Project(nextProjectId)
                        biggestProjectId = nextProjectId
                        if (scrapedLinks.add(project.url)) {
                            sendToScrap(project)
                            success = true
                            break
                        }
                    }
                    if (!success) {
                        if (project404ErrorCount.compareAndSet(currentProject404ErrorCount, Int.MAX_VALUE)) {
                            logger.warn("Could not find new project to scrape")
                        } // else { if we fail to set the value, it was set by another thread, and hopefully we can now find a new project to scrape }
                    }
                }
                success
                if (project404ErrorCount.load() > 100) {
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
    }

    private suspend fun sendToScrapUnique(element: Scrapable) {
        if (scrapedLinks.add(element.url)) {
            sendtoScrapWithUserCheck(element)
        }
    }

    private suspend fun sendtoScrapWithUserCheck(element: Scrapable) {
        when (element) {
            is Scrapable.User -> if (scrapedUsers.contains(element.name)) return
            is Scrapable.UserId -> if (scrapedUsers.contains(element.id)) return
            else -> {}
        }
        sendToScrap(element)
    }

    private suspend fun sendToScrap(element: Scrapable) {
        currentWork.incrementAndFetch()
        toScrapeChannel.send(element)
    }

    @Suppress("DuplicatedCode")
    private fun updateStatsFound(scrapable: Scrapable) {
        totalFound++
        when (scrapable.unwrap()) { // there should never be a Scrapable.WrappedScrapable here, but just in case
            is Scrapable.User, is Scrapable.PagedUser, is Scrapable.UserId -> foundUsers++
            is Scrapable.UserFollowers -> foundUserFollowers++
            is Scrapable.UserFollowing -> foundUserFollowing++
            is Scrapable.Project -> foundProjects++
            is Scrapable.ProjectFollowers -> foundProjectFollowers++
            is Scrapable.Devlog -> foundDevlogs++
            is Scrapable.WrappedScrapable<*> -> throw IllegalStateException("Unexpected wrapped scrapable: $scrapable")
        }
    }

    @Suppress("DuplicatedCode")
    private fun updateStatsUnique(scrapable: Scrapable) {
        totalUnique++
        when (scrapable.unwrap()) { // there should never be a Scrapable.WrappedScrapable here, but just in case
            is Scrapable.User, is Scrapable.PagedUser, is Scrapable.UserId -> uniqueUsers++
            is Scrapable.UserFollowers -> uniqueUserFollowers++
            is Scrapable.UserFollowing -> uniqueUserFollowing++
            is Scrapable.Project -> uniqueProjects++
            is Scrapable.ProjectFollowers -> uniqueProjectFollowers++
            is Scrapable.Devlog -> uniqueDevlogs++
            is Scrapable.WrappedScrapable<*> -> throw IllegalStateException("Unexpected wrapped scrapable: $scrapable")
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
        private const val INIT_PROJECTS_SCRAP = 20000
        private const val INIT_USERS_SCRAP = 35300
    }
}