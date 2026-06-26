package com.fantamomo.hc.stardancegraph.db

import org.jetbrains.exposed.v1.core.Table

object ProjectFollowersTable : Table("project_followers") {
    val project = reference("project", ProjectTable.id)
    val follower = reference("follower", UserTable.name)

    val firstSeen = reference("first_seen", RequestTable.id)
    val lastSeen = reference("last_seen", RequestTable.id)

    override val primaryKey = PrimaryKey(project, follower)
}