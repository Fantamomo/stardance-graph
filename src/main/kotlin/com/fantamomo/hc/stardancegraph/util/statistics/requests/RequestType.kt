package com.fantamomo.hc.stardancegraph.util.statistics.requests

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
    }

    fun copy(name: String = this.name) = invoke(name)
}