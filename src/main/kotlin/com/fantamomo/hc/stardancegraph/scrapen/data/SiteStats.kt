package com.fantamomo.hc.stardancegraph.scrapen.data

import kotlin.concurrent.atomics.AtomicInt

data class SiteStats(
    val dbQueries: AtomicInt = AtomicInt(0),
    val dbCached: AtomicInt = AtomicInt(0),
    val cacheHits: AtomicInt = AtomicInt(0),
    val cacheMisses: AtomicInt = AtomicInt(0),
)