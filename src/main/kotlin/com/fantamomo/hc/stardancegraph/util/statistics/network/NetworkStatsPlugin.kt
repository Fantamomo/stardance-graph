package com.fantamomo.hc.stardancegraph.util.statistics.network

import io.ktor.client.plugins.api.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.coroutines.currentCoroutineContext
import org.slf4j.LoggerFactory

private val logger =
    LoggerFactory.getLogger("com.fantamomo.hc.stardancegraph.util.statistics.network.NetworkStatsPlugin")

val NetworkStatsPlugin = createClientPlugin(
    name = "NetworkStats"
) {
    on(SendingRequest) { request, content ->
        currentCoroutineContext()[NetworkStats]?.let { stats ->
            logger.debug("Sending request ${request.url} with body type ${request.bodyType} and (${content::class.java.name}) ")
            val contentLength = content.contentLength
            if (contentLength != null) {
                stats.addSendBytes(contentLength)
            } else {
                // currently only for testing so that we know when the content length is null
                logger.warn("Content length is null for request ${request.url}, with body type ${request.bodyType} and (${content::class.java.name}) ")
            }
        }
    }

    client.responsePipeline.intercept(HttpResponsePipeline.Receive) {
        val (typeInfo, content) = subject
        if (content !is ByteReadChannel) return@intercept
        val networkStats = currentCoroutineContext()[NetworkStats] ?: return@intercept
        val newContent = CountedByteReadChannel(content) {
            networkStats.addReceivedBytes(it)
        }
        proceedWith(HttpResponseContainer(typeInfo, newContent))
    }
}
