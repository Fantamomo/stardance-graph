package com.fantamomo.hc.stardancegraph.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

object ProjectTable : Table("projects") {
    val id = integer("id")

    val owner = reference("owner", UserTable.name)

    // from app/models/project.rb
    val title = varchar("title", 120).nullable()
    val description = varchar("description", 1_000).nullable()

    val followerCount = integer("followerCount").nullable()
    val devlogCount = integer("devlogCount").nullable()
    val totalHours = integer("totalHours").nullable()

    val firstSeen = timestamp("first_seen")
    val lastRequested = timestamp("last_requested").nullable()
    val lastRequestedIteration = reference("last_requested_iteration", RequestIterationsTable.id).nullable()

    override val primaryKey = PrimaryKey(id)
}