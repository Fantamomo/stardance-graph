package com.fantamomo.hc.stardancegraph.db

import org.jetbrains.exposed.v1.core.Table

object DevlogAttachmentsTable : Table("devlog_attachments") {
    val id = reference("id", DevlogTable.id)
    val number = integer("number") // a devlog can have maximum 4 attachments
    val url = varchar("url", 1_000)

    override val primaryKey = PrimaryKey(id, number)
}