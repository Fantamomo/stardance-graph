package com.fantamomo.hc.stardancegraph.scrapen

import com.fantamomo.hc.stardancegraph.App
import com.fantamomo.hc.stardancegraph.db.RequestIterationsTable
import com.fantamomo.hc.stardancegraph.manager.DatabaseManager
import com.fantamomo.hc.stardancegraph.util.Logger
import com.fantamomo.hc.stardancegraph.util.statistics.delay.WaitingDelay
import com.fantamomo.hc.stardancegraph.util.statistics.network.NetworkStats
import com.fantamomo.hc.stardancegraph.util.statistics.requests.RequestStatistics
import com.fantamomo.hc.stardancegraph.util.statistics.requests.RequestStatisticsContext
import com.fantamomo.hc.stardancegraph.util.statistics.requests.RequestType
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
        try {
            withContext(
                waitingDelay + RequestStatisticsContext(requestStatistics) + networkStats
            ) {
                run()
            }
        } catch (e: Throwable) {
            logger.error("Error while scraping", e)
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

    private suspend fun run() {
        engine = ScrapEngine()
        engine!!.run()
    }
}