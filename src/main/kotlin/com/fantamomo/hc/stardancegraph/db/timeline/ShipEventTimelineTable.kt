package com.fantamomo.hc.stardancegraph.db.timeline

import com.fantamomo.hc.stardancegraph.db.RequestTable
import com.fantamomo.hc.stardancegraph.db.ShipEventTable
import org.jetbrains.exposed.v1.core.Table

object ShipEventTimelineTable : Table("ship_event_timeline") {
    val internalId = reference("internal_id", ShipEventTable.internalId)

    val pending = bool("pending")
    val returned = bool("returned")
    val demoUrl = varchar("demo_url", 2_048)
    val repoUrl = varchar("repo_url", 2_048)
    val devlogCount = integer("devlog_count")
    val hourCount = integer("hour_count")
    val attachedMission = varchar("attached_mission", 30).nullable()
    val description = varchar("description", 4_500)

    val request = reference("request", RequestTable.id)

    override val primaryKey = PrimaryKey(internalId, request)
}