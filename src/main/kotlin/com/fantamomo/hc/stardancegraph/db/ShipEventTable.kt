package com.fantamomo.hc.stardancegraph.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

object ShipEventTable : Table("ship_events") {
    val internalId = integer("internal_id")
    val project = reference("project", ProjectTable.id)
    val shipNumber = integer("ship_number")
    val createdAt = timestamp("created_at")
    val demoUrl = varchar("demo_url", 2_048)
    val repoUrl = varchar("repo_url", 2_048)
    val devlogCount = integer("devlog_count")
    val hourCount = integer("hour_count")
    val description = varchar("description", 4000)

    val firstSeen = timestamp("first_seen")
    val lastSeen = timestamp("last_seen").nullable()
    val lastSeenIteration = reference("last_seen_iteration", RequestIterationsTable.id).nullable()

    override val primaryKey = PrimaryKey(internalId)
}