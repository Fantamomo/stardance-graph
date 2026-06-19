package com.fantamomo.hc.stardancegraph

import com.fantamomo.hc.stardancegraph.data.Config
import com.fantamomo.hc.stardancegraph.manager.DatabaseManager

object App {
    suspend fun start() {
        Config.init()

        DatabaseManager.init()
    }
}