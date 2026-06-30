package com.fantamomo.hc.stardancegraph.model

class UserFollowing(
    val user: User,
    val following: List<User>,
) : Sendable {
    override fun printable() = "User Following"

    override fun getScrapable(result: MutableSet<Scrapable>) {
        user.getScrapable(result)
        following.forEach { it.getScrapable(result) }
    }
}