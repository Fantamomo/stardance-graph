package com.fantamomo.hc.stardancegraph.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Returns a logger for the given class.
 *
 * We use context and receivers to restrict the scope of this function to the class itself.
 *
 * @throws IllegalArgumentException if the class is not in the com.fantamomo.hc.stardancegraph package
 */
context(obj: T)
inline fun <reified T> T.Logger(): Logger {
    if (this !== obj) throw IllegalArgumentException("The Logger function can only be called from within the class itself")
    val clazz = T::class.java
    if (!clazz.`package`.name.startsWith("com.fantamomo.hc.stardancegraph")) throw IllegalArgumentException("The Logger function can only be called on classes in the com.fantamomo.hc.stardancegraph package")
    return LoggerFactory.getLogger(clazz)!!
}