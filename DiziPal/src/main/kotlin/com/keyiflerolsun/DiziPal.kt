package com.keyiflerolsun

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class DiziPal : MainAPI() {

    override var mainUrl = "https://dizipal.bar"
    override var name = "DiziPal"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    override val mainPage = mainPageOf(
        "$mainUrl/filmler" to "Filmler",
        "$mainUrl/diziler" to "Diziler",
        "$mainUrl/animeler" to "Animeler"
    )

    // ================= UTIL =================

    private fun fixUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> mainUrl + url
            else -> url
        }
    }

    private fun Element.getPoster(): String? {
        val img = selectFirst("img") ?: return null

        val poster =
            img.attr("data-src").ifBlank {
                img.attr("data-lazy-src").ifBlank {
                    img.attr("src")
                }
            }

        return fixUrl(poster)
    }

    // ================= MAIN PAGE =================

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val url =
            if (page == 1) request.data
            else "${request.data}/page/$page"

        val doc = app.get(url).document

        val items = doc.select("article, div.post-item, div.poster-item")
            .mapNotNull { element ->

                val link = element.selectFirst("a")?.attr("href")
                    ?.let { fixUrl(it) }
                    ?: return@mapNotNull null

                val title =
                    element.selectFirst("h2, h3, .entry-title")
                        ?.text()
                        ?.trim()
                        ?: element.selectFirst("img")?.attr("alt")
                        ?: return@mapNotNull null

                newTvSeriesSearchResponse(
                    title,
                    link,
                    if (request.name == "Filmler") TvType.Movie else TvType.TvSeries
                ) {
                    posterUrl = element.getPoster()
                }
            }

        return newHomePageResponse(request.name, items)
    }

    // ================= SEARCH =================

    override suspend fun search(query: String): List<SearchResponse> {

        val url = "$mainUrl/?s=${query.replace(" ", "+")}"
        val doc = app.get(url).document

        return doc.select("article, div.post-item")
            .mapNotNull { element ->

                val link = element.selectFirst("a")?.attr("href")
                    ?.let { fixUrl(it) }
                    ?: return@mapNotNull null

                val title =
                    element.selectFirst("h2, h3")?.text()?.trim()
                        ?: element.selectFirst("img")?.attr("alt")
                        ?: return@mapNotNull null

                newTvSeriesSearchResponse(
                    title,
                    link,
                    TvType.TvSeries
                ) {
                    posterUrl = element.getPoster()
                }
            }
    }

    // ================= LOAD =================

    override suspend fun load(url: String): LoadResponse {

        val doc = app.get(url).document

        val title =
            doc.selectFirst("h1, .entry-title")
                ?.text()
                ?.trim()
                ?: return newMovieLoadResponse(
                    name,
                    url,
                    TvType.Movie,
                    url
                )

        val description =
            doc.selectFirst(".entry-content p, .plot")
                ?.text()

        val poster =
            fixUrl(
                doc.selectFirst("img.wp-post-image, .poster img")
                    ?.attr("src")
            )

        val episodeLinks =
            doc.select("a[href*='bolum'], a[href*='episode']")

        val episodes = episodeLinks.mapIndexed { index, element ->
            newEpisode(
                fixUrl(element.attr("href")) ?: ""
            ) {
                name = element.text().trim()
                episode = index + 1
            }
        }

        return if (episodes.isNotEmpty()) {
            newTvSeriesLoadResponse(
                title,
                url,
                TvType.TvSeries,
                episodes
            ) {
                posterUrl = poster
                plot = description
            }
        } else {
            newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                url
            ) {
                posterUrl = poster
                plot = description
            }
        }
    }

    // ================= LOAD LINKS =================

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val res = app.get(data, referer = mainUrl)
        val html = res.text

        Regex("""https?://[^"' ]+\.m3u8[^"' ]*""")
            .findAll(html)
            .forEach {
                callback(
                    newExtractorLink(
                        name,
                        "$name M3U8",
                        it.value,
                        ExtractorLinkType.M3U8
                    ) {
                        referer = data
                        quality = Qualities.Unknown.value
                    }
                )
            }

        Regex("""https?://[^"' ]+\.mp4[^"' ]*""")
            .findAll(html)
            .forEach {
                callback(
                    newExtractorLink(
                        name,
                        "$name MP4",
                        it.value,
                        ExtractorLinkType.VIDEO
                    ) {
                        referer = data
                        quality = Qualities.Unknown.value
                    }
                )
            }

        return true
    }
}
