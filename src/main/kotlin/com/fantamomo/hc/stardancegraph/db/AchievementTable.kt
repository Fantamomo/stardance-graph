package com.fantamomo.hc.stardancegraph.db

import org.jetbrains.exposed.v1.core.Table

object AchievementTable : Table("achievements") {
    val user = reference("user", UserTable.name)
    val achievement = varchar("achievement", 30)

    val firstSeen = reference("first_seen", RequestTable.id)
    val lastSeen = reference("last_seen", RequestTable.id)

    override val primaryKey = PrimaryKey(user, achievement)
}