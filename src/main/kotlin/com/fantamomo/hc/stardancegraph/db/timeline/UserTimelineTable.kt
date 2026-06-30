package com.fantamomo.hc.stardancegraph.db.timeline

import com.fantamomo.hc.stardancegraph.db.RequestTable
import org.jetbrains.exposed.v1.core.Table

object UserTimelineTable : Table("user_timeline") {
    val internalId = integer("internal_id") // reference to UserTable.internalId is not possible because the column is nullable

    val bio = varchar("bio", 1_000).nullable()
    val devlogCount = integer("devlog_count").nullable()
    val projectCount = integer("project_count").nullable()
    val shipCount = integer("ship_count").nullable()
    val votesCount = integer("votes_count").nullable()
    val achievementsCount = integer("achievements_count").nullable()
    val followerCount = integer("follower_count").nullable()
    val followingCount = integer("following_count").nullable()
    val streak = integer("streak").nullable()

    val request = reference("request", RequestTable.id)

    override val primaryKey = PrimaryKey(internalId, request)
}