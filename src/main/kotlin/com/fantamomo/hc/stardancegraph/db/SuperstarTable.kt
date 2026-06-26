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

    val firstSeen = reference("first_seen", RequestTable.id)
    val lastSeen = reference("last_seen", RequestTable.id)

    override val primaryKey = PrimaryKey(internalId)
}