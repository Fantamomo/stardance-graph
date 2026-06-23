package com.fantamomo.hc.stardancegraph.util

import io.ktor.utils.io.*
import kotlinx.io.Buffer
import org.slf4j.LoggerFactory

// copied and modified from io.ktor.utils.io.CountedByteReadChannel
class CountedByteReadChannel(private val delegate: ByteReadChannel, private val onRead: (Long) -> Unit) :
    ByteReadChannel {
    private val buffer = Buffer()
    private var initial = 0L
    private var consumed = 0L

    val totalBytesRead: Long
        get() {
            updateConsumed()
            return consumed
        }

    override val closedCause: Throwable?
        get() = delegate.closedCause

    override val isClosedForRead: Boolean
        get() = buffer.exhausted() && delegate.isClosedForRead

    @InternalAPI
    override val readBuffer: Buffer
        get() {
            transferFromDelegate()
            return buffer
        }

    @OptIn(InternalAPI::class)
    override suspend fun awaitContent(min: Int): Boolean {
        if (readBuffer.size >= min) {
            return true
        }
        if (delegate.awaitContent(min)) {
            transferFromDelegate()
            return true
        }
        return false
    }

    @OptIn(InternalAPI::class)
    private fun transferFromDelegate() {
        updateConsumed()
        val appended = buffer.transferFrom(delegate.readBuffer)
        initial += appended
    }

    override fun cancel(cause: Throwable?) {
        delegate.cancel(cause)
        buffer.close()
    }

    private fun updateConsumed() {
        val lng = initial - buffer.size
        try {
            onRead(lng)
        } catch (e: Throwable) {
            logger.error("Function onRead has thrown an exception", e)
        }
        consumed += lng
        initial = buffer.size
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CountedByteReadChannel::class.java)
    }
}