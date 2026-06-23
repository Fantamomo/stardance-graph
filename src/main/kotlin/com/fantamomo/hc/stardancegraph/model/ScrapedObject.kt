package com.fantamomo.hc.stardancegraph.model

import com.fantamomo.hc.stardancegraph.util.plugins.requests.RequestType
import io.ktor.http.*
import kotlin.time.Duration
import kotlin.time.Instant

class ScrapedObject(
    val sendable: Sendable?,
    val url: Url,
    val type: RequestType,
    val method: HttpMethod,
    val requestedAt: Instant,
    val duration: Duration,
    val statusCode: Int,
    val sendBytes: UInt?,
    val receivedBytes: UInt?,
    val devFooter: DevFooter?
) {
    data class DevFooter(
        val build: String,
        val timeAgo: Duration,
        val dbQueries: UShort,
        val dbQueriesCached: UShort,
        val cacheHits: UShort,
        val cacheMisses: UShort,
        val requestPerSecond: Double,
        val signedInUsers: UShort,
        val visitors: UShort,
    )

    class Builder {
        var sendable: Sendable? = null
        var url: Url? = null
        var type: RequestType? = null
        var method: HttpMethod? = null
        var requestedAt: Instant? = null
        var duration: Duration? = null
        var statusCode: Int? = null
        var sendBytes: UInt? = null
        var receivedBytes: UInt? = null
        var devFooter: DevFooter? = null

        fun build() = ScrapedObject(
            sendable,
            url!!,
            type!!,
            method!!,
            requestedAt!!,
            duration!!,
            statusCode!!,
            sendBytes,
            receivedBytes,
            devFooter
        )
    }
}