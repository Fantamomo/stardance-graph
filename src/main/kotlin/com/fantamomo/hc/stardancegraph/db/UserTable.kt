package com.fantamomo.hc.stardancegraph.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

object UserTable : Table("users") {
    val name = varchar("name", 50) // the maximum length is 30 in app/models/user.rb, but legacy names could have been longer, so we use maximum length of 50 here

    val slackId = varchar("slack_id", 12).nullable()
    val followerCount = integer("follower_count").nullable()
    val followingCount = integer("following_count").nullable()

    val firstSeen = timestamp("first_seen")
    val lastRequested = timestamp("last_requested").nullable()
    val lastRequestedIteration = integer("last_requested_iteration").nullable()

    override val primaryKey = PrimaryKey(name)
}