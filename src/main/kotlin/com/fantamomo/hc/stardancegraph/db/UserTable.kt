package com.fantamomo.hc.stardancegraph.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.date

object UserTable : Table("users") {
    val name = varchar("name", 50) // the maximum length is 30 in app/models/user.rb, but legacy names could have been longer, so we use maximum length of 50 here
    val avatarUrl = varchar("avatar_url", 255)

    val verified = bool("verified").nullable()

    // the id in the stardance database, if we are able to retrieve it
    val internalId = integer("internal_id").nullable()

    val joinData = date("join_data").nullable()
    val bio = varchar("bio", 1_000).nullable()
    val slackId = varchar("slack_id", 15).nullable()
    val devlogCount = integer("devlog_count").nullable()
    val projectCount = integer("project_count").nullable()
    val shipCount = integer("ship_count").nullable()
    val votesCount = integer("votes_count").nullable()
    val achievementsCount = integer("achievements_count").nullable()
    val followerCount = integer("follower_count").nullable()
    val followingCount = integer("following_count").nullable()
    val streak = integer("streak").nullable()
    val pages = integer("pages").nullable()

    val firstSeen = reference("first_seen", RequestTable.id)
    val lastRequested = reference("last_requested", RequestTable.id)

    override val primaryKey = PrimaryKey(name)
}