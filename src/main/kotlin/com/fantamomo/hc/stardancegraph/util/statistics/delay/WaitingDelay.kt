package com.fantamomo.hc.stardancegraph.util.statistics.delay

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

class WaitingDelay : AbstractCoroutineContextElement(Key) {

    companion object Key : CoroutineContext.Key<WaitingDelay>

    private val mutex = Mutex()
    var delayedTime = Duration.ZERO
        private set

    suspend fun addTime(duration: Duration) {
        mutex.withLock {
            delayedTime += duration
        }
    }
}