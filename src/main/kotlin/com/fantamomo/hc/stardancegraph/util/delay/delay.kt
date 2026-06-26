package com.fantamomo.hc.stardancegraph.util.delay

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlin.time.Duration

suspend fun waitingDelay(duration: Duration) {
    if (duration.isNegative()) return
    val waitingDelay = currentCoroutineContext()[WaitingDelay.Key]
    waitingDelay?.addTime(duration)
    delay(duration)
}