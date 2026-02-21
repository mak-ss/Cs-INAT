package com.keyiflerolsun

import android.util.Log
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import java.util.Calendar

class Dizilla : MainAPI() {

    override var mainUrl = "https://dizilla.to"
    override var name = "Dizilla"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val mapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    // ================= MAIN PAGE =================

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {

        val yil = Calendar.getInstance().get(Calendar.YEAR)

        var url =
            "$mainUrl/api/bg/findMovies?releaseYearStart=1900&releaseYearEnd=$yil&imdbPointMin=1&imdbPointMax=10&categoryIdsComma=${request.data}&orderType=date_desc&currentPage=$page&currentPageCount=12"

        if (request.name.contains("Dizi"))
            url = url.replace("findMovies", "findSeries")

        val response = app.post(url, referer = "$mainUrl/")

        val json: SearchResult = mapper.readValue(response.text)

        val decoded = String(
            base64Decode(json.response ?: "").toByteArray(),
            Charsets.UTF_8
        )

        val data: ListItems = mapper.readValue(decoded)

        val home = data.result.map {

            val link = fixUrl(it.usedSlug ?: "")

            if (link.contains("/dizi/")) {
                newTvSeriesSearchResponse(
                    it.originalTitle ?: "",
                    link,
                    TvType.TvSeries
                ) {
                    posterUrl = it.posterUrl
                    score = Score.from10(it.imdbPoint)
                }
            } else {
                newMovieSearchResponse(
                    it.originalTitle ?: "",
                    link,
                    TvType.Movie
                ) {
                    posterUrl = it.posterUrl
                    score = Score.from10(it.imdbPoint)
                }
            }
        }

        return newHomePageResponse(request.name, home)
    }

    // ================= SEARCH =================

    override suspend fun search(query: String): List<SearchResponse> {

        val response = app.post(
            "$mainUrl/api/bg/searchcontent?searchterm=$query",
            referer = "$mainUrl/"
        )

        val json: SearchResult = mapper.readValue(response.text)

        val decoded = String(
            base64Decode(json.response ?: "").toByteArray(),
            Charsets.UTF_8
        )

        val data: SearchData = mapper.readValue(decoded)

        if (data.state != true) return emptyList()

        return data.result?.map {

            val link = fixUrl(it.slug ?: "")

            if (it.type == "Movies") {
                newMovieSearchResponse(
                    it.title ?: "",
                    link,
                    TvType.Movie
                ) { posterUrl = it.poster }
            } else {
                newTvSeriesSearchResponse(
                    it.title ?: "",
                    link,
                    TvType.TvSeries
                ) { posterUrl = it.poster }
            }

        } ?: emptyList()
    }

    override suspend fun quickSearch(query: String) = search(query)

    // ================= LOAD =================

    override suspend fun load(url: String): LoadResponse {

        val document = app.get(url).document
        val script = document.selectFirst("script#__NEXT_DATA__")?.data()
            ?: throw ErrorLoadingException("NEXT_DATA bulunamadı")

        val secureData = mapper.readTree(script)
            .get("props")
            .get("pageProps")
            .get("secureData")
            .asText()

        val decoded = String(
            base64Decode(secureData).toByteArray(),
            Charsets.UTF_8
        )

        val root: Root = mapper.readValue(decoded)
        val item = root.contentItem

        val title = item.originalTitle ?: "Bilinmiyor"

        val poster = item.posterUrl
        val year = item.releaseYear
        val description = item.description
        val rating = item.imdbPoint
        val duration = item.totalMinutes

        // ===== DİZİ =====
        if (root.relatedResults.getSerieSeasonAndEpisodes != null) {

            val episodes = mutableListOf<com.lagradost.cloudstream3.Episode>()

            root.relatedResults.getSerieSeasonAndEpisodes.seasons?.forEach { season ->
                season.episodes?.forEach { ep ->
                    episodes.add(
                        newEpisode(fixUrl(ep.usedSlug ?: "")) {
                            this.name = ep.epText
                            this.season = season.seasonNo
                            this.episode = ep.episodeNo
                        }
                    )
                }
            }

            return newTvSeriesLoadResponse(
                title,
                url,
                TvType.TvSeries,
                episodes
            ) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.score = Score.from10(rating)
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
            this.year = year
            this.score = Score.from10(rating)
            this.duration = duration
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
        val script = document.selectFirst("script#__NEXT_DATA__")?.data()
            ?: return false

        val secureData = mapper.readTree(script)
            .get("props")
            .get("pageProps")
            .get("secureData")
            .asText()

        val decoded = String(
            base64Decode(secureData).toByteArray(),
            Charsets.UTF_8
        )

        val root: Root = mapper.readValue(decoded)

        val sources = mutableListOf<String>()

        if (data.contains("/dizi/")) {
            root.relatedResults.getEpisodeSources?.result?.forEach {
                sources.add(it.sourceContent ?: "")
            }
        } else {
            root.relatedResults.getMoviePartsById?.result?.forEach { part ->
                root.relatedResults.getMoviePartSourcesById?.result?.forEach {
                    sources.add(it.sourceContent ?: "")
                }
            }
        }

        sources.forEach {
            val iframe = Jsoup.parse(it).select("iframe").attr("src")
            if (iframe.isNotEmpty()) {
                loadExtractor(
                    fixUrl(iframe),
                    "$mainUrl/",
                    subtitleCallback,
                    callback
                )
            }
        }

        return true
    }
}
