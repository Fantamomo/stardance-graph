package com.fantamomo.hc.stardancegraph.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.duration

object ProjectTable : Table("projects") {
    val id = integer("id")

    val owner = reference("owner", UserTable.name)

    // from app/models/project.rb
    val title = varchar("title", 300).nullable()
    val description = varchar("description", 1_500).nullable()
    val bannerImage = varchar("banner_image", 500).nullable()
    val superstar = bool("superstar").nullable()
    val sourceUrl = varchar("source_url", 255).nullable()

    val followerCount = integer("follower_count").nullable()
    val devlogCount = integer("devlog_count").nullable()
    val totalHours = integer("total_hours").nullable()
    val postCount = integer("post_count").nullable()
    val isHardware = bool("is_hardware").nullable()
    val attachedMission = varchar("attached_mission", 30).nullable()
    val missionShipped = bool("mission_shipped").nullable() // true = the project has been shipped which this mission, false = the project is still in development
    val lastUpdated = duration("last_updated").nullable() // how long ago was the project last updated when the site was requested, can only been retrieved with /@<name>/projects
    val lastUpdatedAt = reference("last_updated_at", RequestTable.id).nullable() // the request id of the last time the project was scraped and the lastUpdated field was retrived

    val firstSeen = reference("first_seen", RequestTable.id)
    val lastRequested = reference("last_requested", RequestTable.id)

    override val primaryKey = PrimaryKey(id)
}