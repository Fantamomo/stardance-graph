package com.fantamomo.hc.stardancegraph.util

import kotlin.concurrent.atomics.AtomicInt

fun AtomicInt.setIfGreater(newValue: Int): Boolean {
    while (true) {
        val current = load()

        if (current >= newValue) return false

        if (compareAndSet(current, newValue)) return true
    }
}