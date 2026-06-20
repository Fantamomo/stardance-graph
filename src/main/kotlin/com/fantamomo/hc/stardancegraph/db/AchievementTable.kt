package com.fantamomo.hc.stardancegraph.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

object AchievementTable : Table("achievements") {
    val user = reference("user", UserTable.name)
    val achievement = varchar("achievement", 30)

    val firstSeen = timestamp("first_seen")
    val lastSeen = timestamp("last_seen")
    val lastSeenIteration = reference("last_seen_iteration", RequestIterationsTable.id)

    override val primaryKey = PrimaryKey(user, achievement)
}