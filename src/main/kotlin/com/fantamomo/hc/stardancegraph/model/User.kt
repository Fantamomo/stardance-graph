package com.fantamomo.hc.stardancegraph.model

sealed interface User : Sendable {
    val name: String
    val avatarUrl: String

    override fun getScrapable(): Set<Scrapable> = setOf(Scrapable.User(name))

    data class UnverifiedUser(
        override val name: String,
        override val avatarUrl: String
    ) : User

    data class FoundUser(
        override val name: String,
        override val avatarUrl: String
    ) : User

    data class ScrapedUser(
        override val name: String,
        override val avatarUrl: String,
        val bio: String,
        val devlogCount: Int,
        val projectsCount: Int,
        val shipCount: Int,
        val votesCount: Int,
        val followerCount: Int,
        val followingCount: Int,
        val achievements: List<String>,
        val posts: List<Post>
    ) : User {
        override fun getScrapable(): Set<Scrapable> {
            val result = mutableSetOf<Scrapable>()
            when (posts.size) {
                0 -> {}
                1 -> result.addAll(posts[0].getScrapable())
                else -> posts.flatMapTo(result) { it.getScrapable() }
            }
            if (followerCount > 0) result.add(Scrapable.UserFollowers(name))
            if (followingCount > 0) result.add(Scrapable.UserFollowing(name))
            return result
        }
    }
}