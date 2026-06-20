package com.fantamomo.hc.stardancegraph.scrapen

import com.fantamomo.hc.stardancegraph.model.Scrapable
import com.fantamomo.hc.stardancegraph.model.Sendable
import com.fantamomo.hc.stardancegraph.scrapen.data.SiteStats
import io.ktor.http.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class ScrapEngine {
    // elements that are sent to the database so that they can be written to the database
    // SENDER: progress, RECEIVER: databaseWriter
    val databaseChannel = Channel<Sendable>(Channel.UNLIMITED)

    // elements that are found on the site and should be processed
    // SENDER: siteScraper, RECEIVER: progress
    val foundChannel = Channel<Sendable>(Channel.RENDEZVOUS)

    // elements that are to be scraped
    // SENDER: progress, RECEIVER: siteScraper
    val toScrapeChannel = Channel<Scrapable>(Channel.UNLIMITED)

    // links that have already been scraped, so that we don't scrape them again
    val scrapedLinks: MutableSet<Url> = mutableSetOf()

    val databaseWriter = DatabaseWriter(this, databaseChannel)
    val siteScraper = SiteScraper(this, scraped = foundChannel, toScrap = toScrapeChannel)

    val siteStats = SiteStats()

    suspend fun run() = coroutineScope {
        launch {
            // starting the database writer
            databaseWriter.start()
        }
        launch {
            // starting the mapper which reads from foundChannel(siteScraper) and writes to databaseWriter and toScapeChannel
            progress()
        }
        launch {
            // starting the site scraper
            siteScraper.start()
        }

        // the database writer needs to be ready before we start scraping
        databaseWriter.waitForReady()

        // starting the monster
        toScrapeChannel.send(Scrapable.Project(3))
    }

    private suspend fun progress() {
        var sendToToScrape = 0
        for (element in foundChannel) {
            databaseChannel.send(element)
            val scrapable = element.getScrapable()
            if (scrapable.isNotEmpty()) {
                for (link in scrapable) {
                    if (scrapedLinks.add(link.url)) {
                        sendToToScrape++
                        toScrapeChannel.send(link)
                    }
                }
            }
        }
    }
}