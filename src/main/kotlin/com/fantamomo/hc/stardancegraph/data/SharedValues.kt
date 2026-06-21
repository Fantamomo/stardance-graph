package com.fantamomo.hc.stardancegraph.data

import com.fantamomo.hc.stardancegraph.util.statistics.network.NetworkStatsPlugin
import com.fantamomo.hc.stardancegraph.util.statistics.requests.RequestType
import com.fantamomo.hc.stardancegraph.util.statistics.requests.StatisticPlugin
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.http.*

object SharedValues {
    val client = HttpClient(CIO) {
        install(StatisticPlugin) {
            requestTypeResolver = resolver@{ request ->
                if (request.url.host != "stardance.hackclub.com") {
                    return@resolver RequestType.UNKNOWN
                }
                val path = request.url.encodedPath.removePrefix("/").split("/")
                if (path.isEmpty()) return@resolver RequestType.UNKNOWN
                if (path.size == 1 && path[0].startsWith("@")) return@resolver RequestType.USER
                if (path.size == 2 && path[0].startsWith("@") && path[1] == "followers") return@resolver RequestType.USER_FOLLOWERS
                if (path.size == 2 && path[0].startsWith("@") && path[1] == "following") return@resolver RequestType.USER_FOLLOWING
                if (path.size == 2 && path[0] == "projects" && path[1].toIntOrNull() != null) return@resolver RequestType.PROJECT
                if (path.size == 3 && path[0] == "projects" && path[1].toIntOrNull() != null && path[2] == "followers") return@resolver RequestType.PROJECT_FOLLOWERS
                if (path.size == 4 && path[0] == "projects" && path[1].toIntOrNull() != null && path[2] == "devlogs" && path[3].toIntOrNull() != null) return@resolver RequestType.DEVLOG

                RequestType.UNKNOWN
            }
        }

        install(NetworkStatsPlugin)

        install(UserAgent) {
            agent = "Fantamomo/stardancegraph/1.0 (+https://github.com/Fantamomo; Slack-Id=U0905G0BRU5)"
        }
    }
}