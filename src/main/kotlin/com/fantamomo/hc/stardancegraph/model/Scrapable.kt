package com.fantamomo.hc.stardancegraph.model

import io.ktor.http.*

sealed interface Scrapable {
    val url: Url

    data class User(
        val name: String
    ) : Scrapable {
        override val url: Url = Url("https://stardance.hackclub.com/@$name")
    }

    data class UserFollowers(
        val name: String
    ) : Scrapable {
        override val url: Url = Url("https://stardance.hackclub.com/@$name/followers")
    }

    data class UserFollowing(
        val name: String
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
        val id: Int
    ) : Scrapable {
        override val url: Url = Url("https://stardance.hackclub.com/projects/$id/followers")
    }
}