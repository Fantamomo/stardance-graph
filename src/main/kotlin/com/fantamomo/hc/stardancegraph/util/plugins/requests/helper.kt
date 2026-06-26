package com.fantamomo.hc.stardancegraph.util.plugins.requests

import kotlinx.coroutines.currentCoroutineContext

suspend fun currentRequestStatistics(): RequestStatisticsContext? =
    currentCoroutineContext()[RequestStatisticsContext.Key]