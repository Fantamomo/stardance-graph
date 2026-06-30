package com.fantamomo.hc.stardancegraph.db

import org.jetbrains.exposed.v1.core.Table

object ShipEventAttachmentsTable : Table("ship_event_attachments") {
    val shipEvent = reference("ship_event_id", ShipEventTable.internalId)
    val number = integer("number")
    val url = varchar("url", 1_000)

    val firstSeen = reference("first_seen", RequestTable.id)
    val lastSeen = reference("last_seen", RequestTable.id)

    override val primaryKey = PrimaryKey(shipEvent, number)
}