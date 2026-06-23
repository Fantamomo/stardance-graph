package com.fantamomo.hc.stardancegraph.db

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.datetime.duration
import org.jetbrains.exposed.v1.datetime.timestamp

object RequestTable : IntIdTable("requests") {

//    // request id, unique per iteration
//    val number = integer("number")


    // the url of the request, including the protocol and path
    val url = varchar("url", 255)
    // the request method, e.g. GET, POST, PUT, DELETE
    val method = varchar("method", 10) // see io.ktor.http.HttpMethod

    val type = varchar("type", 20)


    // the time when the request was sent
    val requestedAt = timestamp("requested_at")
    // the duration after the request was sent until the response was received
    val duration = duration("duration")
    // the response code of the request, e.g. 200 for OK, -1 for failed without response
    val statusCode = short("status_code")


    // name of the class that stored the result from the package com.fantamomo.hc.stardancegraph.model
    // null if it couldn't be parsed
    val result = varchar("result", 20).nullable()


    // the number of bytes sent during the request
    val sendBytes = uinteger("send_bytes").nullable()
    // the number of bytes received during the request
    val receiveBytes = uinteger("receive_bytes").nullable()


    // some stats extracted from the dev-footer on the site
    // can be null if the footer wasn't found (like on follower/following pages)

    // the server build version, should be a git hash, but is currently only "HEAD"
    val serverBuild = varchar("server_build", 40).nullable()
    // the time when the server was built, e.g. "36 minutes ago", "10 hours ago"
    val serverBuildAgo = duration("server_build_ago").nullable()
    // how many queries the server made to serve this request
    val serverDbQueries = ushort("server_db_queries").nullable()
    // how many queries the server didnt need to execute because they were cached
    val serverDbCached = ushort("server_db_cached").nullable()
    // how many cache hits the server had during this request
    val serverCacheHits = ushort("server_cache_hits").nullable()
    // how many cache misses the server had during this request
    val serverCacheMisses = ushort("server_cache_misses").nullable()
    // the average requests the server handles per second (globally)
    val serverRequestPerSecond = double("server_request_per_second").nullable()
    // the number of active users at the time of this request
    val serverActiveUsersSignIn = ushort("server_active_users_sign_in").nullable()
    // the number of visitors at the time of this request
    val serverActiveUsersVisitors = ushort("server_active_users_visitors").nullable()


    // the iteration this request belongs to
    val requestIteration = reference("request_iteration", RequestIterationsTable.id)
}