package com.fantamomo.hc.stardancegraph.util

import io.ktor.http.*

fun cachedLinkToSlack(link: Url): String? {
    if (link.host != "cachet.hackclub.com") return null
    val path = link.encodedPath.removePrefix("/")
    if (!path.startsWith("users/U")) return null
    if (!path.endsWith("/r")) return null
    val slackId = path.removeSuffix("/r").removePrefix("users/")
    if (slackId.isBlank()) return null
    if (slackId.contains("/")) return null
    if (slackId.length !in 8..24) return null
    return slackId
}

fun cachedLinkToSlack(link: String): String? = runCatching { cachedLinkToSlack(Url(link)) }.getOrNull()