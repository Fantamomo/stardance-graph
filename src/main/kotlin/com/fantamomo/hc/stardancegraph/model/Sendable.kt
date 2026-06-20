package com.fantamomo.hc.stardancegraph.model

sealed interface Sendable {
    fun getScrapable(): Set<Scrapable>
}