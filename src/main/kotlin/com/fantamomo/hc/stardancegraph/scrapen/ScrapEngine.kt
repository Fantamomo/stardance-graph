package com.fantamomo.hc.stardancegraph.scrapen

import com.fantamomo.hc.stardancegraph.db.ProjectTable
import com.fantamomo.hc.stardancegraph.db.RequestTable
import com.fantamomo.hc.stardancegraph.db.RngTable
import com.fantamomo.hc.stardancegraph.db.UserTable
import com.fantamomo.hc.stardancegraph.manager.DatabaseManager
import com.fantamomo.hc.stardancegraph.model.*
import com.fantamomo.hc.stardancegraph.scrapen.data.SiteStats
import com.fantamomo.hc.stardancegraph.util.daysUntilSequence
import com.fantamomo.hc.stardancegraph.util.humanReadable
import io.ktor.http.*
import io.ktor.util.collections.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.toSet
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.select
import org.slf4j.LoggerFactory
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime
import kotlin.time.times

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
            try {
                databaseWriter.start()
            } catch (e: Exception) {
                logger.error("Error in Database Writer", e)
            }
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
        start()

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

    private suspend fun start() {
        logger.info("Calculating initial projects/users to scrape, this might take a while...")

        val (rngScrapCount, rngDates) = getRngIterator()

        logger.info("Found $rngScrapCount initial daily random number sites to scrape (this number is not near the actual number of scrapes)")

        val projectsToScrap = try {
            getProjectsToScrap(INIT_PROJECTS_SCRAP)
        } catch (e: Exception) {
            logger.error("Failed to get projects to scrape from the database, falling back to all projects", e)
            (1..INIT_PROJECTS_SCRAP).toList()
        }
        logger.info("Found ${projectsToScrap.size} projects to scrape")

        val usersToScrap = try {
            getUsersToScrap(INIT_USERS_SCRAP)
        } catch (e: Exception) {
            logger.error("Failed to get users to scrape from the database, falling back to all users", e)
            (1..INIT_USERS_SCRAP).map { Scrapable.UserId(it) }
        }
        logger.info("Found ${usersToScrap.size} users to scrape")

        val totalItemsToScrape = projectsToScrap.size + usersToScrap.size + rngScrapCount
        logger.info("Found a total of $totalItemsToScrape initial scrapes to perform. (this number will increase as we found more links and pages)")

        val delayBetweenRequests = (60_000.0 / SiteScraper.SERVER_RATE_LIMIT_PER_MINUTE).milliseconds
        val estimatedCompletionTime = totalItemsToScrape * delayBetweenRequests

        logger.info("Estimates time to finish: ${estimatedCompletionTime.humanReadable()}")
        logger.info("(this duration is the minimum time it will take to finish scraping all projects and users, it will increase as we found more links and pages)")

        val maxIterations = listOf(
            projectsToScrap.size,
            usersToScrap.size,
            rngScrapCount
        ).max()

        val projectIterator = projectsToScrap.iterator()
        val userIterator = usersToScrap.iterator()

        logger.info("Sending initial scrapes to site scraper")
        for (i in 1..maxIterations) {
            if (projectIterator.hasNext()) {
                val projectId = projectIterator.next()
                val element = Scrapable.Project(projectId)
                sendToScrapUnique(element)
            }
            if (userIterator.hasNext()) {
                val user = userIterator.next()
                sendToScrapUnique(user)
            }
            if (rngDates.hasNext()) {
                val rngPage = Scrapable.RngPage(rngDates.next())
                sendToScrapUnique(rngPage)
            }
        }
        logger.info("Finished sending initial scrapes to site scraper")
    }

    private suspend fun getUsersToScrap(maxUsersId: Int = 0): List<ScrapableUser> {
        val oneDayAgo = Clock.System.now() - 1.days

        val firstSeenRequest = RequestTable.alias("firstSeenRequest")
        val lastRequestedRequest = RequestTable.alias("lastRequestedRequest")

        return DatabaseManager.transaction {
            val existingUsers = UserTable
                .select(UserTable.name, UserTable.internalId)
                .map {
                    it[UserTable.name] to it[UserTable.internalId]
                }
                .toSet()

            // fast path, if there are no users in the database, we need to scrape all users
            if (existingUsers.isEmpty()) {
                return@transaction if (maxUsersId > 0) {
                    @Suppress("EmptyRange")
                    (1..maxUsersId).map { Scrapable.UserId(it) }
                } else emptyList()
            }

            val existingIds = existingUsers
                .mapNotNullTo(mutableSetOf()) { it.second }

            val missingIds = if (maxUsersId > 0) {
                @Suppress("EmptyRange")
                (1..maxUsersId)
                    .asSequence()
                    .filterNot(existingIds::contains)
                    .map(Scrapable::UserId)
                    .toList()
            } else emptyList()

            val eligibleUsers = UserTable
                .join(
                    firstSeenRequest,
                    JoinType.INNER,
                    UserTable.firstSeen,
                    firstSeenRequest[RequestTable.id]
                )
                .join(
                    lastRequestedRequest,
                    JoinType.INNER,
                    UserTable.lastRequested,
                    lastRequestedRequest[RequestTable.id]
                )
                .select(UserTable.name, UserTable.internalId)
                .where {
                    (UserTable.devlogCount greater 0) or
//                            (UserTable.projectCount greater 0) or // we are not including projects in this list, because many users have one project without a devlog
                            (UserTable.shipCount greater 0) or
                            (UserTable.votesCount greater 0) or
                            (UserTable.followerCount greater 0) or
                            (UserTable.streak.isNotNull()) or
                            (UserTable.pages greater 1) or
                            (firstSeenRequest[RequestTable.requestedAt] greater oneDayAgo) or // we are scraping users that we first found less than a day ago
                            (lastRequestedRequest[RequestTable.requestedAt] lessEq oneDayAgo) // we are scraping users that we last scraped more than a day ago
                }
                .map {
                    it[UserTable.name] to it[UserTable.internalId]
                }
                .toSet()

            buildList {
                addAll(missingIds)

                eligibleUsers.forEach { (name, id) ->
                    add(Scrapable.User(name))
                    id?.let { add(Scrapable.UserId(it)) }
                }
            }
        }
    }


    private suspend fun getProjectsToScrap(maxProjectId: Int = 0): List<Int> {
        val oneDayAgo = Clock.System.now() - 1.days

        val firstSeenRequest = RequestTable.alias("firstSeenRequest")
        val lastRequestedRequest = RequestTable.alias("lastRequestedRequest")

        val projectsToScrap = DatabaseManager.transaction {
            val existingIds = ProjectTable
                .select(ProjectTable.id)
                .map { it[ProjectTable.id] }
                .toSet()

            // fast path, if there are no projects in the database, we need to scrape all projects
            if (existingIds.isEmpty()) return@transaction if (maxProjectId < 1) emptyList() else @Suppress("EmptyRange") (1..maxProjectId).toList()

            val missingIds = if (maxProjectId < 1) emptyList()
            else @Suppress("EmptyRange") (1..maxProjectId)
                .asSequence()
                .filterNot { it in existingIds }
                .toSet()

            val eligibleProjectIds = ProjectTable
                .join(
                    firstSeenRequest,
                    JoinType.INNER,
                    ProjectTable.firstSeen,
                    firstSeenRequest[RequestTable.id]
                )
                .join(
                    lastRequestedRequest,
                    JoinType.INNER,
                    ProjectTable.lastRequested,
                    lastRequestedRequest[RequestTable.id]
                )
                .select(ProjectTable.id)
                .where {
                    (ProjectTable.followerCount greater 0) or
                            (ProjectTable.devlogCount greater 0) or
                            (ProjectTable.totalHours greater 0) or
                            (ProjectTable.postCount greater 0) or
                            (firstSeenRequest[RequestTable.requestedAt] greater oneDayAgo) or
                            (lastRequestedRequest[RequestTable.requestedAt] lessEq oneDayAgo)
                }
                .map { it[ProjectTable.id] }
                .toSet()

            missingIds + eligibleProjectIds
        }

        return projectsToScrap.distinct()
    }

    private suspend fun getRngIterator(): Pair<Int, Iterator<LocalDate>> {
        val dates = try {
            DatabaseManager.transaction {
                RngTable.select(RngTable.date)
                    .withDistinct(true)
                    .orderBy(RngTable.date, SortOrder.DESC)
                    .limit(2)
                    .map { it[RngTable.date] }
                    .toList()
            }
        } catch (e: Exception) {
            logger.error("Failed to get last RNG date", e)
            emptyList()
        }

        val rngLaunchDate = LocalDate(
            year = 2026,
            month = 6,
            day = 15
        ) // from https://github.com/Fantamomo/stardance/blob/dd6a05b73c3796ecd18a80255fb1be68dfd053a7/app/models/daily_roll.rb#L34
        val currentDate = Clock.System.now().toLocalDateTime(TimeZone.UTC).date

        val fromRngScrapDate = when {
            dates.isEmpty() -> rngLaunchDate
            dates.first() == currentDate -> currentDate
            else -> dates.first() // always scrap the same last scraped date (because maybe something has changed)
        }

        val rngDates = fromRngScrapDate.daysUntilSequence(currentDate).iterator()

        return fromRngScrapDate.daysUntil(currentDate) to rngDates
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
    }

    private suspend fun progress() {
        var sendToToScrape = 0
        var sendLimitError = false
        while (true) {
            val elementResult = foundChannel.receiveCatching()

            if (elementResult.isClosed) {
                logger.info("foundChannel is closed")
                break
            }
            if (elementResult.isFailure) {
                logger.error("Error in foundChannel", elementResult.exceptionOrNull())
                continue
            }
            val element = elementResult.getOrNull()
            if (element == null) {
                logger.error("Received null from successfully result, this should never happen")
                continue
            }

            @Suppress("Deprecation")
            if (element != ScrapedObject.EMPTY) {
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
                        scrapedUsers.add(sendable.internalId)
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
//                    try {
//                        foundChannel.close()
//                    } catch (e: Exception) {
//                        logger.error("Error closing foundChannel", e)
//                    }
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
            is Scrapable.RngPage -> {} // ignore
            is Scrapable.UserProjects -> {} // ignore
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
            is Scrapable.RngPage -> {} // ignore
            is Scrapable.UserProjects -> {} // ignore
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

        // limits the scrap-engine to a specific number of scrapes (it is nearly impossible to get to that many scrapes)
        private const val LIMIT_SCRAPES = Int.MAX_VALUE / 2

        private const val INIT_PROJECTS_SCRAP = 29330
        private const val INIT_USERS_SCRAP = 39500
    }
}