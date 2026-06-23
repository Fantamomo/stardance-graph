package com.fantamomo.hc.stardancegraph.util.plugins.network

import com.fantamomo.hc.stardancegraph.util.CountedByteReadChannel
import io.ktor.client.plugins.api.*
import io.ktor.client.statement.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.currentCoroutineContext
import org.slf4j.LoggerFactory

private val logger =
    LoggerFactory.getLogger("com.fantamomo.hc.stardancegraph.util.statistics.network.NetworkStatsPlugin")

val SEND_BYTES_KEY = AttributeKey<Long>("send_bytes")
val RECEIVE_BYTES_KEY = AttributeKey<Long>("receive_bytes")

val NetworkStatsPlugin = createClientPlugin(
    name = "NetworkStats"
) {
    on(SendingRequest) { request, content ->
        val contentLength = content.contentLength
        if (contentLength != null) {
            request.attributes[SEND_BYTES_KEY] = contentLength
            currentCoroutineContext()[NetworkStats]?.addSendBytes(contentLength)
        } else {
            // currently only for testing so that we know when the content length is null
            logger.warn("Content length is null for request ${request.url}, with body type ${request.bodyType} and (${content::class.java.name}) ")
        }
    }

    client.responsePipeline.intercept(HttpResponsePipeline.Receive) {
        val (typeInfo, content) = subject
        if (content !is ByteReadChannel) return@intercept

        var receivedBytes = 0L
        val stats = currentCoroutineContext()[NetworkStats]
        val newContent = CountedByteReadChannel(content) {
            receivedBytes += it
            context.attributes[RECEIVE_BYTES_KEY] = receivedBytes
            stats?.addReceivedBytes(it)
        }

        proceedWith(HttpResponseContainer(typeInfo, newContent))
    }
}
