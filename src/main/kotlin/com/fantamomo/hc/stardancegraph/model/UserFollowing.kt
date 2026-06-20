package com.fantamomo.hc.stardancegraph.model

class UserFollowing(
    val user: User,
    val following: List<User>,
) : Sendable {
    override fun getScrapable() = user.getScrapable() + following.flatMap { it.getScrapable() }
}