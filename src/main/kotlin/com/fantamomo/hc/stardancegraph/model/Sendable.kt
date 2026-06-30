package com.fantamomo.hc.stardancegraph.model

sealed interface Sendable {
    fun printable(): String = this::class.simpleName
        // Sendable is sealed, and simpleName only returns null for anonymous classes,
        // which is not possible here
        ?: throw IllegalStateException("unreachable")

    fun getScrapable(result: MutableSet<Scrapable>)

    fun getScrapable(): Set<Scrapable> = mutableSetOf<Scrapable>().also { getScrapable(it) }
}