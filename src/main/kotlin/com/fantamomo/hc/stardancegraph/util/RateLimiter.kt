package com.fantamomo.hc.stardancegraph.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class RateLimiter(
    requestsPerMinute: Int
) {
    private val mutex = Mutex()

    private val interval: Duration = (60_000.0 / requestsPerMinute).milliseconds

    private var nextAllowedTime = TimeSource.Monotonic.markNow()

    suspend fun acquire() {
        mutex.withLock {
            val remaining = nextAllowedTime.elapsedNow()

            if (remaining.isNegative()) {
                delay(-remaining)
            }

            nextAllowedTime = TimeSource.Monotonic.markNow() + interval
        }
    }

    suspend fun acquire(block: suspend (Duration) -> Unit) {
        mutex.withLock {
            val remaining = nextAllowedTime.elapsedNow()

            if (remaining.isNegative()) {
                block(-remaining)
            }
            nextAllowedTime = TimeSource.Monotonic.markNow() + interval
        }
    }
}