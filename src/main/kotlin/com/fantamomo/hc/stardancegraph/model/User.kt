package com.fantamomo.hc.stardancegraph.model

sealed interface User : Sendable {
    val name: String
    val avatarUrl: String

    override fun getScrapable(): Set<Scrapable> = setOf(Scrapable.User(name))

    data class UnverifiedUser(
        override val name: String,
        override val avatarUrl: String,
        val internalId: Int?
    ) : User {
        init {
            if (name.contains("@")) throw IllegalArgumentException("User name cannot contain an @")
        }
    }

    data class FoundUser(
        override val name: String,
        override val avatarUrl: String
    ) : User {
        init {
            if (name.contains("@")) throw IllegalArgumentException("User name cannot contain an @")
        }
    }

    data class PagedUser(
        val original: ScrapedUser,
        val page: Int,
        val posts: List<Post>,
        val hasMorePages: Boolean,
    ) : User by original {
        override fun getScrapable(): Set<Scrapable> {
            val result = mutableSetOf<Scrapable>()
//            result.addAll(original.getScrapable())
            when (posts.size) {
                 0 -> {}
                 1 -> result.addAll(posts[0].getScrapable())
                else -> posts.flatMapTo(result) { it.getScrapable() }
            }
            if (hasMorePages) result.add(Scrapable.PagedUser(name, page + 1, original))
            return result
        }
    }

    data class ScrapedUser(
        override val name: String,
        override val avatarUrl: String,
        val internalId: Int?,
        val bio: String,
        val devlogCount: Int,
        val projectsCount: Int,
        val shipCount: Int,
        val votesCount: Int,
        val followerCount: Int,
        val followingCount: Int,
        val achievements: List<String>,
        val posts: List<Post>,
        val hasMorePages: Boolean,
    ) : User {
        init {
            if (name.contains("@")) throw IllegalArgumentException("User name cannot contain an @")
        }
        override fun getScrapable(): Set<Scrapable> {
            val result = mutableSetOf<Scrapable>()
            when (posts.size) {
                0 -> {}
                1 -> result.addAll(posts[0].getScrapable())
                else -> posts.flatMapTo(result) { it.getScrapable() }
            }
            if (followerCount > 0) result.add(Scrapable.UserFollowers(name, this))
            if (followingCount > 0) result.add(Scrapable.UserFollowing(name, this))
            if (hasMorePages) result.add(Scrapable.PagedUser(name, 2, this))
            return result
        }
    }
}