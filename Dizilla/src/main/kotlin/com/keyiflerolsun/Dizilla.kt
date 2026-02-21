package com.keyiflerolsun

import android.util.Base64
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
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
        "${mainUrl}/dizi-izle" to "Yeni Eklenen Diziler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {

        val document = app.get("${request.data}?page=$page").document

        val home = document.select("a.w-full").mapNotNull { element ->
            val title = element.selectFirst("h2")?.text() ?: return@mapNotNull null
            val href = fixUrlNull(element.attr("href")) ?: return@mapNotNull null
            val poster = fixUrlNull(element.selectFirst("img")?.attr("src"))

            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = poster
            }
        }

        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return try {

            val response = app.post(
                "${mainUrl}/api/bg/searchcontent?searchterm=$query",
                referer = mainUrl
            )

            val mapper = ObjectMapper()
                .registerModule(KotlinModule.Builder().build())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

            val searchResult: SearchResult =
                mapper.readValue(response.body.string())

            val decoded =
                String(Base64.decode(searchResult.response ?: "", Base64.DEFAULT))

            val content: SearchData = mapper.readValue(decoded)

            content.result?.mapNotNull {
                val title = it.title ?: return@mapNotNull null
                val slug = it.slug ?: return@mapNotNull null
                val link = fixUrlNull(slug) ?: return@mapNotNull null

                newTvSeriesSearchResponse(title, link, TvType.TvSeries) {
                    this.posterUrl = it.poster
                }
            } ?: emptyList()

        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun quickSearch(query: String) = search(query)

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

            val year = document
                .select("div.w-fit.min-w-fit")
                .getOrNull(1)
                ?.selectFirst("span.text-sm.opacity-60")
                ?.ownText()
                ?.split(" ")
                ?.lastOrNull()
                ?.toIntOrNull()

            val actors =
                document.select("div.global-box h5")
                    .map { Actor(it.ownText()) }

            val episodes = mutableListOf<Episode>()

            document.select("div.episodes div.cursor-pointer")
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
                            this.episode = epNumber
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
                this.year = year
                this.plot = description
                addActors(actors)
            }

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
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

            val source =
                decodedJson.get("RelatedResults")
                    ?.get("getEpisodeSources")
                    ?.get("result")
                    ?.get(0)
                    ?.get("source_content")
                    ?.asText()
                    ?: return false

            val iframe =
                fixUrlNull(
                    Jsoup.parse(source)
                        .selectFirst("iframe")
                        ?.attr("src")
                ) ?: return false

            loadExtractor(iframe, mainUrl, subtitleCallback, callback)

            return true

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return false
        }
    }
}
