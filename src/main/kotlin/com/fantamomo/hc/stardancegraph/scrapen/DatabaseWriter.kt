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
import org.jetbrains.exposed.v1.r2dbc.select
import org.jetbrains.exposed.v1.r2dbc.upsert
import org.slf4j.LoggerFactory
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Clock

class DatabaseWriter(val engine: ScrapEngine, val channel: ReceiveChannel<Sendable>) {
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
            val elements = mutableListOf<Sendable>()
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

    private suspend fun saveToDatabase(elements: List<Sendable>) {
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

    private suspend fun insert(element: Sendable) {
        when (element) {
            is Post -> insertPost(element)
            is User -> insertUser(element)
            is Project -> insertProject(element)
            is ProjectFollowers -> insertProjectFollowers(element)
            is UserFollower -> insertUserFollowers(element)
            is UserFollowing -> insertUserFollowing(element)
        }
    }

    private suspend fun insertPost(element: Post) {
        when (element) {
            is Devlog -> insertDevlog(element)
            is Repost -> insertRepost(element)
            is ShipEvent -> insertShipEvent(element)
            is SuperStar -> insertSuperStar(element)
        }
    }

    private suspend fun insertUserFollowing(element: UserFollowing) {
        insertMissingUser(element.user)
        for (follower in element.following) insertMissingUser(follower)
        val now = Clock.System.now()
        UserFollowerTable.batchUpsert(
            element.following,
            onUpdateExclude = listOf(UserFollowerTable.firstSeen)
        ) {
            this[UserFollowerTable.follower] = element.user.name
            this[UserFollowerTable.user] = it.name
            this[UserFollowerTable.firstSeen] = now
            this[UserFollowerTable.lastSeen] = now
            this[UserFollowerTable.lastSeenIteration] = Scraper.iterationId
        }
    }

    private suspend fun insertUserFollowers(element: UserFollower) {
        insertMissingUser(element.user)
        for (follower in element.follower) insertMissingUser(follower)
        val now = Clock.System.now()
        UserFollowerTable.batchUpsert(
            element.follower,
            onUpdateExclude = listOf(UserFollowerTable.firstSeen)
        ) {
            this[UserFollowerTable.follower] = it.name
            this[UserFollowerTable.user] = element.user.name
            this[UserFollowerTable.firstSeen] = now
            this[UserFollowerTable.lastSeen] = now
            this[UserFollowerTable.lastSeenIteration] = Scraper.iterationId
        }
    }

    private suspend fun insertProjectFollowers(element: ProjectFollowers) {
        insertMissingProject(element.project, element.owner)
        for (follower in element.followers) insertMissingUser(follower)
        val now = Clock.System.now()
        ProjectFollowersTable.batchUpsert(
            element.followers,
            onUpdateExclude = listOf(ProjectFollowersTable.firstSeen)
        ) {
            this[ProjectFollowersTable.project] = element.project
            this[ProjectFollowersTable.follower] = it.name

            this[ProjectFollowersTable.firstSeen] = now
            this[ProjectFollowersTable.lastSeen] = now
            this[ProjectFollowersTable.lastSeenIteration] = Scraper.iterationId
        }
    }

    private suspend fun insertMissingProject(id: Int, owner: User) {
        insertMissingUser(owner)
        val existing = existingProjects[id]
        if (existing == null) {
            insertFoundProject(Project.FoundProject(id, owner))
        }
    }

    private suspend fun insertProject(element: Project) {
        when (element) {
            is Project.FoundProject -> insertFoundProject(element)
            is Project.ScrapedProject -> insertScrapedProject(element)
        }
    }

    private suspend fun insertFoundProject(element: Project.FoundProject) {
        insertMissingUser(element.owner)
        val now = Clock.System.now()
        ProjectTable.upsert(
            onUpdateExclude = listOf(ProjectTable.firstSeen)
        ) {
            it[ProjectTable.id] = element.id
            it[ProjectTable.owner] = element.owner.name

            it[ProjectTable.firstSeen] = now
            it[ProjectTable.lastRequested] = now
            it[ProjectTable.lastRequestedIteration] = Scraper.iterationId
        }
        existingProjects.putIfAbsent(element.id, false)
    }

    private suspend fun insertScrapedProject(element: Project.ScrapedProject) {
        if (element.owner.name !in existingUsers) insertUser(element.owner)
        val now = Clock.System.now()
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

            it[ProjectTable.firstSeen] = now
            it[ProjectTable.lastRequested] = now
            it[ProjectTable.lastRequestedIteration] = Scraper.iterationId
        }
        val existing = existingProjects[element.id] ?: false
        if (!existing) existingProjects[element.id] = true
    }

    private suspend fun insertMissingUser(element: User) {
        if (element is User.ScrapedUser) {
            insertScrapedUser(element)
            return
        }

        val existingType = existingUsers[element.name] ?: (-1).toByte()
        if (existingType == (-1).toByte()) {
            insertUser(element)
        } else {
            val foundType = when (element) {
                is User.FoundUser -> 0
                is User.UnverifiedUser -> 1
                is User.PagedUser -> throw IllegalStateException("PagedUser should not be inserted")
            }
            if (existingType < foundType) {
                insertUser(element)
                return
            }
        }
    }

    private suspend fun insertUser(element: User) {
        when (element) {
            is User.ScrapedUser -> insertScrapedUser(element)
            is User.FoundUser -> insertFoundUser(element)
            is User.UnverifiedUser -> insertUnverifiedUser(element)
            is User.PagedUser -> insertPagedUser(element)
        }
    }

