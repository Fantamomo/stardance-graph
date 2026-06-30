package com.fantamomo.hc.stardancegraph.db.timeline

import com.fantamomo.hc.stardancegraph.db.ProjectTable
import com.fantamomo.hc.stardancegraph.db.RequestTable
import org.jetbrains.exposed.v1.core.Table

object ProjectTimelineTable : Table("project_timeline") {
    val id = reference("id", ProjectTable.id)

    val title = varchar("title", 300)
    val description = varchar("description", 1_500).nullable()
    val bannerImage = varchar("banner_image", 300).nullable()
    val superstar = bool("superstar")
    val sourceUrl = varchar("source_url", 255).nullable()
    val followerCount = integer("follower_count")
    val devlogCount = integer("devlog_count")
    val totalHours = integer("total_hours")
    val postCount = integer("post_count")
    val isHardware = bool("is_hardware")
    val attachedMission = varchar("attached_mission", 30).nullable()
    val missionShipped = bool("mission_shipped").nullable()

    val request = reference("request", RequestTable.id)

    override val primaryKey = PrimaryKey(id, request)
}