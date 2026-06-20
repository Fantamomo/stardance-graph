package com.fantamomo.hc.stardancegraph.data

import com.fantamomo.hc.stardancegraph.util.statistics.network.NetworkStatsPlugin
import com.fantamomo.hc.stardancegraph.util.statistics.requests.RequestType
import com.fantamomo.hc.stardancegraph.util.statistics.requests.StatisticPlugin
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.http.*

object SharedValues {
    val client = HttpClient(CIO) {
        install(StatisticPlugin) {
            requestTypeResolver = resolver@ { request ->
                if (request.url.host != "stardance.hackclub.com") {
                    return@resolver RequestType.UNKNOWN
                }
                val path = request.url.encodedPath.split("/")
                if (path.isEmpty()) return@resolver RequestType.UNKNOWN
                if (path.size == 1 && path[0].startsWith("@")) return@resolver RequestType.USER
                if (path.size == 2 && path[0] == "projects" && path[1].toIntOrNull() != null) return@resolver RequestType.UNKNOWN

                RequestType.UNKNOWN
            }
        }
        install(NetworkStatsPlugin)
    }
}