    private suspend fun insertPagedUser(element: User.PagedUser) {
        UserTable.upsert(
            onUpdateExclude = listOf(UserTable.firstSeen)
        ) {
            it[UserTable.name] = element.name
            it[UserTable.avatarUrl] = element.avatarUrl

            it[UserTable.pages] = element.page

            it[UserTable.firstSeen] = Clock.System.now()
        }

        for (post in element.posts) {
            insertPost(post)
        }
    }

    private suspend fun insertUnverifiedUser(element: User.UnverifiedUser) {
        UserTable.upsert(
            onUpdateExclude = listOf(UserTable.firstSeen)
        ) {
            it[UserTable.name] = element.name
            it[UserTable.avatarUrl] = element.avatarUrl
            it[UserTable.verified] = false

            it[UserTable.slackId] = cachedLinkToSlack(element.avatarUrl)

            it[UserTable.firstSeen] = Clock.System.now()
        }
        val existingType = existingUsers[element.name] ?: (-1).toByte()
        if (existingType < 1) existingUsers[element.name] = 1
    }

    private suspend fun insertFoundUser(element: User.FoundUser) {
        UserTable.upsert(
            onUpdateExclude = listOf(UserTable.firstSeen)
        ) {
            it[UserTable.name] = element.name
            it[UserTable.avatarUrl] = element.avatarUrl
            it[UserTable.slackId] = cachedLinkToSlack(element.avatarUrl)

            it[UserTable.firstSeen] = Clock.System.now()
        }
        existingUsers.putIfAbsent(element.name, 0)
    }

    private suspend fun insertScrapedUser(element: User.ScrapedUser) {
        val now = Clock.System.now()
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

            it[UserTable.firstSeen] = now
            it[UserTable.lastRequested] = now
            it[UserTable.lastRequestedIteration] = Scraper.iterationId
        }
        val existingType = existingUsers[element.name] ?: (-1).toByte()
        if (existingType < 2) existingUsers[element.name] = 2
        AchievementTable.batchUpsert(
            element.achievements,
            onUpdateExclude = listOf(AchievementTable.firstSeen)
        ) {
            this[AchievementTable.user] = element.name
            this[AchievementTable.achievement] = it
            this[AchievementTable.firstSeen] = now
            this[AchievementTable.lastSeen] = now
            this[AchievementTable.lastSeenIteration] = Scraper.iterationId
        }
        for (post in element.posts) {
            insertPost(post)
        }
    }

    private suspend fun insertSuperStar(element: SuperStar) {
        insertMissingUser(element.author)
        if (element.projectId !in existingProjects) insertProject(
            Project.FoundProject(
                element.projectId,
                element.author
            )
        )
        val now = Clock.System.now()
        SuperstarTable.upsert(
            onUpdateExclude = listOf(SuperstarTable.firstSeen)
        ) {
            it[SuperstarTable.internalId] = element.internalId
            it[SuperstarTable.project] = element.projectId
            it[SuperstarTable.author] = element.author.name
            it[SuperstarTable.createdAt] = element.createdAt
            it[SuperstarTable.views] = element.views
            it[SuperstarTable.reposts] = element.reposts

            it[SuperstarTable.firstSeen] = now
            it[SuperstarTable.lastSeen] = now
            it[SuperstarTable.lastSeenIteration] = Scraper.iterationId
        }
    }

    private suspend fun insertShipEvent(element: ShipEvent) {
        insertMissingUser(element.author)
        if (element.projectId !in existingProjects) insertProject(
            Project.FoundProject(
                element.projectId,
                element.author
            )
        )
        val now = Clock.System.now()
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

            it[ShipEventTable.firstSeen] = now
            it[ShipEventTable.lastSeen] = now
            it[ShipEventTable.lastSeenIteration] = Scraper.iterationId
        }
    }

    private suspend fun insertRepost(element: Repost) {
        insertMissingUser(element.author)
        if (element.projectId !in existingProjects) insertProject(
            Project.FoundProject(
                element.projectId,
                element.devlogAuthor
            )
        )
        val now = Clock.System.now()
        RepostTable.upsert(
            onUpdateExclude = listOf(RepostTable.firstSeen)
        ) {
            it[RepostTable.devlog] = element.devlogId
            it[RepostTable.by] = element.author.name
            it[RepostTable.createdAt] = element.createdAt
            it[RepostTable.body] = element.message

            it[RepostTable.firstSeen] = now
            it[RepostTable.lastSeen] = now
            it[RepostTable.lastSeenIteration] = Scraper.iterationId
        }
    }

    private suspend fun insertDevlog(element: Devlog) {
        insertMissingUser(element.author)
        if (element.projectId !in existingProjects) {
            insertProject(Project.FoundProject(element.projectId, element.author))
        }
        if (element.body.length > 4500) {
            logger.warn("Devlog (id: ${element.id} from ${element.projectId}) too long: ${element.body.length} > 4500")
            return
        }
        val now = Clock.System.now()
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

            it[DevlogTable.firstSeen] = now
            it[DevlogTable.lastSeen] = now
            it[DevlogTable.lastSeenIteration] = Scraper.iterationId
        }
        var attachmentsCount = 0
        DevlogAttachmentsTable.batchUpsert(
            element.attachments
        ) {
            this[DevlogAttachmentsTable.id] = element.id
            this[DevlogAttachmentsTable.number] = attachmentsCount++
            this[DevlogAttachmentsTable.url] = it
        }
        if (element.comments != null) {
            for (comment in element.comments) {
                if (comment.author.name !in existingUsers) insertUser(comment.author)
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

                this[CommentsTable.firstSeen] = now
                this[CommentsTable.lastSeen] = now
                this[CommentsTable.lastSeenIteration] = Scraper.iterationId
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DatabaseWriter::class.java)
    }
}