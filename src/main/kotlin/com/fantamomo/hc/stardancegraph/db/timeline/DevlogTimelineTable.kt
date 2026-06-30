package com.fantamomo.hc.stardancegraph.db.timeline

import com.fantamomo.hc.stardancegraph.db.DevlogTable
import com.fantamomo.hc.stardancegraph.db.RequestTable
import org.jetbrains.exposed.v1.core.Table

object DevlogTimelineTable : Table("devlog_timeline") {
    // public id in stardance
    val id = reference("id", DevlogTable.id)

    // the maximum length of a devlog is 4000, but our html to markdown converter does not make the devlog like it was written by the user, so it is sometimes longer then 4000
    val content = varchar("content", 4_500)

    val attachmentsCount = integer("attachments_count")

    val comments = integer("comments")
    val reposts = integer("reposts")
    val likes = integer("likes")
    val views = integer("views")

    val request = reference("request", RequestTable.id)

    override val primaryKey = PrimaryKey(id, request)
}