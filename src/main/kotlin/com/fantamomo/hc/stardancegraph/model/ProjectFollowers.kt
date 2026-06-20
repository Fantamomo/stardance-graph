package com.fantamomo.hc.stardancegraph.model

class ProjectFollowers(
    val project: Int,
    val owner: User,
    val followers: List<User>,
) : Sendable {
    override fun getScrapable(): Set<Scrapable> {
        val result = mutableSetOf<Scrapable>()
        result.add(Scrapable.Project(project))
        result.addAll(owner.getScrapable())
        followers.forEach { result.add(Scrapable.User(it.name)) }
        return result
    }
}