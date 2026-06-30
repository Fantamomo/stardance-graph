package com.fantamomo.hc.stardancegraph.model

import com.fantamomo.hc.stardancegraph.model.User.ScrapedUser
import io.ktor.http.*
import kotlinx.datetime.LocalDate

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

    data class UserId(
        val id: Int
    ) : Scrapable {
        override val url: Url = Url("https://stardance.hackclub.com/users/$id")
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

    data class UserProjects(
        val name: String
    ) : Scrapable {
        init {
            if (name.contains("@")) throw IllegalArgumentException("User name cannot contain an @")
        }
        override val url: Url = Url("https://stardance.hackclub.com/@$name/projects")
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

    data class RngPage(
        val date: LocalDate,
        val page: Int = 1,
    ) : Scrapable {
        override val url: Url = Url("https://stardance.hackclub.com/rng?date=$date&page=$page")
    }

    data class WrappedScrapable<D>(val scrapable: Scrapable, val data: D) : Scrapable {
        override val url: Url
            get() = throw UnsupportedOperationException("Cannot get url from wrapped scrapable, please unwrap it first")

        override fun unwrap() = scrapable.unwrap()
    }

    fun unwrap(): Scrapable = this
}