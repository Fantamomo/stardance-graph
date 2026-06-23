package com.fantamomo.hc.stardancegraph.data

import com.fantamomo.hc.stardancegraph.util.plugins.network.NetworkStatsPlugin
import com.fantamomo.hc.stardancegraph.util.plugins.requests.RequestType
import com.fantamomo.hc.stardancegraph.util.plugins.requests.StatisticPlugin
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cookies.*
import io.ktor.http.*

object SharedValues {
    val client = HttpClient(CIO) {
        install(StatisticPlugin) {
            requestTypeResolver = resolver@{ request ->
                return@resolver RequestType.parse(request.url)
            }
        }

        install(NetworkStatsPlugin)

        install(UserAgent) {
            agent = "Fantamomo/stardancegraph/1.0 (+https://github.com/Fantamomo; Slack-Id=U0905G0BRU5)"
        }

        install(HttpCookies) {
            storage = AcceptAllCookiesStorage()
        }
    }
}