package com.keyiflerolsun

import android.util.Base64
import android.util.Log
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import kotlin.coroutines.cancellation.CancellationException

class Dizilla : MainAPI() {

    override var mainUrl = "https://dizilla.to"
    override var name = "Dizilla"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/dizi-izle" to "Yeni Eklenen Diziler"
    )

    // ================= MAIN PAGE =================

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val document = app.get(request.data).document

        val home = document.select("a.w-full").mapNotNull {

            val title = it.selectFirst("h2")?.text()
                ?: return@mapNotNull null

            val href = fixUrlNull(it.attr("href"))
                ?: return@mapNotNull null

            val poster = fixUrlNull(
                it.selectFirst("img")?.attr("src")
            )

            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = poster
            }
        }

        return newHomePageResponse(request.name, home)
    }

    // ================= SEARCH =================

    override suspend fun search(query: String): List<SearchResponse> {

        return try {

            val response = app.post(
                "$mainUrl/api/bg/searchcontent?searchterm=$query",
                referer = mainUrl
            )

            val mapper = ObjectMapper()
                .registerModule(KotlinModule.Builder().build())
                .configure(
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    false
                )

            val searchResult: SearchResult =
                mapper.readValue(
                    response.body.string(),
                    SearchResult::class.java
                )

            val decoded =
                String(Base64.decode(searchResult.response ?: "", Base64.DEFAULT))

            val content: SearchData =
                mapper.readValue(decoded, SearchData::class.java)

            content.result?.mapNotNull {

                val title = it.title ?: return@mapNotNull null
                val slug = it.slug ?: return@mapNotNull null

                val link = fixUrlNull(slug)
                    ?: return@mapNotNull null

                newTvSeriesSearchResponse(title, link, TvType.TvSeries) {
                    this.posterUrl = it.poster
                }

            } ?: emptyList()

        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun quickSearch(query: String) = search(query)

    // ================= LOAD SERIES =================

    override suspend fun load(url: String): LoadResponse? {

        try {

            val document = app.get(url).document

            val title =
                document.selectFirst("div.poster.poster h2")
                    ?.ownText()
                    ?: return null

            val poster =
                fixUrlNull(
                    document.selectFirst("div.w-full.page-top.relative img")
                        ?.attr("src")
                )

            val description =
                document.selectFirst("div.mt-2.text-sm")
                    ?.ownText()
                    ?.trim()

            val actors =
                document.select("div.global-box h5")
                    .map { Actor(it.ownText()) }

            // ---- EPISODES (HTML'den) ----

            val episodes = document
                .select("div.episodes div.cursor-pointer")
                .mapNotNull { ep ->

                    val links = ep.select("a")
                    if (links.isEmpty()) return@mapNotNull null

                    val epName = links.last()?.ownText()
                        ?: return@mapNotNull null

                    val epHref = fixUrlNull(
                        links.last()?.attr("href")
                    ) ?: return@mapNotNull null

                    val epNumber =
                        links.first()?.ownText()
                            ?.trim()
                            ?.toIntOrNull()

                    newEpisode(epHref) {
                        this.name = epName
                        this.episode = epNumber
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
                addActors(actors)
            }

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("DIZILLA_LOAD_ERROR", e.toString())
            return null
        }
    }

    // ================= LOAD LINKS =================

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        try {

            val response = app.get(
                data,
                headers = mapOf(
                    "User-Agent" to
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36",
                    "Accept-Language" to "tr-TR,tr;q=0.9",
                    "Referer" to mainUrl
                )
            )

            val document = response.document

            val script =
                document.selectFirst("script#__NEXT_DATA__")
                    ?.data()
                    ?: return false

            val mapper = ObjectMapper()
                .registerModule(KotlinModule.Builder().build())
                .configure(
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    false
                )

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

            val sources =
                decodedJson.get("data")
                    ?.get("episode")
                    ?.get("sources")
                    ?: return false

            if (!sources.isArray || sources.size() == 0)
                return false

            sources.forEach { source ->

                val html =
                    source.get("source_content")
                        ?.asText()
                        ?: return@forEach

                val iframe =
                    Jsoup.parse(html)
                        .selectFirst("iframe")
                        ?.attr("src")
                        ?: return@forEach

                val fixed =
                    fixUrlNull(iframe)
                        ?: return@forEach

                loadExtractor(
                    fixed,
                    fixed,
                    subtitleCallback,
                    callback
                )
            }

            return true

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("DIZILLA_LINK_ERROR", e.toString())
            return false
        }
    }
}
