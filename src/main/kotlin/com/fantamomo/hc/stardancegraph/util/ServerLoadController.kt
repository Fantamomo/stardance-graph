package com.fantamomo.hc.stardancegraph.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ServerLoadController(
    private val maxRps: Double,
    private val overloadWindowMs: Long = 10_000,
    private val maxPauseMs: Long = 120_000
) {
    private val mutex = Mutex()

    @Volatile
    private var paused = false

    @Volatile
    private var currentWaitMs = 0L

    private var pauseMs = 10_000L
    private var overloadStart = 0L
    private var averageRps = 0.0

    suspend fun update(serverRps: Double) {
        val now = System.currentTimeMillis()

        mutex.withLock {
            averageRps = averageRps * 0.8 + serverRps * 0.2

            if (averageRps > maxRps) {
                if (overloadStart == 0L) {
                    overloadStart = now
                }
                if (!paused && now - overloadStart >= overloadWindowMs) {
                    paused = true
                    currentWaitMs = pauseMs
                }
            } else {
                overloadStart = 0L

                if (paused) {
                    paused = false
                    pauseMs = 10_000L
                    currentWaitMs = 0L
                }
            }
        }
    }

    fun getWaitTimeOrNull(): Duration? {
        if (!paused) return null

        val wait = currentWaitMs
        return if (wait > 0) {
            wait.milliseconds
        } else {
            null
        }
    }

    suspend fun awaitIfNeeded() {
        val wait = getWaitTimeOrNull() ?: return

        mutex.withLock {
            pauseMs = min(
                pauseMs * 2,
                maxPauseMs
            )
            currentWaitMs = pauseMs
        }
        delay(wait)
    }

    fun currentLoad(): Double = averageRps

    companion object {
        operator fun invoke(maxRps: Double, overloadWindow: Duration = 10.seconds, maxPause: Duration = 2.minutes) = ServerLoadController(
            maxRps = maxRps,
            overloadWindowMs = overloadWindow.inWholeMilliseconds,
            maxPauseMs = maxPause.inWholeMilliseconds
        )
    }
}