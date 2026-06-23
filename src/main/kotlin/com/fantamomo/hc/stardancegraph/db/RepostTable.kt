package com.fantamomo.hc.stardancegraph.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

object RepostTable : Table("reposts") {
    // we cannot use a reference here because it is possible that hasn't been scraped yet
    val devlog = integer("devlog")
    val by = reference("by", UserTable.name)
    val createdAt = timestamp("created_at")

    val body = varchar("content", 4_000).nullable()

    val firstSeen = reference("first_seen", RequestTable.id)
    val lastSeen = reference("last_seen", RequestTable.id)

    override val primaryKey = PrimaryKey(devlog, by)
}