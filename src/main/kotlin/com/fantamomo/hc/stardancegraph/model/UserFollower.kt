package com.fantamomo.hc.stardancegraph.model

data class UserFollower(
    val user: User,
    val follower: List<User>,
) : Sendable {
    override fun getScrapable() = user.getScrapable() + follower.flatMap { it.getScrapable() }
}