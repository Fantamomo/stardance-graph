package com.fantamomo.hc.stardancegraph.util.statistics.requests

import kotlinx.coroutines.currentCoroutineContext

suspend fun currentRequestStatistics(): RequestStatisticsContext? =
    currentCoroutineContext()[RequestStatisticsContext.Key]