package com.fantamomo.hc.stardancegraph.util.plugins.requests

import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Duration

class RequestStatistics {

    val totalRequests = AtomicInt(0)
    val totalTimeMillis = AtomicLong(0)

    val exceptionCount = AtomicInt(0)
    val nonSuccessfulStatusCodeCount = AtomicInt(0)

    val requestsPerType: MutableMap<String, AtomicInt> = ConcurrentHashMap()
    val timePerType: ConcurrentHashMap<String, AtomicLong> = ConcurrentHashMap()

    fun incrementRequest(type: RequestType) {
        totalRequests.incrementAndFetch()
        requestsPerType.computeIfAbsent(type.name) { AtomicInt(0) }.incrementAndFetch()
    }

    fun addTime(type: RequestType, duration: Duration) {
        val milliseconds = duration.inWholeMilliseconds
        totalTimeMillis.addAndFetch(milliseconds)
        timePerType.computeIfAbsent(type.name) { AtomicLong(0) }.addAndFetch(milliseconds)
    }
}