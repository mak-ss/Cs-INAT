// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.nodes.Element

class DiziPal : MainAPI() {

    // ! GÜNCELLENDİ: dizipal2027.com
    override var mainUrl = "https://dizipal2027.com"
    override var name = "DiziPal 2027"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val response = chain.proceed(chain.request())
            return if (response.code == 403 || response.code == 503) {
                cloudflareKiller.intercept(chain)
            } else response
        }
    }

    // ! GÜNCELLENDİ: dizipal2027.com URL yapısına göre
    override val mainPage = mainPageOf(
        "$mainUrl/trend" to "Trend",
        "$mainUrl/diziler" to "Diziler",
        "$mainUrl/filmler" to "Filmler",
        "$mainUrl/platform/netflix" to "Netflix",
        "$mainUrl/platform/exxen" to "Exxen",
        "$mainUrl/platform/blutv" to "BluTV",
        "$mainUrl/platform/prime-video" to "Amazon Prime",
        "$mainUrl/platform/disney" to "Disney+",
        "$mainUrl/platform/gain" to "Gain",
        "$mainUrl/platform/tod" to "TOD",
        "$mainUrl/platform/mubi" to "Mubi",
        "$mainUrl/kategori/aksiyon" to "Aksiyon",
        "$mainUrl/kategori/komedi" to "Komedi",
        "$mainUrl/kategori/dram" to "Dram"
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
        var poster =
            selectFirst("img")?.attr("data-src")
                ?: selectFirst("img")?.attr("data-lazy-src")
                ?: selectFirst("img")?.attr("src")

        if (poster.isNullOrBlank()) {
            val srcset = selectFirst("img")?.attr("srcset")
            poster = srcset?.split(",")?.lastOrNull()?.trim()?.split(" ")?.firstOrNull()
        }

        return fixUrl(poster)
    }

    // ================= MAIN PAGE =================

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        // ! GÜNCELLENDİ: Sayfalama yapısı ?page=2 şeklinde
        val url = if (page == 1) request.data else "${request.data}?page=$page"
        val doc = app.get(url, interceptor = interceptor).document

        // ! GÜNCELLENDİ: article.movies ve article.series selector'leri
        val items = doc.select("article.movies, article.series, div.content article")
            .mapNotNull { element ->

                val title = element.selectFirst("h3.title, h2, img")
                    ?.let { if (it.tagName() == "img") it.attr("alt") else it.text() }
                    ?.trim()
                    ?: return@mapNotNull null

                val link = element.selectFirst("a")?.attr("href")
                    ?.let { fixUrl(it) }
                    ?: return@mapNotNull null

                // ! GÜNCELLENDİ: Film mi dizi mi kontrolü
                val isMovie = link.contains("/film/") || element.hasClass("movies")

                if (isMovie) {
                    newMovieSearchResponse(title, link, TvType.Movie) {
                        posterUrl = element.getPoster()
                    }
                } else {
                    newTvSeriesSearchResponse(title, link, TvType.TvSeries) {
                        posterUrl = element.getPoster()
                    }
                }
            }

        return newHomePageResponse(request.name, items)
    }

    // ================= SEARCH =================

    override suspend fun search(query: String): List<SearchResponse> {

        // ! GÜNCELLENDİ: Arama URL'si /search?q=sorgu
        val url = "$mainUrl/search?q=${query.replace(" ", "+")}"
        val doc = app.get(url, interceptor = interceptor).document

        return doc.select("article.movies, article.series, .search-results article")
            .mapNotNull { element ->

                val title = element.selectFirst("h3.title, h2, img")
                    ?.let { if (it.tagName() == "img") it.attr("alt") else it.text() }
                    ?.trim()
                    ?: return@mapNotNull null

                val link = element.selectFirst("a")?.attr("href")
                    ?.let { fixUrl(it) }
                    ?: return@mapNotNull null

                val isMovie = link.contains("/film/") || element.hasClass("movies")

                if (isMovie) {
                    newMovieSearchResponse(title, link, TvType.Movie) {
                        posterUrl = element.getPoster()
                    }
                } else {
                    newTvSeriesSearchResponse(title, link, TvType.TvSeries) {
                        posterUrl = element.getPoster()
                    }
                }
            }
    }

    // ================= LOAD =================

    override suspend fun load(url: String): LoadResponse {

        val doc = app.get(url, interceptor = interceptor).document

        // ! GÜNCELLENDİ: Başlık ve poster selector'leri
        val title = doc.selectFirst("h1, .entry-title, div.title h1")?.text()?.trim() ?: "DiziPal"
        val description = doc.selectFirst(".entry-content p, .summary p, .description")?.text()
        val poster = fixUrl(doc.selectFirst("div.poster img, img.wp-post-image")?.attr("src"))

        // ! GÜNCELLENDİ: Bölümler div.episodes içinde
        val episodeElements = doc.select("div.episodes a, .episodes-list a, a[href*='/bolum/']")

        val episodes = if (episodeElements.isNotEmpty()) {
            episodeElements.mapIndexed { index, ep ->
                val epLink = fixUrl(ep.attr("href")) ?: ""
                val epName = ep.selectFirst("span.name, .episode-name")?.text()?.trim() 
                    ?: ep.text().trim()
                    ?: "Bölüm ${index + 1}"

                // ! GÜNCELLENDİ: Sezon ve bölüm bilgisini URL'den çıkar
                // URL: /bolum/dizi-adi-1-sezon-3-bolum
                val seasonMatch = Regex("(\\d+)-sezon").find(epLink)
                val episodeMatch = Regex("(\\d+)-bolum").find(epLink)
                
                val season = seasonMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val episodeNum = episodeMatch?.groupValues?.get(1)?.toIntOrNull() ?: (index + 1)

                newEpisode(epLink) {
                    name = epName
                    episode = episodeNum
                    this.season = season
                }
            }
        } else null

        return if (!episodes.isNullOrEmpty()) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
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

        val visited = mutableSetOf<String>()

        suspend fun extract(url: String) {

            if (visited.contains(url)) return
            visited.add(url)

            val res = app.get(url, referer = mainUrl, interceptor = interceptor)
            val html = res.text
            val doc = res.document

            // m3u8
            Regex("""https?://[^"' ]+\.m3u8[^"' ]*""")
                .findAll(html)
                .forEach {
                    callback(
                        newExtractorLink(
                            source = name,
                            name = "$name M3U8",
                            url = it.value,
                            type = ExtractorLinkType.M3U8
                        ) {
                            referer = url
                            quality = Qualities.Unknown.value
                        }
                    )
                }

            // mp4
            Regex("""https?://[^"' ]+\.mp4[^"' ]*""")
                .findAll(html)
                .forEach {
                    callback(
                        newExtractorLink(
                            source = name,
                            name = "$name MP4",
                            url = it.value,
                            type = ExtractorLinkType.VIDEO
                        ) {
                            referer = url
                            quality = Qualities.Unknown.value
                        }
                    )
                }

            // iframe recursive
            doc.select("iframe").forEach {
                val src = fixUrl(it.attr("src"))
                if (!src.isNullOrBlank() && !visited.contains(src)) {
                    extract(src)
                }
            }
        }

        extract(data)

        return true
    }
}
