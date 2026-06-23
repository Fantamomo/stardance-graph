package com.fantamomo.hc.stardancegraph.db

import org.jetbrains.exposed.v1.core.Table

object UserFollowerTable : Table("user_followers") {
    val user = reference("user", UserTable.name)

    // the person who follows the user
    val follower = reference("follower", UserTable.name)

    val firstSeen = reference("first_seen", RequestTable.id)

    val lastSeen = reference("last_seen", RequestTable.id)

    init {
        index(false, user)
        index(false, follower)
    }

    override val primaryKey = PrimaryKey(user, follower)
}