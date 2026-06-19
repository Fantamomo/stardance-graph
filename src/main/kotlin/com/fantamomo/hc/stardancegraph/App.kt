package com.fantamomo.hc.stardancegraph

import com.fantamomo.hc.stardancegraph.data.Config
import com.fantamomo.hc.stardancegraph.db.ProgramIterationsTable
import com.fantamomo.hc.stardancegraph.manager.DatabaseManager
import com.fantamomo.hc.stardancegraph.util.Logger
import org.jetbrains.exposed.v1.r2dbc.insert
import kotlin.properties.Delegates
import kotlin.time.Clock

object App {
    private val logger = Logger()

    var pid by Delegates.notNull<Long>()
        private set
    var programIterationId by Delegates.notNull<Int>()
        private set

    suspend fun start() {
        pid = try {
            ProcessHandle.current().pid()
        } catch (e: Exception) {
            logger.error("Failed to get process id", e)
            return
        }
        logger.info("Starting Stardance Graph with pid $pid")
        init()
        run()
    }

    private suspend fun init() {
        try {
            Config.init()
        } catch (e: Exception) {
            logger.error("Failed to initialize config", e)
            return
        }

        DatabaseManager.init()

        programIterationId = try {
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

    }
}