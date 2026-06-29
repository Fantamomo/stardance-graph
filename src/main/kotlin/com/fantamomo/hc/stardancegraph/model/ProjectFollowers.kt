package com.fantamomo.hc.stardancegraph.model

class ProjectFollowers(
    val project: Int,
    val owner: User,
    val followers: List<User>,
) : Sendable {
    override fun printable() = "Project Followers"

    override fun getScrapable(result: MutableSet<Scrapable>) {
        result.add(Scrapable.Project(project))
        owner.getScrapable(result)
        followers.forEach { result.add(Scrapable.User(it.name)) }
    }
}