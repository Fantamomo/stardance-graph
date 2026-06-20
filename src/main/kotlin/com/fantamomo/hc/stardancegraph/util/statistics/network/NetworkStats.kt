package com.fantamomo.hc.stardancegraph.util.statistics.network

import kotlin.concurrent.atomics.AtomicLong
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class NetworkStats : AbstractCoroutineContextElement(Key) {

    companion object Key : CoroutineContext.Key<NetworkStats>

    private val sent = AtomicLong(0)
    private val received = AtomicLong(0)

    fun addSendBytes(bytes: Long) {
        sent.addAndFetch(bytes)
    }

    fun addReceivedBytes(bytes: Long) {
        received.addAndFetch(bytes)
    }

    val totalSend: Long
        get() = sent.load()

    val totalReceived: Long
        get() = received.load()
}