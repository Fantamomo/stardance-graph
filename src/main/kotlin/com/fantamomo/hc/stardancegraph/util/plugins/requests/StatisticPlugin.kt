package com.fantamomo.hc.stardancegraph.util.plugins.requests

import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Clock
import kotlin.time.Instant

class StatisticPluginConfig {

    var requestTypeResolver: (HttpRequestBuilder) -> RequestType = { RequestType.UNKNOWN }
}

private val StartTimeKey = AttributeKey<Instant>("request-start-time")
private val RequestTypeKey = AttributeKey<RequestType>("request-type")

val StatisticPlugin = createClientPlugin(
    name = "StatisticPlugin",
    createConfiguration = ::StatisticPluginConfig
) {

    val resolver = pluginConfig.requestTypeResolver

    onRequest { request, content ->

        val stats = currentRequestStatistics()?.statistics ?: return@onRequest

        val type = resolver(request)

        stats.incrementRequest(type)

        request.attributes.put(StartTimeKey, Clock.System.now())
        request.attributes.put(RequestTypeKey, type)
    }

    onResponse { response ->

        val stats = currentRequestStatistics()?.statistics ?: return@onResponse

        val startTime = response.call.attributes[StartTimeKey]
        val requestType = response.call.attributes[RequestTypeKey]

        val duration = Clock.System.now() - startTime

        stats.addTime(requestType, duration)

        if (!response.status.isSuccess()) {
            stats.nonSuccessfulStatusCodeCount.incrementAndFetch()
        }
    }

    on(Send) { request ->
        val stats = currentRequestStatistics()?.statistics

        try {
            proceed(request)
        } catch (e: Throwable) {
            if (stats != null) {
                stats.exceptionCount.incrementAndFetch()

                val startTime = request.attributes.getOrNull(StartTimeKey)
                val requestType = request.attributes.getOrNull(RequestTypeKey)

                if (startTime != null && requestType != null) {
                    stats.addTime(requestType, Clock.System.now() - startTime)
                }
            }
            throw e
        }
    }
}