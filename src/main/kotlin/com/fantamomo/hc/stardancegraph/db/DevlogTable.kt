package com.fantamomo.hc.stardancegraph.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.duration
import org.jetbrains.exposed.v1.datetime.timestamp

object DevlogTable : Table("posts") {
    // public id in stardance
    val id = integer("id")

    // the internal id in the stardance database
    val internalId = integer("internal_id")

    val content = varchar("content", 4_000)
    val author = reference("author", UserTable.name)
    val project = reference("project", ProjectTable.id)

    val attachmentsCount = integer("attachments_count").default(0)

    val comments = integer("comments").default(0)
    val reposts = integer("reposts").default(0)
    val likes = integer("likes").default(0)
    val views = integer("views").default(0)

    val createdAt = timestamp("created_at")
    val timeLogged = duration("time_logged")

    val firstSeen = timestamp("first_seen")

    val lastSeen = timestamp("last_seen")
    val lastSeenIteration = reference("last_seen_iteration", RequestIterationsTable.id)

    // those could possibly always be null if the devlog never gets comments
    val lastRequested = timestamp("last_requested").nullable()
    val lastRequestedIteration = reference("last_requested_iteration", RequestIterationsTable.id).nullable()

    override val primaryKey = PrimaryKey(id)
}