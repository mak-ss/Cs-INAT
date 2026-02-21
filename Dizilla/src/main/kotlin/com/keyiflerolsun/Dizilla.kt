package com.keyiflerolsun

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Dizilla : MainAPI() {

    override var mainUrl = "https://dizilla.to"
    override var name = "Dizilla"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/dizi-izle" to "Diziler"
    )

    private val mapper = jacksonObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    // =========================
    // MAIN PAGE (JSON PARSE)
    // =========================
    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val response = app.get(request.data + "?page=$page")
        val document = response.document

        val script = document
            .selectFirst("script#__NEXT_DATA__")
            ?.data() ?: return newHomePageResponse(emptyList(), false)

        val root = mapper.readTree(script)

        val seriesList =
            root["props"]
                ?.get("pageProps")
                ?.get("data")
                ?.get("series")

        val home = seriesList?.mapNotNull { item ->

            val title = item["title"]?.asText() ?: return@mapNotNull null
            val slug = item["slug"]?.asText() ?: return@mapNotNull null
            val poster = item["poster"]?.asText()

            newTvSeriesSearchResponse(
                title,
                "$mainUrl/$slug",
                TvType.TvSeries
            ) {
                this.posterUrl = poster
            }

        } ?: emptyList()

        return newHomePageResponse(
            listOf(HomePageList(request.name, home)),
            hasNext = true
        )
    }

    // =========================
    // LOAD (BÖLÜMLER)
    // =========================
    override suspend fun load(url: String): LoadResponse? {

        val response = app.get(url)
        val document = response.document

        val script = document
            .selectFirst("script#__NEXT_DATA__")
            ?.data() ?: return null

        val root = mapper.readTree(script)

        val data =
            root["props"]
                ?.get("pageProps")
                ?.get("data") ?: return null

        val series = data["series"] ?: return null

        val title = series["title"]?.asText() ?: return null
        val description = series["description"]?.asText()
        val poster = series["poster"]?.asText()

        val episodesJson = data["episodes"] ?: return null

        val episodes = episodesJson.mapNotNull { ep ->

            val slug = ep["slug"]?.asText() ?: return@mapNotNull null
            val epTitle = ep["title"]?.asText()
            val season = ep["season_number"]?.asInt()
            val episode = ep["episode_number"]?.asInt()

            newEpisode("$mainUrl/$slug") {
                this.name = epTitle
                this.season = season
                this.episode = episode
            }
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

    // =========================
    // VIDEO LINKS
    // =========================
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
                loadExtractor(
                    fixUrl(src),
                    data,
                    subtitleCallback,
                    callback
                )
            }
        }

        return true
    }
}
