package com.fantamomo.hc.stardancegraph.model

import com.fantamomo.hc.stardancegraph.model.User.ScrapedUser
import io.ktor.http.*

sealed interface Scrapable {
    val url: Url

    data class User(
        val name: String
    ) : Scrapable {
        init {
            if (name.contains("@")) throw IllegalArgumentException("User name cannot contain an @")
        }
        override val url: Url = Url("https://stardance.hackclub.com/@$name")
    }

    data class PagedUser(
        val name: String,
        val page: Int,
        val original: ScrapedUser,
    ) : Scrapable {
        init {
            if (name.contains("@")) throw IllegalArgumentException("User name cannot contain an @")
        }
        override val url: Url = Url("https://stardance.hackclub.com/@$name?page=$page")
    }

    data class UserFollowers(
        val name: String,
        val user: com.fantamomo.hc.stardancegraph.model.User, // yeah, i also dont like this, but it does not work without it
    ) : Scrapable {
        override val url: Url = Url("https://stardance.hackclub.com/@$name/followers")
    }

    data class UserFollowing(
        val name: String,
        val user: com.fantamomo.hc.stardancegraph.model.User, // yeah, i also dont like this, but it does not work without it
    ) : Scrapable {
        override val url: Url = Url("https://stardance.hackclub.com/@$name/following")
    }

    data class Devlog(
        val project: Int,
        val id: Int
    ) : Scrapable {
        override val url: Url = Url("https://stardance.hackclub.com/projects/$project/devlogs/$id")
    }

    data class Project(
        val id: Int
    ) : Scrapable {
        override val url: Url = Url("https://stardance.hackclub.com/projects/$id")
    }

    data class ProjectFollowers(
        val id: Int,
        val owner: com.fantamomo.hc.stardancegraph.model.User, // yeah, i also dont like this, but it does not work without it
    ) : Scrapable {
        override val url: Url = Url("https://stardance.hackclub.com/projects/$id/followers")
    }
}