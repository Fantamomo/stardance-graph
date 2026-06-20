package com.fantamomo.hc.stardancegraph.util.statistics.requests

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class RequestStatisticsContext(
    val statistics: RequestStatistics
) : AbstractCoroutineContextElement(Key) {

    companion object Key : CoroutineContext.Key<RequestStatisticsContext>
}