package com.fantamomo.hc.stardancegraph.util.plugins.requests

import io.ktor.http.*

class RequestType private constructor(val name: String) {

    companion object {
        val UNKNOWN = RequestType("unknown")

        val USER = RequestType("user") // /@<username>
        val USER_FOLLOWERS = RequestType("user_followers") // /@<username>/followers
        val USER_FOLLOWING = RequestType("user_following") // /@<username>/following
        val PROJECT = RequestType("project") // /projects/<project_id>
        val PROJECT_FOLLOWERS = RequestType("project_followers") // /projects/<project_id>/followers
        val DEVLOG = RequestType("devlog") // /projects/<project_id>/devlogs/<devlog_id>

        operator fun invoke(name: String): RequestType {
            if (name.isBlank()) throw IllegalArgumentException("Request type name cannot be blank.")
            if (name == "unknown") return UNKNOWN
            return RequestType(name)
        }

        fun parse(url: URLBuilder): RequestType {
            if (url.host != "stardance.hackclub.com") {
                return UNKNOWN
            }
            return parse(url.encodedPath.removePrefix("/").split("/"))
        }
        fun parse(url: Url): RequestType {
            if (url.host != "stardance.hackclub.com") {
                return UNKNOWN
            }
            return parse(url.encodedPath.removePrefix("/").split("/"))
        }

        private fun parse(path: List<String>): RequestType {
            if (path.isEmpty()) return UNKNOWN
            if (path.size == 1 && path[0].startsWith("@")) return USER
            if (path.size == 2 && path[0].startsWith("@") && path[1] == "followers") return USER_FOLLOWERS
            if (path.size == 2 && path[0].startsWith("@") && path[1] == "following") return USER_FOLLOWING
            if (path.size == 2 && path[0] == "users" && path[1].toIntOrNull() != null) return USER
            if (path.size == 2 && path[0] == "projects" && path[1].toIntOrNull() != null) return PROJECT
            if (path.size == 3 && path[0] == "projects" && path[1].toIntOrNull() != null && path[2] == "followers") return PROJECT_FOLLOWERS
            if (path.size == 4 && path[0] == "projects" && path[1].toIntOrNull() != null && path[2] == "devlogs" && path[3].toIntOrNull() != null) return DEVLOG

            return UNKNOWN
        }
    }

    fun copy(name: String = this.name) = invoke(name)
}