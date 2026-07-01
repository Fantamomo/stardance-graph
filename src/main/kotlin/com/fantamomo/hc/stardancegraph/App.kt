package com.fantamomo.hc.stardancegraph

import com.fantamomo.hc.stardancegraph.data.Config
import com.fantamomo.hc.stardancegraph.db.ProgramIterationsTable
import com.fantamomo.hc.stardancegraph.manager.DatabaseManager
import com.fantamomo.hc.stardancegraph.scrapen.Scraper
import com.fantamomo.hc.stardancegraph.util.Logger
import kotlinx.coroutines.*
import org.jetbrains.exposed.v1.r2dbc.insert
import kotlin.properties.Delegates
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

object App {
    private val logger = Logger()

    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    var pid by Delegates.notNull<Long>()
        private set
    var programId by Delegates.notNull<Int>()
        private set

    suspend fun start() {
        pid = try {
            ProcessHandle.current().pid()
        } catch (e: Exception) {
            logger.error("Failed to get process id", e)
            return
        }
        logger.info("Starting Stardance Graph with pid $pid")

        val job = scope.launch {
            init()
            run()
        }
        job.join()
        logger.info("Program iteration $programId finished, exiting ...")
    }

    private suspend fun init() {
        try {
            Config.init()
        } catch (e: Exception) {
            logger.error("Failed to initialize config", e)
            return
        }

        DatabaseManager.init()

        programId = try {
            DatabaseManager.transaction {
                ProgramIterationsTable.insert {
                    it[ProgramIterationsTable.start] = Clock.System.now()
                    it[ProgramIterationsTable.pid] = this@App.pid
                } get ProgramIterationsTable.id
            }
        } catch (e: Exception) {
            logger.error("Failed to insert program iteration", e)
            return
        }
    }

    private suspend fun run() {
        logger.info("Initialized program with iteration $programId")

        while (true) {
            try {
                Scraper.scrape()
            } catch (e: Throwable) {
                logger.error("Scraper failed, waiting for one hour", e)
                repeat(60) { // we want to wait for 1 hour. but if we directly write 1.hours, we could not override it with the debugger if we don't want to wait for 1 hour
                    delay(1.minutes)
                }
            }
        }
    }
}