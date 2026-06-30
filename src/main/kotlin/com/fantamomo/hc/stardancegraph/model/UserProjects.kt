package com.fantamomo.hc.stardancegraph.model

data class UserProjects(
    val user: User,
    val projects: List<Project.UserProjectPageProject>,
) : Sendable {
    override fun printable() = "User Projects"

    override fun getScrapable(result: MutableSet<Scrapable>) {
        user.getScrapable(result)
        projects.forEach { it.getScrapable(result) }
    }
}