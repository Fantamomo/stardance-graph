package com.fantamomo.hc.stardancegraph.db.timeline

import com.fantamomo.hc.stardancegraph.db.RequestTable
import com.fantamomo.hc.stardancegraph.db.SuperstarTable
import org.jetbrains.exposed.v1.core.Table

object SuperstarTimelineTable : Table("superstar_timeline") {
    val internalId = reference("internal_id", SuperstarTable.internalId)

    val views = integer("views")
    val reposts = integer("reposts")

    val request = reference("request", RequestTable.id)

    override val primaryKey = PrimaryKey(internalId, request)
}