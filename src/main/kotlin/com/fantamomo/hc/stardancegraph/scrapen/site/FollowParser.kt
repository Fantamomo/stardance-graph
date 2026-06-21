package com.fantamomo.hc.stardancegraph.scrapen.site

import com.fantamomo.hc.stardancegraph.model.ProjectFollowers
import com.fantamomo.hc.stardancegraph.model.User
import com.fantamomo.hc.stardancegraph.model.UserFollower
import com.fantamomo.hc.stardancegraph.model.UserFollowing
import com.fantamomo.hc.stardancegraph.util.Logger
import io.ktor.http.*
import org.jsoup.nodes.Element

object FollowParser {
    private val logger = Logger()

    fun parse(html: Element, url: Url): List<User.FoundUser> {
        if (html.selectFirst(".follow-list__empty") != null) return emptyList()
        val result = mutableListOf<User.FoundUser>()
        for (item in html.select(".follow-list__item")) {
            val avatarElement = item.selectFirst(".follow-list__avatar")
            if (avatarElement == null) {
                logger.warn("Failed to find avatar element in ${item.cssSelector()} from $url")
                continue
            }
            val avatarUrl = try {
                Url(avatarElement.attr("src"))
            } catch (e: Exception) {
                logger.warn("Failed to parse avatar URL in ${avatarElement.cssSelector()} from $url", e)
                continue
            }
            val usernameElement = item.selectFirst(".follow-list__name")
            if (usernameElement == null) {
                logger.warn("Failed to find username element in ${item.cssSelector()} from $url")
                continue
            }
            val username = usernameElement.text().removePrefix("@")
            if (username.isNotBlank()) {
                result.add(User.FoundUser(username, avatarUrl.toString()))
            } else {
                logger.warn("Failed to find username in ${usernameElement.cssSelector()} from $url")
            }
        }
        return result
    }

    fun parseProjectFollowers(html: Element, url: Url, projectId: Int, owner: User) =
        ProjectFollowers(
            project = projectId,
            owner = owner,
            followers = parse(html, url)
        )

    fun parseUserFollowers(html: Element, url: Url, user: User) = UserFollower(
        user = user,
        follower = parse(html, url)
    )

    fun parseUserFollowing(html: Element, url: Url, user: User) = UserFollowing(
        user = user,
        following = parse(html, url)
    )
}