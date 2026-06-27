package com.fantamomo.hc.stardancegraph.util

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

private val oneDayPeriod = DatePeriod(days = 1)

fun LocalDate.daysUntilSequence(other: LocalDate): Sequence<LocalDate> = sequence {
    var current = this@daysUntilSequence

    while (current <= other) {
        yield(current)
        current += oneDayPeriod
    }
}