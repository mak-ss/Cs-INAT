package com.keyiflerolsun


import android.util.Base64
import android.util.Log
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.databind.ObjectMapper
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup

class Dizilla : MainAPI() {

    override var mainUrl = "https://dizilla.to"
    override var name = "Dizilla"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    // ---------------- MAIN PAGE ----------------

    override val mainPage = mainPageOf(
        "$mainUrl/diziler" to "Diziler",
        "$mainUrl/filmler" to "Filmler"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val document = app.get(request.data).document

        val home = document.select("div.poster-item").mapNotNull {
            val title = it.selectFirst("img")?.attr("alt") ?: return@mapNotNull null
            val href = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val poster = it.selectFirst("img")?.attr("src")

            newTvSeriesSearchResponse(title, fixUrl(href)) {
                this.posterUrl = poster
            }
        }

        return newHomePageResponse(request.name, home)
    }

    // ---------------- SEARCH ----------------

    override suspend fun search(query: String): List<SearchResponse> {

        val document =
            app.get("$mainUrl/?s=$query").document

        return document.select("div.poster-item").mapNotNull {

            val title = it.selectFirst("img")?.attr("alt") ?: return@mapNotNull null
            val href = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val poster = it.selectFirst("img")?.attr("src")

            newTvSeriesSearchResponse(title, fixUrl(href)) {
                this.posterUrl = poster
            }
        }
    }

    // ---------------- LOAD DETAIL ----------------

    override suspend fun load(url: String): LoadResponse {

        val document = app.get(url).document

        val title =
            document.selectFirst("h1")?.text()
                ?: throw ErrorLoadingException()

        val poster =
            document.selectFirst("div.poster img")
                ?.attr("src")

        val description =
            document.selectFirst("div.description")
                ?.text()

        val episodes = document
            .select("div.episodes a")
            .mapIndexed { index, element ->

                val epName = element.text()
                val epHref = fixUrl(element.attr("href"))

                Episode(
                    epHref,
                    epName,
                    index + 1
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

    // ---------------- LOAD LINKS ----------------

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
                    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                    "Accept-Language" to "tr-TR,tr;q=0.9",
                    "Referer" to mainUrl,
                    "Connection" to "keep-alive"
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

                val fixed = fixUrlNull(iframe)
                    ?: return@forEach

                loadExtractor(
                    fixed,
                    fixed,
                    subtitleCallback,
                    callback
                )
            }

            return true

        } catch (e: Exception) {
            Log.e("DIZILLA_ERROR", e.toString())
            return false
        }
    }
}
