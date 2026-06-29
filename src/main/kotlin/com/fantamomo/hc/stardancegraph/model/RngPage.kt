package com.fantamomo.hc.stardancegraph.model

import kotlinx.datetime.LocalDate

data class RngPage(
    val date: LocalDate,
    val page: Int,
    val hasNextPage: Boolean,
    val entries: List<RngEntry>,
) : Sendable {
    override fun printable() = "RNG Page"

    override fun getScrapable(result: MutableSet<Scrapable>) {
        if (hasNextPage) result.add(Scrapable.RngPage(date, page + 1))
        entries.forEach { it.user.getScrapable(result) }
    }

    data class RngEntry(
        val rank: Int,
        val user: User.FoundUser,
        val score: Int,
    )
}