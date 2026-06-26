package com.fantamomo.hc.stardancegraph.data

import com.fantamomo.hc.stardancegraph.util.plugins.network.NetworkStatsPlugin
import com.fantamomo.hc.stardancegraph.util.plugins.requests.RequestType
import com.fantamomo.hc.stardancegraph.util.plugins.requests.StatisticPlugin
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cookies.*
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import java.util.concurrent.TimeUnit

object SharedValues {
    val client = HttpClient(OkHttp) {
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

        engine {
            config {
                connectionPool(
                    ConnectionPool(
                        maxIdleConnections = 100,
                        keepAliveDuration = 5,
                        timeUnit = TimeUnit.MINUTES
                    )
                )

                val dispatcher = Dispatcher()

                dispatcher.maxRequests = 500
                dispatcher.maxRequestsPerHost = 500

                dispatcher(dispatcher)
            }
        }
    }
}