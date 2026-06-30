package com.fantamomo.hc.stardancegraph.scrapen

import com.fantamomo.hc.stardancegraph.db.*
import com.fantamomo.hc.stardancegraph.manager.DatabaseManager
import com.fantamomo.hc.stardancegraph.model.*
import com.fantamomo.hc.stardancegraph.util.cachedLinkToSlack
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.selects.select
import org.jetbrains.exposed.v1.r2dbc.*
import org.slf4j.LoggerFactory
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.decrementAndFetch
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
    private val shouldStopDeferred = CompletableDeferred<Unit>()

    val databaseRequests: Int
        get() = databaseRequestsInternal.load()

    private var savingJob: Job? = null

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
            select {
                channel.onReceive {
                    elements.add(it)
                }
                shouldStopDeferred.onAwait {}
            }
            if (elements.isEmpty() && shouldStop.load()) {
                finished.complete(Unit)
                return@coroutineScope
            }
            engine.databaseChannelSize.decrementAndFetch()
            while (isActive && !channel.isClosedForReceive) {
                val element = channel.tryReceive()
                if (element.isSuccess) {
                    elements.add(element.getOrThrow())
                    engine.databaseChannelSize.decrementAndFetch()
                } else break
                if (elements.size >= 200 && !shouldStop.load()) {
                    logger.warn("Received ${elements.size} elements in a row, saving to database now. Current objects still in channel: ${engine.databaseChannelSize.load()}")
                    break
                }
            }
            if (elements.isNotEmpty()) {
                saveToDb(elements)
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
        shouldStopDeferred.complete(Unit)
    }

    private suspend fun CoroutineScope.saveToDb(elements: List<ScrapedObject>) {
        savingJob?.join()
        savingJob = launch {
//            logger.info("Saving ${elements.size} elements to database")
//            val duration = measureTime {
            saveToDatabase(elements)
//            }
//            logger.info("Saved ${elements.size} elements to database in ${duration.inWholeMilliseconds}ms")
        }
    }

    private suspend fun saveToDatabase(elements: List<ScrapedObject>) {
        try {
//            var slowInsert = true
//            if (elements.size > 5) {
//                // if the size is greater than 5, we will try to insert optimized so that it is faster,
//                // but the chance that something goes wrong is higher, so if something goes wrong, we will insert manually
//                try {
//                    DatabaseManager.transaction {
//                        insertOptimised(elements)
//                    }
//                    slowInsert = false
//                } catch (e: Exception) {
//                    logger.error("Error saving to database", e)
//                }
//            }
//            if (slowInsert) {
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
//            }
        } catch (e: Exception) {
            logger.error("Error saving to database", e)
        }
    }

    private suspend fun insertOptimised(elements: List<ScrapedObject>) {
        val insertedIds = RequestTable.batchInsert(elements) { obj ->
            this[RequestTable.scraper] = obj.scraperId
            this[RequestTable.url] = obj.url.toString()
            this[RequestTable.method] = obj.method.value
            this[RequestTable.type] = obj.type.name

            this[RequestTable.requestedAt] = obj.requestedAt
            this[RequestTable.duration] = obj.duration
            this[RequestTable.statusCode] = obj.statusCode.toShort()
            this[RequestTable.result] =
                obj.sendable?.let { sendable -> sendable::class.java.simpleName }

            this[RequestTable.sendBytes] = obj.sendBytes
            this[RequestTable.receiveBytes] = obj.receivedBytes

            val footer = obj.devFooter
            if (footer != null) {
                this[RequestTable.serverBuild] = footer.build
                this[RequestTable.serverBuildAgo] = footer.timeAgo
                this[RequestTable.serverDbQueries] = footer.dbQueries
                this[RequestTable.serverDbCached] = footer.dbQueriesCached
                this[RequestTable.serverCacheHits] = footer.cacheHits
                this[RequestTable.serverCacheMisses] = footer.cacheMisses
                this[RequestTable.serverRequestPerSecond] = footer.requestPerSecond
                this[RequestTable.serverActiveUsersSignIn] = footer.signedInUsers
                this[RequestTable.serverActiveUsersVisitors] = footer.visitors
            }

            this[RequestTable.requestIteration] = Scraper.iterationId
        }.map { it[RequestTable.id].value }

        elements.forEachIndexed { index, obj ->
            val sendable = obj.sendable ?: return@forEachIndexed
            val requestId = insertedIds[index]
            insertSendable(sendable, requestId)
        }
    }

    private suspend fun insert(obj: ScrapedObject) {
        val sendable = obj.sendable
        val requestId = RequestTable.insertAndGetId {
            it[RequestTable.scraper] = obj.scraperId
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
            is RngPage -> insertRngPage(element, requestId)
            is UserProjects -> insertUserProjects(element, requestId)
        }
    }

    private suspend fun insertUserProjects(element: UserProjects, requestId: Int) {
        insertMissingUser(element.user, requestId)
        for (project in element.projects) insertUserProjectPageProject(project, requestId)
    }

    private suspend fun insertRngPage(element: RngPage, requestId: Int) {
        for (follower in element.entries) insertMissingUser(follower.user, requestId)
        RngTable.batchUpsert(
            element.entries,
            onUpdateExclude = listOf(RngTable.firstSeen),
            shouldReturnGeneratedValues = false
        ) {
            this[RngTable.date] = element.date
            this[RngTable.user] = it.user.name
            this[RngTable.rank] = it.rank
            this[RngTable.score] = it.score

            this[RngTable.firstSeen] = requestId
            this[RngTable.lastSeen] = requestId
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
            onUpdateExclude = listOf(UserFollowerTable.firstSeen),
            shouldReturnGeneratedValues = false
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
            onUpdateExclude = listOf(UserFollowerTable.firstSeen),
            shouldReturnGeneratedValues = false
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
            onUpdateExclude = listOf(ProjectFollowersTable.firstSeen),
            shouldReturnGeneratedValues = false
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
            is Project.UserProjectPageProject -> insertUserProjectPageProject(element, requestId)
        }
    }

    private suspend fun insertUserProjectPageProject(
        element: Project.UserProjectPageProject,
        requestId: Int
    ) {
        insertMissingUser(element.owner, requestId)
        ProjectTable.upsert(
            onUpdateExclude = listOf(ProjectTable.firstSeen)
        ) {
            it[ProjectTable.id] = element.id
            it[ProjectTable.owner] = element.owner.name
            it[ProjectTable.title] = element.title
            it[ProjectTable.bannerImage] = element.bannerUrl.toString()
            it[ProjectTable.description] = element.description
            it[ProjectTable.devlogCount] = element.devlogCount
            it[ProjectTable.totalHours] = element.hoursCount
            it[ProjectTable.lastUpdated] = element.lastUpdated
            it[ProjectTable.lastUpdatedAt] = requestId

            it[ProjectTable.firstSeen] = requestId
            it[ProjectTable.lastRequested] = requestId
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

            val bannerUrlString = element.bannerUrl?.toString()
            if ((bannerUrlString?.length ?: 0) <= 300) {
                it[ProjectTable.bannerImage] = bannerUrlString
            } else {
                logger.warn("Banner URL of project ${element.id} is too long: ${element.bannerUrl}")
            }

            it[ProjectTable.superstar] = element.superstar
            it[ProjectTable.sourceUrl] = element.sourceUrl?.toString()

            it[ProjectTable.followerCount] = element.followerCount
            it[ProjectTable.devlogCount] = element.devlogCount
            it[ProjectTable.totalHours] = element.hourCount
            it[ProjectTable.postCount] = element.posts.size
            it[ProjectTable.isHardware] = element.isHardware

            it[ProjectTable.attachedMission] = element.attachedMission
            it[ProjectTable.missionShipped] = element.missionShipped

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
            if (element.internalId != null) it[UserTable.internalId] = element.internalId
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

            val bannerUrlString = element.bannerUrl?.toString()
            if ((bannerUrlString?.length ?: 0) <= 300) {
                it[UserTable.bannerUrl] = bannerUrlString
            } else {
                logger.warn("Banner URL of user @${element.name} is too long: ${element.bannerUrl}")
            }

            if (element.internalId != null) it[UserTable.internalId] = element.internalId
            it[UserTable.verified] = true

            it[UserTable.joinData] = element.joinedDate
            it[UserTable.bio] = element.bio
            it[UserTable.slackId] = cachedLinkToSlack(element.avatarUrl)
            it[UserTable.devlogCount] = element.devlogCount
            it[UserTable.projectCount] = element.projectsCount
            it[UserTable.shipCount] = element.shipCount
            it[UserTable.votesCount] = element.votesCount
            it[UserTable.achievementsCount] = element.achievements.size
            it[UserTable.followerCount] = element.followerCount
            it[UserTable.followingCount] = element.followingCount
            it[UserTable.streak] = element.streak
            it[UserTable.pages] = 1

            it[UserTable.firstSeen] = requestId
            it[UserTable.lastRequested] = requestId
        }
        val existingType = existingUsers[element.name] ?: (-1).toByte()
        if (existingType < 2) existingUsers[element.name] = 2
        AchievementTable.batchUpsert(
            element.achievements,
            onUpdateExclude = listOf(AchievementTable.firstSeen),
            shouldReturnGeneratedValues = false
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
            it[ShipEventTable.pending] = element.pending
            it[ShipEventTable.returned] = element.returned
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
        if (element.attachments.isNotEmpty()) {
            var attachmentsCount = 0
            ShipEventAttachmentsTable.batchUpsert(
                element.attachments,
                onUpdateExclude = listOf(ShipEventAttachmentsTable.firstSeen),
                shouldReturnGeneratedValues = false
            ) {
                this[ShipEventAttachmentsTable.shipEvent] = element.internalId
                this[ShipEventAttachmentsTable.number] = attachmentsCount++
                this[ShipEventAttachmentsTable.url] = it

                this[ShipEventAttachmentsTable.firstSeen] = requestId
                this[ShipEventAttachmentsTable.lastSeen] = requestId
            }
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
        if (element.attachments.isNotEmpty()) {
            var attachmentsCount = 0
            DevlogAttachmentsTable.batchUpsert(
                element.attachments,
                onUpdateExclude = listOf(DevlogAttachmentsTable.firstSeen),
                shouldReturnGeneratedValues = false
            ) {
                this[DevlogAttachmentsTable.id] = element.id
                this[DevlogAttachmentsTable.number] = attachmentsCount++
                this[DevlogAttachmentsTable.url] = it

                this[DevlogAttachmentsTable.firstSeen] = requestId
                this[DevlogAttachmentsTable.lastSeen] = requestId
            }
        }
        if (element.comments != null) {
            for (comment in element.comments) {
                if (comment.author.name !in existingUsers) insertUser(comment.author, requestId)
            }
            CommentsTable.batchUpsert(
                element.comments,
                onUpdateExclude = listOf(CommentsTable.firstSeen),
                shouldReturnGeneratedValues = false
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