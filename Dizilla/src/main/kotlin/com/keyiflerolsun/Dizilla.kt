package com.keyiflerolsun

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup

class Dizilla : MainAPI() {

    override var mainUrl = "https://dizilla.to"
    override var name = "Dizilla"
    override var lang = "tr"
    override val hasMainPage = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // ================= SEARCH =================

    override suspend fun search(query: String): List<SearchResponse> {

        val document = app.get("$mainUrl/?s=$query").document

        return document.select("div.poster").mapNotNull { element ->

            val title = element.selectFirst("img")?.attr("alt") ?: return@mapNotNull null
            val link = element.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val poster = element.selectFirst("img")?.attr("src")

            if (link.contains("/dizi/")) {
                newTvSeriesSearchResponse(title, link, TvType.TvSeries) {
                    this.posterUrl = poster
                }
            } else {
                newMovieSearchResponse(title, link, TvType.Movie) {
                    this.posterUrl = poster
                }
            }
        }
    }

    override suspend fun quickSearch(query: String) = search(query)

    // ================= LOAD =================

    override suspend fun load(url: String): LoadResponse {

        val document = app.get(url).document

        val title = document.selectFirst("h1")?.text() ?: "Bilinmiyor"
        val poster = document.selectFirst(".poster img")?.attr("src")
        val description = document.selectFirst(".overview")?.text()

        // ===== DİZİ =====
        if (url.contains("/dizi/")) {

            val episodes = mutableListOf<Episode>()

            document.select("div.episode a").forEachIndexed { index, element ->
                val epLink = element.attr("href")
                episodes.add(
                    newEpisode(epLink) {
                        this.name = element.text()
                        this.episode = index + 1
                        this.season = 1
                    }
                )
            }

            return newTvSeriesLoadResponse(
                title,
                url,
                TvType.TvSeries,
                episodes
            ) {
                this.posterUrl = poster
                this.plot = description
            }
        }

        // ===== FİLM =====
        return newMovieLoadResponse(
            title,
            url,
            TvType.Movie,
            url
        ) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    // ================= LINKS =================

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = app.get(data).document

        document.select("iframe").forEach { iframe ->
            val src = iframe.attr("src")
            if (src.isNotEmpty()) {
                loadExtractor(src, mainUrl, subtitleCallback, callback)
            }
        }

        return true
    }
}
