package com.fantamomo.hc.stardancegraph.model

data class UserFollower(
    val user: User,
    val follower: List<User>,
) : Sendable {
    override fun printable() = "User Follower"

    override fun getScrapable(result: MutableSet<Scrapable>) {
        user.getScrapable(result)
        follower.forEach { it.getScrapable(result) }
    }
}