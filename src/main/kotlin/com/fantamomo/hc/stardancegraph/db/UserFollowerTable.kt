package com.fantamomo.hc.stardancegraph.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

object UserFollowerTable : Table("user_followers") {
    val user = reference("user", UserTable.name)
    val follower = reference("follower", UserTable.name)

    val firstSeen = timestamp("first_seen")

    val lastSeen = timestamp("last_seen")
    val lastSeenIteration = reference("last_seen_iteration", RequestIterationsTable.id)

    init {
        index(false, user)
        index(false, follower)
    }

    override val primaryKey = PrimaryKey(user, follower)
}