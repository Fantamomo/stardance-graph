package com.fantamomo.hc.stardancegraph.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

object SuperstarTable : Table("superstars") {
    val internalId = integer("internal_id")
    val project = reference("project", ProjectTable.id)
    val author = reference("author", UserTable.name)
    val createdAt = timestamp("created_at")
    val views = integer("views")
    val reposts = integer("reposts")

    val firstSeen = timestamp("first_seen")
    val lastSeen = timestamp("last_seen").nullable()
    val lastSeenIteration = reference("last_seen_iteration", RequestIterationsTable.id).nullable()

    override val primaryKey = PrimaryKey(internalId)
}