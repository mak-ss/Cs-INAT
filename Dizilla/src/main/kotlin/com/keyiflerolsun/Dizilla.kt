// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Base64
import android.util.Log
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.*
import kotlin.coroutines.cancellation.CancellationException

class Dizilla : MainAPI() {

    override var mainUrl = "https://dizilla.to"
    override var name = "Dizilla"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        "${mainUrl}/arsiv" to "Yeni Eklenen Diziler",
        "${mainUrl}/dizi-turu/aile" to "Aile",
        "${mainUrl}/dizi-turu/aksiyon" to "Aksiyon",
        "${mainUrl}/dizi-turu/bilim-kurgu" to "Bilim Kurgu",
        "${mainUrl}/dizi-turu/dram" to "Dram",
        "${mainUrl}/dizi-turu/fantastik" to "Fantastik",
        "${mainUrl}/dizi-turu/gerilim" to "Gerilim",
        "${mainUrl}/dizi-turu/komedi" to "Komedi",
        "${mainUrl}/dizi-turu/korku" to "Korku",
        "${mainUrl}/dizi-turu/macera" to "Macera",
        "${mainUrl}/dizi-turu/romantik" to "Romantik",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {

        var document = app.get(request.data).document

        val home = if (request.data.contains("/arsiv")) {
            val yil = Calendar.getInstance().get(Calendar.YEAR)
            val url = "${request.data}?page=$page&tab=1&sort=date_desc&filterType=2&imdbMin=5&imdbMax=10&yearMin=1900&yearMax=$yil"
            document = app.get(url).document
            document.select("a.w-full").mapNotNull { it.toSeries() }
        } else {
            document.select("div.col-span-3 a").mapNotNull { it.toSeries() }
        }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSeries(): SearchResponse? {
        val title = this.selectFirst("h2")?.text() ?: return null
        val href = fixUrlNull(this.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            val response = app.post(
                "${mainUrl}/api/bg/searchcontent?searchterm=$query",
                referer = mainUrl
            )

            val objectMapper = ObjectMapper()
                .registerModule(KotlinModule.Builder().build())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

            val searchResult: SearchResult =
                objectMapper.readValue(response.body.string())

            val decoded =
                String(Base64.decode(searchResult.response ?: "", Base64.DEFAULT))

            val content: SearchData = objectMapper.readValue(decoded)

            content.result?.mapNotNull {
                val link = fixUrlNull(it.slug ?: return@mapNotNull null)
                newTvSeriesSearchResponse(
                    it.title ?: return@mapNotNull null,
                    link,
                    TvType.TvSeries
                ) {
                    this.posterUrl = it.poster
                }
            } ?: emptyList()

        } catch (e: Exception) {
            Log.e("Dizilla", "Search error: ${e.message}")
            emptyList()
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> =
        search(query)

    override suspend fun load(url: String): LoadResponse? {

        try {
            val document = app.get(url).document

            val title =
                document.selectFirst("div.poster.poster h2")?.ownText()
                    ?: return null

            val poster = fixUrlNull(
                document.selectFirst("div.w-full.page-top.relative img")
                    ?.attr("src")
            )

            val description =
                document.selectFirst("div.mt-2.text-sm")?.ownText()?.trim()

            val tags =
                document.selectFirst("div.poster.poster h3")
                    ?.ownText()
                    ?.split(",")
                    ?.map { it.trim() }

            val year = document
                .select("div.w-fit.min-w-fit")
                .getOrNull(1)
                ?.selectFirst("span.text-sm.opacity-60")
                ?.ownText()
                ?.split(" ")
                ?.lastOrNull()
                ?.toIntOrNull()

            // ✅ CloudStream yeni Score yapısı
            val scoreValue = document
                .selectFirst("div.flex.items-center span.text-white.text-sm")
                ?.ownText()
                ?.split("/")
                ?.firstOrNull()
                ?.toDoubleOrNull()

            val score = scoreValue?.let {
                Score(it.toInt(), 10)
            }

            val actors =
                document.select("div.global-box h5")
                    .map { Actor(it.ownText()) }

            val episodes = mutableListOf<Episode>()

            val seasonLinks =
                document.select("div.flex.items-center.flex-wrap.gap-2.mb-4 a")

            for (season in seasonLinks) {

                val seasonUrl = fixUrl(season.attr("href"))
                val seasonDoc = app.get(seasonUrl).document

                val seasonNumber =
                    seasonUrl.split("-")
                        .getOrNull(seasonUrl.split("-").size - 2)
                        ?.toIntOrNull()

                seasonDoc.select("div.episodes div.cursor-pointer")
                    .forEach { ep ->

                        val links = ep.select("a")
                        if (links.isEmpty()) return@forEach

                        val epName = links.last()?.ownText() ?: return@forEach
                        val epHref =
                            fixUrlNull(links.last()?.attr("href"))
                                ?: return@forEach

                        val epNumber =
                            links.first()?.ownText()
                                ?.trim()
                                ?.toIntOrNull()

                        episodes.add(
                            newEpisode(epHref) {
                                this.name = epName
                                this.season = seasonNumber
                                this.episode = epNumber
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
                this.tags = tags
                this.score = score
                addActors(actors)
            }

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("Dizilla", "Load error: ${e.message}")
            return null
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        try {
            val document = app.get(data).document

            val script =
                document.selectFirst("script#__NEXT_DATA__")
                    ?.data()
                    ?: return false

            val mapper = ObjectMapper()
                .registerModule(KotlinModule.Builder().build())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

            val root = mapper.readTree(script)

            val secureData =
                root.get("props")
                    ?.get("pageProps")
                    ?.get("secureData")
                    ?.asText()
                    ?: return false

            val decoded =
                String(Base64.decode(secureData, Base64.DEFAULT))

            val decodedJson = mapper.readTree(decoded)

            val sourceContent =
                decodedJson.get("RelatedResults")
                    ?.get("getEpisodeSources")
                    ?.get("result")
                    ?.get(0)
                    ?.get("source_content")
                    ?.asText()
                    ?: return false

            val iframe =
                fixUrlNull(
                    Jsoup.parse(sourceContent)
                        .selectFirst("iframe")
                        ?.attr("src")
                ) ?: return false

            loadExtractor(
                iframe,
                mainUrl,
                subtitleCallback,
                callback
            )

            return true

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("Dizilla", "LoadLinks error: ${e.message}")
            return false
        }
    }
}
