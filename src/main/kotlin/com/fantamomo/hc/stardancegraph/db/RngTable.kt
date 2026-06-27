package com.fantamomo.hc.stardancegraph.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.date

object RngTable : Table("rng") {
    val date = date("date")
    val user = reference("user", UserTable.name)
    val rank = integer("rank")
    val score = integer("score")

    val firstSeen = reference("first_seen", RequestTable.id)
    val lastSeen = reference("last_seen", RequestTable.id)

    override val primaryKey = PrimaryKey(date, user)
}