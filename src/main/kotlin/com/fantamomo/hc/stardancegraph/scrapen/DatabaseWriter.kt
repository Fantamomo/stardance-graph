package com.fantamomo.hc.stardancegraph.scrapen

import com.fantamomo.hc.stardancegraph.db.*
import com.fantamomo.hc.stardancegraph.manager.DatabaseManager
import com.fantamomo.hc.stardancegraph.model.*
import com.fantamomo.hc.stardancegraph.util.cachedLinkToSlack
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.r2dbc.batchUpsert
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.upsert
import org.slf4j.LoggerFactory
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.incrementAndFetch

class DatabaseWriter(val engine: ScrapEngine, val channel: ReceiveChannel<ScrapedObject>) {
    // 0 = found, 1 = unverified, 2 = scraped
    private val existingUsers: MutableMap<String, Byte> = mutableMapOf()

    // false = found, true = scraped
    private val existingProjects: MutableMap<Int, Boolean> = mutableMapOf()

    private val ready = CompletableDeferred<Unit>()
    private val finished = CompletableDeferred<Unit>()

    private val databaseRequestsInternal = AtomicInt(0)
    private val shouldStop = AtomicBoolean(false)

    val databaseRequests: Int
        get() = databaseRequestsInternal.load()

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun start() = coroutineScope {

        DatabaseManager.transaction {
            UserTable.select(UserTable.name)
                .map { it[UserTable.name] }
                .toList()
        }.forEach { existingUsers[it] = 0 }
        databaseRequestsInternal.incrementAndFetch()

        ready.complete(Unit)

        if (shouldStop.load()) {
            finished.complete(Unit)
            return@coroutineScope
        }

        while (isActive && !channel.isClosedForReceive) {
            val elements = mutableListOf<ScrapedObject>()
            elements.add(channel.receive())
            while (isActive && !channel.isClosedForReceive) {
                val element = channel.tryReceive()
                if (element.isSuccess) elements.add(element.getOrThrow())
                else break
            }
            if (elements.isNotEmpty()) {
                saveToDatabase(elements)
            }
            @OptIn(ExperimentalCoroutinesApi::class)
            if (shouldStop.load()) {
                if (channel.isEmpty) {
                    finished.complete(Unit)
                    return@coroutineScope
                }
                // the channel is not empty, so we need to wait for it to become empty
            }
        }
    }

    suspend fun waitForReady() = ready.await()

    suspend fun waitForFinished() = finished.await()

    suspend fun stopSignal() {
        shouldStop.store(true)
    }

