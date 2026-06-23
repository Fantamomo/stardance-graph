package com.fantamomo.hc.stardancegraph.scrapen

import com.fantamomo.hc.stardancegraph.App
import com.fantamomo.hc.stardancegraph.db.RequestIterationsTable
import com.fantamomo.hc.stardancegraph.manager.DatabaseManager
import com.fantamomo.hc.stardancegraph.util.Logger
import com.fantamomo.hc.stardancegraph.util.delay.WaitingDelay
import com.fantamomo.hc.stardancegraph.util.plugins.network.NetworkStats
import com.fantamomo.hc.stardancegraph.util.plugins.requests.RequestStatistics
import com.fantamomo.hc.stardancegraph.util.plugins.requests.RequestStatisticsContext
import com.fantamomo.hc.stardancegraph.util.plugins.requests.RequestType
import kotlinx.coroutines.*
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.update
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.properties.Delegates
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

object Scraper {
    private val logger = Logger()
    val scope = CoroutineScope(App.scope.coroutineContext.let { it + SupervisorJob(it.job) })

    private val running = AtomicBoolean(false)
    private var programIterationId = 0

    private var engine: ScrapEngine? = null

    var iterationId by Delegates.notNull<Int>()
        private set

    suspend fun scrape(): RequestStatistics {
        if (!running.compareAndSet(expectedValue = false, newValue = true)) {
            throw IllegalStateException("Scraping already running")
        }
        programIterationId++
        val job = scope.async {
            start()
        }
        return job.await()
    }

    private suspend fun start(): RequestStatistics {
        iterationId = DatabaseManager.transaction {
            RequestIterationsTable.insert {
                it[RequestIterationsTable.program] = App.programId
                it[RequestIterationsTable.programIteration] = programIterationId
                it[RequestIterationsTable.start] = Clock.System.now()
            } get RequestIterationsTable.id
        }

        val waitingDelay = WaitingDelay()
        val requestStatistics = RequestStatistics()
        val networkStats = NetworkStats()
        val result = try {
            withContext(
                waitingDelay + RequestStatisticsContext(requestStatistics) + networkStats
            ) {
                run()
            }
        } catch (e: Throwable) {
            logger.error("Error while scraping", e)
            null
        }

        val engine = engine

        DatabaseManager.transaction {
            RequestIterationsTable.update(where = { RequestIterationsTable.id eq iterationId }) {
                it[RequestIterationsTable.end] = Clock.System.now()
                it[RequestIterationsTable.waitingTime] = waitingDelay.delayedTime
                it[RequestIterationsTable.requestingTime] = requestStatistics.totalTimeMillis.load().milliseconds
                it[RequestIterationsTable.totalRequests] = requestStatistics.totalRequests.load()

                val requestsPerType = requestStatistics.requestsPerType
                it[RequestIterationsTable.requestedUsers] = requestsPerType[RequestType.USER.name]?.load() ?: 0
                it[RequestIterationsTable.requestedUserFollowers] = requestsPerType[RequestType.USER_FOLLOWERS.name]?.load() ?: 0
                it[RequestIterationsTable.requestedUserFollowing] = requestsPerType[RequestType.USER_FOLLOWING.name]?.load() ?: 0
                it[RequestIterationsTable.requestedProjects] = requestsPerType[RequestType.PROJECT.name]?.load() ?: 0
                it[RequestIterationsTable.requestedProjectFollowers] = requestsPerType[RequestType.PROJECT_FOLLOWERS.name]?.load() ?: 0
                it[RequestIterationsTable.requestedDevlogs] = requestsPerType[RequestType.DEVLOG.name]?.load() ?: 0

                @Suppress("DuplicatedCode")
                if (result != null) {
                    it[RequestIterationsTable.totalFound] = result.totalFound
                    it[RequestIterationsTable.foundUsers] = result.foundUsers
                    it[RequestIterationsTable.foundUserFollowers] = result.foundUserFollowers
                    it[RequestIterationsTable.foundUserFollowing] = result.foundUserFollowing
                    it[RequestIterationsTable.foundProjects] = result.foundProjects
                    it[RequestIterationsTable.foundProjectFollowers] = result.foundProjectFollowers
                    it[RequestIterationsTable.foundDevlogs] = result.foundDevlogs

                    it[RequestIterationsTable.totalUnique] = result.totalUnique
                    it[RequestIterationsTable.uniqueUsers] = result.uniqueUsers
                    it[RequestIterationsTable.uniqueUserFollowers] = result.uniqueUserFollowers
                    it[RequestIterationsTable.uniqueUserFollowing] = result.uniqueUserFollowing
                    it[RequestIterationsTable.uniqueProjects] = result.uniqueProjects
                    it[RequestIterationsTable.uniqueProjectFollowers] = result.uniqueProjectFollowers
                    it[RequestIterationsTable.uniqueDevlogs] = result.uniqueDevlogs
                }

                it[RequestIterationsTable.totalErrors] = requestStatistics.exceptionCount.load()
                it[RequestIterationsTable.totalNonSuccessResponses] = requestStatistics.nonSuccessfulStatusCodeCount.load()

                if (engine != null) {
                    val siteStats = engine.siteStats
                    it[RequestIterationsTable.databaseQueries] = siteStats.dbQueries.load()
                    it[RequestIterationsTable.databaseCached] = siteStats.dbCached.load()
                    it[RequestIterationsTable.cacheHits] = siteStats.cacheHits.load()
                    it[RequestIterationsTable.cacheMisses] = siteStats.cacheMisses.load()

                    it[RequestIterationsTable.databaseRequests] = engine.databaseWriter.databaseRequests
                }

                it[RequestIterationsTable.totalBytesSent] = networkStats.totalSend
                it[RequestIterationsTable.totalBytesReceived] = networkStats.totalReceived
            }
        }
        return requestStatistics
    }

    private suspend fun run(): ScrapEngine.Result {
        engine = ScrapEngine()
        val result = engine!!.run()

        return result
    }
}