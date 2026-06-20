package com.fantamomo.hc.stardancegraph.scrapen

import com.fantamomo.hc.stardancegraph.App
import com.fantamomo.hc.stardancegraph.db.RequestIterationsTable
import com.fantamomo.hc.stardancegraph.manager.DatabaseManager
import com.fantamomo.hc.stardancegraph.util.Logger
import com.fantamomo.hc.stardancegraph.util.statistics.delay.WaitingDelay
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
        val stats = RequestStatistics()
        try {
            withContext(
                waitingDelay + RequestStatisticsContext(stats)
            ) {
                run()
            }
        } catch (e: Throwable) {
            logger.error("Error while scraping", e)
        }


        DatabaseManager.transaction {
            RequestIterationsTable.update(where = { RequestIterationsTable.id eq iterationId }) {
                it[RequestIterationsTable.end] = Clock.System.now()
                it[RequestIterationsTable.waitingTime] = waitingDelay.delayedTime
                it[RequestIterationsTable.requestingTime] = stats.totalTimeMillis.load().milliseconds
                it[RequestIterationsTable.totalRequests] = stats.totalRequests.load()

                val requestsPerType = stats.requestsPerType
                it[RequestIterationsTable.requestedUsers] = requestsPerType[RequestType.USER.name]?.load() ?: 0
                it[RequestIterationsTable.requestedUserFollowers] = requestsPerType[RequestType.USER_FOLLOWERS.name]?.load() ?: 0
                it[RequestIterationsTable.requestedUserFollowing] = requestsPerType[RequestType.USER_FOLLOWING.name]?.load() ?: 0
                it[RequestIterationsTable.requestedProjects] = requestsPerType[RequestType.PROJECT.name]?.load() ?: 0
                it[RequestIterationsTable.requestedProjectFollowers] = requestsPerType[RequestType.PROJECT_FOLLOWERS.name]?.load() ?: 0
                it[RequestIterationsTable.requestedDevlogs] = requestsPerType[RequestType.DEVLOG.name]?.load() ?: 0

                it[RequestIterationsTable.totalErrors] = stats.exceptionCount.load()
                it[RequestIterationsTable.totalNonSuccessResponses] = stats.nonSuccessfulStatusCodeCount.load()
            }
        }
        return stats
    }

    private suspend fun run() {
        val engine = ScrapEngine()
        engine.run()
    }
}