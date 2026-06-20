package com.fantamomo.hc.stardancegraph.model

import kotlin.time.Instant

data class Comment(
    val project: Int,
    val number: Int,
    val author: User,
    val body: String,
    val createdAt: Instant,
)