package com.fantamomo.hc.stardancegraph.db

import org.jetbrains.exposed.v1.core.Table

object ProjectTable : Table("projects") {
    val id = integer("id")

    val owner = reference("owner", UserTable.name)

    // from app/models/project.rb
    val title = varchar("title", 120).nullable()
    val description = varchar("description", 1_000).nullable()
    val superstar = bool("superstar").nullable()

    val followerCount = integer("follower_count").nullable()
    val devlogCount = integer("devlog_count").nullable()
    val totalHours = integer("total_hours").nullable()
    val postCount = integer("post_count").nullable()
    val isHardware = bool("is_hardware").nullable()
    val attachedMission = varchar("attached_mission", 30).nullable()
    val missionShipped = bool("mission_shipped").nullable() // true = the project has been shipped which this mission, false = the project is still in development

    val firstSeen = reference("first_seen", RequestTable.id)
    val lastRequested = reference("last_requested", RequestTable.id)

    override val primaryKey = PrimaryKey(id)
}