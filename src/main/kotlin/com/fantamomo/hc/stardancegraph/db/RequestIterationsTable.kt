package com.fantamomo.hc.stardancegraph.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.duration
import org.jetbrains.exposed.v1.datetime.timestamp

object RequestIterationsTable : Table("request_iterations") {
    // ITERATION INFO
    // the unique id of this iteration
    val id = integer("id").autoIncrement()
    // the program which this iteration belongs to
    val program = reference("program", ProgramIterationsTable.id)
    // the iteration program id, unique within a program, starts by 1 and increments by 1
    val programId = integer("program_id")

    // TIME INFO
    val start = timestamp("start")
    val end = timestamp("end").nullable()
    // time we waited to avoid rate limiting
    val waitingTime = duration("waiting_time").nullable()
    // time spent requesting
    val requestingTime = duration("requesting_time").nullable()

    // REQUEST COUNTS
    // how many requests we made in this iteration
    val totalRequests = integer("total_requests").nullable()
    val requestedUsers = integer("requested_users").nullable()
    val requestedProjects = integer("requested_projects").nullable()
    val requestedDevlogs = integer("requested_devlogs").nullable()
    val requestedProjectFollowers = integer("requested_project_followers").nullable()
    val requestedUserFollowers = integer("requested_user_followers").nullable()
    val requestedUserFollowing = integer("requested_user_following").nullable()

    // FOUND COUNTS
    // how many ids we found in the responses to our requests
    val totalFound = integer("total_found").nullable()
    val foundUsers = integer("found_users").nullable()
    val foundProjects = integer("found_projects").nullable()
    val foundDevlogs = integer("found_devlogs").nullable()
    val foundProjectFollowers = integer("found_project_followers").nullable()
    val foundUserFollowers = integer("found_user_followers").nullable()
    val foundUserFollowing = integer("found_user_following").nullable()

    // UNIQUE COUNTS
    // how many unique ids we found in the responses to our requests
    // (theoretically, this should be the same as requested ids, or at least close to it)
    val totalUnique = integer("total_unique").nullable()
    val uniqueUsers = integer("unique_users").nullable()
    val uniqueProjects = integer("unique_projects").nullable()
    val uniqueDevlogs = integer("unique_devlogs").nullable()
    val uniqueProjectFollowers = integer("unique_project_followers").nullable()
    val uniqueUserFollowers = integer("unique_user_followers").nullable()
    val uniqueUserFollowing = integer("unique_user_following").nullable()

    // ERROR COUNTS
    // how many errors we encountered while processing the responses
    val totalErrors = integer("total_errors").nullable()
    val totalWarnings = integer("total_warnings").nullable()

    override val primaryKey = PrimaryKey(id)
}