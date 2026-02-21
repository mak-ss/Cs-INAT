package com.keyiflerolsun

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class Dizilla : MainAPI() {

    override var mainUrl = "https://dizilla.to"
    override var name = "Dizilla"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/dizi" to "Diziler"
    )

    // ================================
    // MAIN PAGE
    // ================================
    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val url = request.data + "?page=$page"
        val document = app.get(url).document

        val home = document.select("div.poster").mapNotNull { element ->
            val link = element.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val title = element.selectFirst("img")?.attr("alt") ?: return@mapNotNull null
            val poster = element.selectFirst("img")?.attr("src")

            newTvSeriesSearchResponse(
                title,
                fixUrl(link),
                TvType.TvSeries
            ) {
                this.posterUrl = fixUrlNull(poster)
            }
        }

        return newHomePageResponse(
            listOf(HomePageList(request.name, home)),
            hasNext = true
        )
    }

    // ================================
    // SEARCH
    // ================================
    override suspend fun search(query: String): List<SearchResponse> {

        val document =
            app.get("$mainUrl/?s=$query").document

        return document.select("div.poster").mapNotNull { element ->

            val link = element.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val title = element.selectFirst("img")?.attr("alt") ?: return@mapNotNull null
            val poster = element.selectFirst("img")?.attr("src")

            newTvSeriesSearchResponse(
                title,
                fixUrl(link),
                TvType.TvSeries
            ) {
                this.posterUrl = fixUrlNull(poster)
            }
        }
    }

    // ================================
    // LOAD (NEXT.JS JSON PARSE)
    // ================================
    override suspend fun load(url: String): LoadResponse? {

        val response = app.get(url)
        val document = response.document

        val script = document
            .selectFirst("script#__NEXT_DATA__")
            ?.data()
            ?: return null

        val mapper = jacksonObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        val root = mapper.readTree(script)

        val pageProps = root["props"]?.get("pageProps") ?: return null
        val data = pageProps["data"] ?: return null
        val series = data["series"] ?: return null

        val title = series["title"]?.asText() ?: return null
        val description = series["description"]?.asText()
        val poster = series["poster"]?.asText()?.let { fixUrl(it) }

        val episodesJson = data["episodes"] ?: return null

        val episodes = mutableListOf<Episode>()

        episodesJson.forEach { ep ->

            val slug = ep["slug"]?.asText() ?: return@forEach
            val epTitle = ep["title"]?.asText()
            val season = ep["season_number"]?.asInt()
            val episode = ep["episode_number"]?.asInt()

            episodes.add(
                newEpisode("$mainUrl/$slug") {
                    this.name = epTitle
                    this.season = season
                    this.episode = episode
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

    // ================================
    // LOAD LINKS
    // ================================
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
                loadExtractor(fixUrl(src), data, subtitleCallback, callback)
            }
        }

        return true
    }
}