    private suspend fun saveToDatabase(elements: List<ScrapedObject>) {
        try {
            DatabaseManager.transaction {
                databaseRequestsInternal.incrementAndFetch()
                elements.forEach { element ->
                    try {
                        insert(element)
                    } catch (e: Exception) {
                        logger.error("Error saving ${element::class.java.name} to database", e)
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error saving to database", e)
        }
    }

    private suspend fun insert(obj: ScrapedObject) {
        val sendable = obj.sendable
        val requestId = RequestTable.insertAndGetId {
            it[RequestTable.url] = obj.url.toString()
            it[RequestTable.method] = obj.method.value
            it[RequestTable.type] = obj.type.name

            it[RequestTable.requestedAt] = obj.requestedAt
            it[RequestTable.duration] = obj.duration
            it[RequestTable.statusCode] = obj.statusCode.toShort()
            it[RequestTable.result] =
                sendable?.let { sendable -> sendable::class.java.simpleName }

            it[RequestTable.sendBytes] = obj.sendBytes
            it[RequestTable.receiveBytes] = obj.receivedBytes

            val footer = obj.devFooter
            if (footer != null) {
                it[RequestTable.serverBuild] = footer.build
                it[RequestTable.serverBuildAgo] = footer.timeAgo
                it[RequestTable.serverDbQueries] = footer.dbQueries
                it[RequestTable.serverDbCached] = footer.dbQueriesCached
                it[RequestTable.serverCacheHits] = footer.cacheHits
                it[RequestTable.serverCacheMisses] = footer.cacheMisses
                it[RequestTable.serverRequestPerSecond] = footer.requestPerSecond
                it[RequestTable.serverActiveUsersSignIn] = footer.signedInUsers
                it[RequestTable.serverActiveUsersVisitors] = footer.visitors
            }

            it[RequestTable.requestIteration] = Scraper.iterationId
        }
        if (sendable != null) {
            insertSendable(sendable, requestId.value)
        }
    }

    private suspend fun insertSendable(element: Sendable, requestId: Int) {
        when (element) {
            is Post -> insertPost(element, requestId)
            is User -> insertUser(element, requestId)
            is Project -> insertProject(element, requestId)
            is ProjectFollowers -> insertProjectFollowers(element, requestId)
            is UserFollower -> insertUserFollowers(element, requestId)
            is UserFollowing -> insertUserFollowing(element, requestId)
        }
    }

    private suspend fun insertPost(element: Post, requestId: Int) {
        when (element) {
            is Devlog -> insertDevlog(element, requestId)
            is Repost -> insertRepost(element, requestId)
            is ShipEvent -> insertShipEvent(element, requestId)
            is SuperStar -> insertSuperStar(element, requestId)
        }
    }

    private suspend fun insertUserFollowing(element: UserFollowing, requestId: Int) {
        insertMissingUser(element.user, requestId)
        for (follower in element.following) insertMissingUser(follower, requestId)
        UserFollowerTable.batchUpsert(
            element.following,
            onUpdateExclude = listOf(UserFollowerTable.firstSeen)
        ) {
            this[UserFollowerTable.follower] = element.user.name
            this[UserFollowerTable.user] = it.name
            this[UserFollowerTable.firstSeen] = requestId
            this[UserFollowerTable.lastSeen] = requestId
        }
    }

    private suspend fun insertUserFollowers(element: UserFollower, requestId: Int) {
        insertMissingUser(element.user, requestId)
        for (follower in element.follower) insertMissingUser(follower, requestId)
        UserFollowerTable.batchUpsert(
            element.follower,
            onUpdateExclude = listOf(UserFollowerTable.firstSeen)
        ) {
            this[UserFollowerTable.follower] = it.name
            this[UserFollowerTable.user] = element.user.name
            this[UserFollowerTable.firstSeen] = requestId
            this[UserFollowerTable.lastSeen] = requestId
        }
    }

    private suspend fun insertProjectFollowers(element: ProjectFollowers, requestId: Int) {
        insertMissingProject(element.project, element.owner, requestId)
        for (follower in element.followers) insertMissingUser(follower, requestId)
        ProjectFollowersTable.batchUpsert(
            element.followers,
            onUpdateExclude = listOf(ProjectFollowersTable.firstSeen)
        ) {
            this[ProjectFollowersTable.project] = element.project
            this[ProjectFollowersTable.follower] = it.name

            this[ProjectFollowersTable.firstSeen] = requestId
            this[ProjectFollowersTable.lastSeen] = requestId
        }
    }

    private suspend fun insertMissingProject(id: Int, owner: User, requestId: Int) {
        insertMissingUser(owner, requestId)
        val existing = existingProjects[id]
        if (existing == null) {
            insertFoundProject(Project.FoundProject(id, owner), requestId)
        }
    }

    private suspend fun insertProject(element: Project, requestId: Int) {
        when (element) {
            is Project.FoundProject -> insertFoundProject(element, requestId)
            is Project.ScrapedProject -> insertScrapedProject(element, requestId)
        }
    }

    private suspend fun insertFoundProject(element: Project.FoundProject, requestId: Int) {
        insertMissingUser(element.owner, requestId)
        ProjectTable.upsert(
            onUpdateExclude = listOf(ProjectTable.firstSeen)
        ) {
            it[ProjectTable.id] = element.id
            it[ProjectTable.owner] = element.owner.name

            it[ProjectTable.firstSeen] = requestId
            it[ProjectTable.lastRequested] = requestId
        }
        existingProjects.putIfAbsent(element.id, false)
    }

    private suspend fun insertScrapedProject(element: Project.ScrapedProject, requestId: Int) {
        if (element.owner.name !in existingUsers) insertUser(element.owner, requestId)
        ProjectTable.upsert(
            onUpdateExclude = listOf(ProjectTable.firstSeen)
        ) {
            it[ProjectTable.id] = element.id
            it[ProjectTable.owner] = element.owner.name
            it[ProjectTable.title] = element.title
            it[ProjectTable.description] = element.description
            it[ProjectTable.superstar] = element.superstar

            it[ProjectTable.followerCount] = element.followerCount
            it[ProjectTable.devlogCount] = element.devlogCount
            it[ProjectTable.totalHours] = element.hourCount
            it[ProjectTable.postCount] = element.posts.size
            it[ProjectTable.isHardware] = element.isHardware

            it[ProjectTable.firstSeen] = requestId
            it[ProjectTable.lastRequested] = requestId
        }
        val existing = existingProjects[element.id] ?: false
        if (!existing) existingProjects[element.id] = true
        for (post in element.posts) {
            insertPost(post, requestId)
        }
    }

    private suspend fun insertMissingUser(element: User, requestId: Int) {
        if (element is User.ScrapedUser) {
            insertScrapedUser(element, requestId)
            return
        }

        val existingType = existingUsers[element.name] ?: (-1).toByte()
        if (existingType == (-1).toByte()) {
            insertUser(element, requestId)
        } else {
            val foundType = when (element) {
                is User.FoundUser -> 0
                is User.UnverifiedUser -> 1
                is User.PagedUser -> throw IllegalStateException("PagedUser should not be inserted")
            }
            if (existingType < foundType) {
                insertUser(element, requestId)
                return
            }
        }
    }

    private suspend fun insertUser(element: User, requestId: Int) {
        when (element) {
            is User.ScrapedUser -> insertScrapedUser(element, requestId)
            is User.FoundUser -> insertFoundUser(element, requestId)
            is User.UnverifiedUser -> insertUnverifiedUser(element, requestId)
            is User.PagedUser -> insertPagedUser(element, requestId)
        }
    }

    private suspend fun insertPagedUser(element: User.PagedUser, requestId: Int) {
        UserTable.upsert(
            onUpdateExclude = listOf(UserTable.firstSeen)
        ) {
            it[UserTable.name] = element.name
            it[UserTable.avatarUrl] = element.avatarUrl

            it[UserTable.pages] = element.page

            it[UserTable.firstSeen] = requestId
            it[UserTable.lastRequested] = requestId
        }

        for (post in element.posts) {
            insertPost(post, requestId)
        }
    }

    private suspend fun insertUnverifiedUser(element: User.UnverifiedUser, requestId: Int) {
        UserTable.upsert(
            onUpdateExclude = listOf(UserTable.firstSeen)
        ) {
            it[UserTable.name] = element.name
            it[UserTable.avatarUrl] = element.avatarUrl
            it[UserTable.verified] = false

            it[UserTable.slackId] = cachedLinkToSlack(element.avatarUrl)

            it[UserTable.firstSeen] = requestId
            it[UserTable.lastRequested] = requestId
        }
        val existingType = existingUsers[element.name] ?: (-1).toByte()
        if (existingType < 1) existingUsers[element.name] = 1
    }

    private suspend fun insertFoundUser(element: User.FoundUser, requestId: Int) {
        UserTable.upsert(
            onUpdateExclude = listOf(UserTable.firstSeen, UserTable.lastRequested)
        ) {
            it[UserTable.name] = element.name
            it[UserTable.avatarUrl] = element.avatarUrl
            it[UserTable.slackId] = cachedLinkToSlack(element.avatarUrl)

            it[UserTable.firstSeen] = requestId
            it[UserTable.lastRequested] = requestId
        }
        existingUsers.putIfAbsent(element.name, 0)
    }

    private suspend fun insertScrapedUser(element: User.ScrapedUser, requestId: Int) {
        UserTable.upsert(
            onUpdateExclude = listOf(UserTable.firstSeen)
        ) {
            it[UserTable.name] = element.name
            it[UserTable.avatarUrl] = element.avatarUrl
            it[UserTable.verified] = true

            it[UserTable.bio] = element.bio
            it[UserTable.slackId] = cachedLinkToSlack(element.avatarUrl)
            it[UserTable.devlogCount] = element.devlogCount
            it[UserTable.projectCount] = element.projectsCount
            it[UserTable.shipCount] = element.shipCount
            it[UserTable.votesCount] = element.votesCount
            it[UserTable.achievementsCount] = element.achievements.size
            it[UserTable.followerCount] = element.followerCount
            it[UserTable.followingCount] = element.followingCount
            it[UserTable.pages] = 1

            it[UserTable.firstSeen] = requestId
            it[UserTable.lastRequested] = requestId
        }
        val existingType = existingUsers[element.name] ?: (-1).toByte()
        if (existingType < 2) existingUsers[element.name] = 2
        AchievementTable.batchUpsert(
            element.achievements,
            onUpdateExclude = listOf(AchievementTable.firstSeen)
        ) {
            this[AchievementTable.user] = element.name
            this[AchievementTable.achievement] = it
            this[AchievementTable.firstSeen] = requestId
            this[AchievementTable.lastSeen] = requestId
        }
        for (post in element.posts) {
            insertPost(post, requestId)
        }
    }

    private suspend fun insertSuperStar(element: SuperStar, requestId: Int) {
        insertMissingUser(element.author, requestId)
        if (element.projectId !in existingProjects) insertProject(
            Project.FoundProject(
                element.projectId,
                element.author
            ),
            requestId
        )
        SuperstarTable.upsert(
            onUpdateExclude = listOf(SuperstarTable.firstSeen)
        ) {
            it[SuperstarTable.internalId] = element.internalId
            it[SuperstarTable.project] = element.projectId
            it[SuperstarTable.author] = element.author.name
            it[SuperstarTable.createdAt] = element.createdAt
            it[SuperstarTable.views] = element.views
            it[SuperstarTable.reposts] = element.reposts

            it[SuperstarTable.firstSeen] = requestId
            it[SuperstarTable.lastSeen] = requestId
        }
    }

    private suspend fun insertShipEvent(element: ShipEvent, requestId: Int) {
        insertMissingUser(element.author, requestId)
        if (element.projectId !in existingProjects) insertProject(
            Project.FoundProject(
                element.projectId,
                element.author
            ),
            requestId
        )
        ShipEventTable.upsert(
            onUpdateExclude = listOf(ShipEventTable.firstSeen)
        ) {
            it[ShipEventTable.internalId] = element.internalId
            it[ShipEventTable.project] = element.projectId
            if (element.shipNumber != null) {
                it[ShipEventTable.shipNumber] = element.shipNumber
            }
            it[ShipEventTable.createdAt] = element.createdAt
            it[ShipEventTable.demoUrl] = element.demoUrl.toString()
            it[ShipEventTable.repoUrl] = element.repoUrl.toString()
            it[ShipEventTable.devlogCount] = element.devlogCount
            it[ShipEventTable.hourCount] = element.hourCount
            it[ShipEventTable.attachedMission] = element.mission
            it[ShipEventTable.description] = element.body

            it[ShipEventTable.firstSeen] = requestId
            it[ShipEventTable.lastSeen] = requestId
        }
    }

    private suspend fun insertRepost(element: Repost, requestId: Int) {
        insertMissingUser(element.author, requestId)
        if (element.projectId !in existingProjects) insertProject(
            Project.FoundProject(
                element.projectId,
                element.devlogAuthor
            ),
            requestId
        )
        RepostTable.upsert(
            onUpdateExclude = listOf(RepostTable.firstSeen)
        ) {
            it[RepostTable.devlog] = element.devlogId
            it[RepostTable.by] = element.author.name
            it[RepostTable.createdAt] = element.createdAt
            it[RepostTable.body] = element.message

            it[RepostTable.firstSeen] = requestId
            it[RepostTable.lastSeen] = requestId
        }
    }

    private suspend fun insertDevlog(element: Devlog, requestId: Int) {
        insertMissingUser(element.author, requestId)
        if (element.projectId !in existingProjects) {
            insertProject(Project.FoundProject(element.projectId, element.author), requestId)
        }
        if (element.body.length > 4500) {
            logger.warn("Devlog (id: ${element.id} from ${element.projectId}) too long: ${element.body.length} > 4500")
            return
        }
        DevlogTable.upsert(
            onUpdateExclude = listOf(DevlogTable.firstSeen)
        ) {
            it[DevlogTable.id] = element.id
            it[DevlogTable.internalId] = element.internalId
            it[DevlogTable.project] = element.projectId
            it[DevlogTable.author] = element.author.name
            it[DevlogTable.createdAt] = element.createdAt
            it[DevlogTable.content] = element.body

            it[DevlogTable.timeLogged] = element.timeLogged
            it[DevlogTable.attachmentsCount] = element.attachments.size
            it[DevlogTable.comments] = element.commentsCount
            it[DevlogTable.reposts] = element.repostsCount
            it[DevlogTable.likes] = element.likesCount
            it[DevlogTable.views] = element.viewsCount

            it[DevlogTable.firstSeen] = requestId
            it[DevlogTable.lastSeen] = requestId
        }
        var attachmentsCount = 0
        DevlogAttachmentsTable.batchUpsert(
            element.attachments,
            onUpdateExclude = listOf(DevlogAttachmentsTable.firstSeen)
        ) {
            this[DevlogAttachmentsTable.id] = element.id
            this[DevlogAttachmentsTable.number] = attachmentsCount++
            this[DevlogAttachmentsTable.url] = it

            this[DevlogAttachmentsTable.firstSeen] = requestId
            this[DevlogAttachmentsTable.lastSeen] = requestId
        }
        if (element.comments != null) {
            for (comment in element.comments) {
                if (comment.author.name !in existingUsers) insertUser(comment.author, requestId)
            }
            CommentsTable.batchUpsert(
                element.comments,
                onUpdateExclude = listOf(CommentsTable.firstSeen)
            ) {
                this[CommentsTable.devlog] = element.id
                this[CommentsTable.number] = it.number
                this[CommentsTable.author] = it.author.name
                this[CommentsTable.content] = it.body
                this[CommentsTable.created] = it.createdAt

                this[CommentsTable.firstSeen] = requestId
                this[CommentsTable.lastSeen] = requestId
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DatabaseWriter::class.java)
    }
}