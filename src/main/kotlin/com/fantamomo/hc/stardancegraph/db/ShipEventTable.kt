package com.fantamomo.hc.stardancegraph.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

object ShipEventTable : Table("ship_events") {
    val internalId = integer("internal_id")
    val project = reference("project", ProjectTable.id)
    val shipNumber = integer("ship_number").nullable()
    val createdAt = timestamp("created_at")
    val pending = bool("pending").default(false)
    val returned = bool("returned").default(false)
    val demoUrl = varchar("demo_url", 2_048)
    val repoUrl = varchar("repo_url", 2_048)
    val devlogCount = integer("devlog_count")
    val hourCount = integer("hour_count")
    val attachedMission = varchar("attached_mission", 30).nullable()

    val description = varchar("description", 4000)

    val firstSeen = reference("first_seen", RequestTable.id)
    val lastSeen = reference("last_seen", RequestTable.id)

    override val primaryKey = PrimaryKey(internalId)
}