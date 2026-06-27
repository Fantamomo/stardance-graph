package com.fantamomo.hc.stardancegraph.model

import kotlinx.datetime.LocalDate

data class RngPage(
    val date: LocalDate,
    val page: Int,
    val hasNextPage: Boolean,
    val entries: List<RngEntry>,
) : Sendable {
    data class RngEntry(
        val rank: Int,
        val user: User.FoundUser,
        val score: Int,
    )

    override fun getScrapable(): Set<Scrapable> {
        val result = mutableSetOf<Scrapable>()
        if (hasNextPage) result.add(Scrapable.RngPage(date, page + 1))
        entries.forEach { result.addAll(it.user.getScrapable()) }
        return result
    }
}