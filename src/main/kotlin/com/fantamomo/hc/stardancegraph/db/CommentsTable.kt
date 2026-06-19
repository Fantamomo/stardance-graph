package com.fantamomo.hc.stardancegraph.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

object CommentsTable : Table("comments") {
    val devlog = reference("devlog", DevlogTable.id)
    val number = integer("number") // the number of the comment in the devlog
    val author = reference("author", UserTable.name)
    val content = varchar("content", 5_000)
    val created = timestamp("created")

    val firstSeen = timestamp("first_seen")
    val lastSeen = timestamp("last_seen")
    val lastSeenIteration = reference("last_seen_iteration", RequestIterationsTable.id)

    override val primaryKey = PrimaryKey(devlog, number)
}