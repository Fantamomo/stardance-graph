package com.fantamomo.hc.stardancegraph.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

object ProjectFollowersTable : Table("project_followers") {
    val project = reference("project", ProjectTable.id)
    val follower = reference("follower", UserTable.name)

    val firstSeen = timestamp("first_seen")
    val lastSeen = timestamp("last_seen")
    val lastSeenIteration = reference("last_seen_iteration", RequestIterationsTable.id)

    override val primaryKey = PrimaryKey(project, follower)
}