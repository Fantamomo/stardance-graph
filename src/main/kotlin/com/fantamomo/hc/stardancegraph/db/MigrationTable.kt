package com.fantamomo.hc.stardancegraph.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.duration
import org.jetbrains.exposed.v1.datetime.timestamp

// NEVER EVER CREATE MIGRATIONS FOR THIS TABLE
object MigrationTable : Table("migration") {
    val migration = varchar("migration", 255)
    val appliedAt = timestamp("applied_at")
    val took = duration("took")

    override val primaryKey = PrimaryKey(migration)
}