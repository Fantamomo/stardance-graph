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
    val programIteration = integer("program_iteration")


    // TIME INFO

    val start = timestamp("start")
    val end = timestamp("end").nullable()
    // time we waited to avoid rate limiting
    val waitingTime = duration("waiting_time").nullable()
    // time spent requesting
    val requestingTime = duration("requesting_time").nullable()

    val databaseRequests = integer("database_requests").nullable()


    // REQUEST COUNTS
    // how many requests we made in this iteration

    val totalRequests = integer("total_requests").nullable()
    val requestedUsers = integer("requested_users").nullable()
    val requestedUserFollowers = integer("requested_user_followers").nullable()
    val requestedUserFollowing = integer("requested_user_following").nullable()
    val requestedProjects = integer("requested_projects").nullable()
    val requestedProjectFollowers = integer("requested_project_followers").nullable()
    val requestedDevlogs = integer("requested_devlogs").nullable()

    // FOUND COUNTS
    // how many ids we found in the responses to our requests
    val totalFound = integer("total_found").nullable()
    val foundUsers = integer("found_users").nullable()
    val foundUserFollowers = integer("found_user_followers").nullable()
    val foundUserFollowing = integer("found_user_following").nullable()
    val foundProjects = integer("found_projects").nullable()
    val foundProjectFollowers = integer("found_project_followers").nullable()
    val foundDevlogs = integer("found_devlogs").nullable()


    // UNIQUE COUNTS
    // how many unique ids we found in the responses to our requests

    // (theoretically, this should be the same as requested ids, or at least close to it)
    val totalUnique = integer("total_unique").nullable()
    val uniqueUsers = integer("unique_users").nullable()
    val uniqueUserFollowers = integer("unique_user_followers").nullable()
    val uniqueUserFollowing = integer("unique_user_following").nullable()
    val uniqueProjects = integer("unique_projects").nullable()
    val uniqueProjectFollowers = integer("unique_project_followers").nullable()
    val uniqueDevlogs = integer("unique_devlogs").nullable()


    // SITE STATS
    // some stats extracted from the dev-footer on the site
    // likes like: Build HEAD from 36 minutes ago. (DB: 21 queries, 3 cached) (CACHE: 26 hits, 0 misses) (0.8 req/sec) (Active: 80 signed in, 133 visitors)

    // the total number of queries the server made to serve all our requests
    val databaseQueries = integer("database_queries").nullable()
    val databaseCached = integer("database_cached").nullable()
    val cacheHits = integer("cache_hits").nullable()
    val cacheMisses = integer("cache_misses").nullable()

    // NETWORK TRANSFER STATS
    // how many bytes we sent and received
    val totalBytesSent = long("total_bytes_sent").nullable()
    val totalBytesReceived = long("total_bytes_received").nullable()

    // ERROR COUNTS
    // how many errors we encountered while processing the responses
    val totalErrors = integer("total_errors").nullable()
    val totalNonSuccessResponses = integer("total_non_success_responses").nullable()

    override val primaryKey = PrimaryKey(id)
}