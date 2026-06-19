package com.fantamomo.hc.stardancegraph.db

import com.fantamomo.hc.stardancegraph.data.Config
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

object ProgramIterationsTable : Table("program_iterations") {
    val id = integer("id").autoIncrement()
    val start = timestamp("start")
    val end = timestamp("end").nullable()
    val pid = long("pid")
    val environment = varchar("environment", 10).clientDefault { Config.ENVIRONMENT }

    override val primaryKey = PrimaryKey(id)
}