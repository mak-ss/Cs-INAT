// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.network.CloudflareKiller
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup

class DiziPal : MainAPI() {
    override var mainUrl              = "https://dizipal2027.com"
    override var name                 = "DiziPal 2027"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.TvSeries, TvType.Movie)

    override var sequentialMainPage = true
    override var sequentialMainPageDelay       = 100L
    override var sequentialMainPageScrollDelay = 100L

    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor      by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller): Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request  = chain.request()
            val response = chain.proceed(request)
            
            val responseBody = response.peekBody(1024 * 1024).string()
            val doc = Jsoup.parse(responseBody)
            val title = doc.select("title").text()
            
            if (title.contains("Just a moment", ignoreCase = true) || 
                title.contains("Bir dakika", ignoreCase = true) ||
                title.contains("Checking your browser", ignoreCase = true) ||
                response.code == 503) {
                Log.d("DZP", "CloudFlare detected...")
                return cloudflareKiller.intercept(chain)
            }
            return response
        }
    }

    override val mainPage = mainPageOf(
        "$mainUrl/bolumler"              to "Son Bölümler",
        "$mainUrl/diziler"               to "Yeni Diziler",
        "$mainUrl/filmler"               to "Yeni Filmler",
        "$mainUrl/kategori/aksiyon"      to "Aksiyon",
        "$mainUrl/kategori/komedi"       to "Komedi",
        "$mainUrl/kategori/dram"         to "Dram",
        "$mainUrl/kategori/bilim-kurgu"  to "Bilim Kurgu",
        "$mainUrl/kategori/fantastik"    to "Fantastik",
        "$mainUrl/kategori/gerilim"      to "Gerilim",
        "$mainUrl/kategori/suc"          to "Suç",
        "$mainUrl/kategori/macera"       to "Macera",
        "$mainUrl/kategori/romantik"     to "Romantik",
        "$mainUrl/kategori/korku"        to "Korku",
        "$mainUrl/kategori/animasyon"    to "Animasyon",
        "$mainUrl/kategori/belgesel"     to "Belgesel",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        try {
            val url = if (page > 1) "${request.data}/page/$page/" else request.data
            
            Log.d("DZP", "Fetching URL: $url")
            
            val response = app.get(
                url,
                interceptor = interceptor,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
                    "Accept-Language" to "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7"
                )
            )
            
            Log.d("DZP", "Response code: ${response.code}")
            
            val document = response.document
            
            // Debug: Tüm HTML'i logla (ilk 2000 karakter)
            val htmlSample = document.html().take(2000)
            Log.d("DZP", "HTML Sample: $htmlSample")
            
            // Debug: Tüm class isimlerini logla
            val allClasses = document.select("[class]").map { it.className() }.distinct().take(20)
            Log.d("DZP", "Found classes: $allClasses")

            // ! ÇOK DAHA ESNEK SELECTOR'LER
            val home = when {
                request.data.contains("/bolumler") -> {
                    // Son bölümler için çeşitli olasılıklar
                    val items = document.select("article, .post, .item, .episode, .bolum, [class*='episode'], [class*='bolum']")
                    Log.d("DZP", "Bolumler found items: ${items.size}")
                    items.mapNotNull { parseBolumItem(it) }
                }
                else -> {
                    // Dizi ve film için çeşitli olasılıklar
                    val items = document.select("article, .post, .item, .movie, .dizi, .series, .film, [class*='movie'], [class*='series'], [class*='dizi']")
                    Log.d("DZP", "Diziler/Filmler found items: ${items.size}")
                    items.mapNotNull { parseContentItem(it) }
                }
            }

            Log.d("DZP", "Parsed ${home.size} items")

            // Sonraki sayfa var mı kontrol et
            val hasNext = document.select("a.next, .pagination a, .page-numbers a, [class*='next'], [class*='sonraki']").any { 
                it.text().contains("Sonraki", ignoreCase = true) || 
                it.text().contains("Next", ignoreCase = true) ||
                it.attr("href").contains("page") ||
                it.text().toIntOrNull() != null 
            }

            return newHomePageResponse(request.name, home, hasNext=hasNext && home.isNotEmpty())
        } catch (e: Exception) {
            Log.e("DZP", "getMainPage error: ${e.message}")
            e.printStackTrace()
            return newHomePageResponse(request.name, emptyList(), hasNext=false)
        }
    }

    // ! BÖLÜM İTEM PARSER (Çok esnek)
    private fun parseBolumItem(element: Element): SearchResponse? {
        try {
            // Tüm olası başlık selector'leri
            val name = element.selectFirst("h1, h2, h3, h4, .title, .entry-title, .post-title, a[title], [class*='title']")?.let {
                it.text().trim().ifEmpty { it.attr("title").trim() }
            }?.takeIf { it.isNotEmpty() } ?: return null

            // Tüm olası link selector'leri
            val href = element.selectFirst("a[href]")?.attr("href")?.let { fixUrlNull(it) } ?: return null
            
            // Tüm olası poster selector'leri
            val posterUrl = extractPosterUrlFlexible(element)
            
            Log.d("DZP", "Parsed bolum: $name - $href - $posterUrl")

            // Bölüm bilgisi varsa ekle
            val metaText = element.selectFirst(".meta, .episode-meta, .post-meta, [class*='meta']")?.text() ?: ""
            val seasonMatch = Regex("(\\d+)\\.?\\s*Sezon").find(metaText)
            val episodeMatch = Regex("(\\d+)\\.?\\s*Bölüm").find(metaText)
            
            val title = if (seasonMatch != null && episodeMatch != null) {
                "$name ${seasonMatch.groupValues[1]}x${episodeMatch.groupValues[1]}"
            } else {
                name
            }

            // Bölüm linkinden dizi linkini çıkarmaya çalış
            val seriesHref = if (href.contains("/bolum/")) {
                val diziAdi = href.substringAfter("/bolum/")
                    .replace(Regex("-\\d+-sezon-.*"), "")
                    .replace(Regex("-\\d+\\-bolum.*"), "")
                    .replace(Regex("-\\d+x\\d+.*"), "")
                    .replace(Regex("-izle.*"), "")
                "$mainUrl/dizi/$diziAdi"
            } else if (href.contains("/dizi/")) {
                href
            } else {
                href
            }

            return newTvSeriesSearchResponse(title, seriesHref, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        } catch (e: Exception) {
            Log.e("DZP", "parseBolumItem error: ${e.message}")
            return null
        }
    }

    // ! DİZİ/FİLM İTEM PARSER (Çok esnek)
    private fun parseContentItem(element: Element): SearchResponse? {
        try {
            // Başlık
            val title = element.selectFirst("h1, h2, h3, h4, .title, .entry-title, .post-title, a[title], [class*='title']")?.let {
                it.text().trim().ifEmpty { it.attr("title").trim() }
            }?.takeIf { it.isNotEmpty() } ?: return null

            // Link
            val href = element.selectFirst("a[href]")?.attr("href")?.let { fixUrlNull(it) } ?: return null
            
            // Poster
            val posterUrl = extractPosterUrlFlexible(element)
            
            Log.d("DZP", "Parsed content: $title - $href - $posterUrl")

            // Tür belirleme
            val type = when {
                href.contains("/film/") -> TvType.Movie
                href.contains("/dizi/") -> TvType.TvSeries
                element.className().contains("film", ignoreCase = true) -> TvType.Movie
                element.className().contains("dizi", ignoreCase = true) -> TvType.TvSeries
                element.className().contains("movie", ignoreCase = true) -> TvType.Movie
                element.className().contains("series", ignoreCase = true) -> TvType.TvSeries
                else -> TvType.TvSeries
            }

            return if (type == TvType.Movie) {
                newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
            } else {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
            }
        } catch (e: Exception) {
            Log.e("DZP", "parseContentItem error: ${e.message}")
            return null
        }
    }

    // ! ÇOK ESNEK POSTER ÇEKME
    private fun extractPosterUrlFlexible(element: Element): String? {
        // 1. Lazy loading attribute'ları
        var poster = element.selectFirst("img")?.attr("data-src")
            ?: element.selectFirst("img")?.attr("data-original")
            ?: element.selectFirst("img")?.attr("data-lazy-src")
            ?: element.selectFirst("img")?.attr("data-srcset")
            ?: element.selectFirst("source")?.attr("srcset")
        
        // 2. Normal src
        if (poster.isNullOrEmpty()) {
            poster = element.selectFirst("img")?.attr("src")
        }
        
        // 3. CSS background-image
        if (poster.isNullOrEmpty()) {
            val styleElements = element.select("[style*='background'], [style*='background-image']")
            for (el in styleElements) {
                val style = el.attr("style")
                val match = Regex("""background-image:\s*url\(['"]?([^'")]+)['"]?\)""").find(style)
                if (match != null) {
                    poster = match.groupValues[1]
                    break
                }
            }
        }
        
        // 4. Picture elementi
        if (poster.isNullOrEmpty()) {
            poster = element.selectFirst("picture source[srcset]")?.attr("srcset")?.split(",")?.first()?.trim()?.split(" ")?.first()
        }
        
        // URL düzeltmeleri
        poster?.let {
            if (it.startsWith("//")) {
                poster = "https:$it"
            } else if (it.startsWith("/")) {
                poster = "$mainUrl$it"
            } else if (!it.startsWith("http")) {
                poster = "$mainUrl/$it"
            }
        }
        
        return poster?.let { fixUrlNull(it) }
    }

    // Search ve diğer fonksiyonlar aynı kalıyor...
    private fun SearchItem.toPostSearchResult(): SearchResponse {
        val title = this.title
        val href = "${mainUrl}${this.url}"
        val posterUrl = this.poster.let { 
            if (it.startsWith("http")) it else "$mainUrl$it" 
        }

        return if (this.type == "series" || this.type == "dizi") {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        try {
            val responseRaw = app.post(
                "${mainUrl}/api/search-autocomplete",
                headers = mapOf(
                    "Accept" to "application/json, text/javascript, */*; q=0.01",
                    "X-Requested-With" to "XMLHttpRequest",
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                    "Referer" to "${mainUrl}/"
                ),
                referer = "${mainUrl}/",
                data = mapOf("query" to query),
                interceptor = interceptor
            )

            val searchItemsMap = jacksonObjectMapper().readValue<Map<String, SearchItem>>(responseRaw.text)
            val searchResponses = mutableListOf<SearchResponse>()

            for ((_, searchItem) in searchItemsMap) {
                searchResponses.add(searchItem.toPostSearchResult())
            }

            return searchResponses
        } catch (e: Exception) {
            Log.e("DZP", "search error: ${e.message}")
            return emptyList()
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        try {
            val document = app.get(
                url, 
                interceptor = interceptor,
                headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            ).document

            var poster = document.selectFirst("[property='og:image']")?.attr("content")
                ?: document.selectFirst("article img, .poster img, .cover img")?.let { 
                    extractPosterUrlFlexible(it.parent()!!) 
                }
            
            val year = document.selectFirst(".year, .release-year, [class*='year']")?.text()?.trim()?.toIntOrNull()
            
            val description = document.selectFirst("[property='og:description']")?.attr("content")
                ?: document.selectFirst(".summary, .description, .plot, [class*='summary'], [class*='description']")?.text()?.trim()
            
            val tags = document.select(".genre a, .categories a, .tags a, [class*='genre'] a").map { it.text().trim() }.filter { it.isNotEmpty() }
            
            val rating = document.selectFirst(".rating, .imdb, .score, [class*='rating'], [class*='imdb']")?.text()?.trim()
            
            val duration = Regex("(\\d+)").find(document.selectFirst(".duration, .runtime, [class*='duration']")?.text() ?: "")?.value?.toIntOrNull()

            val isSeries = url.contains("/dizi/") || document.select(".episodes, .episode-list, .seasons, [class*='episode'], [class*='bolum']").isNotEmpty()

            if (isSeries) {
                val title = document.selectFirst("h1, [property='og:title']")?.let {
                    it.attr("content").ifEmpty { it.text().trim() }
                } ?: return null

                val episodes = document.select(".episode, .bolum, [class*='episode'], [class*='bolum']").mapNotNull {
                    val epName = it.selectFirst("h3, h4, .title, [class*='title']")?.text()?.trim() ?: "Bölüm"
                    val epHref = it.selectFirst("a[href]")?.attr("href")?.let { fixUrlNull(it) } ?: return@mapNotNull null
                    
                    val epText = it.text()
                    val seasonMatch = Regex("(\\d+)\\.?\\s*Sezon").find(epText)
                    val episodeMatch = Regex("(\\d+)\\.?\\s*Bölüm").find(epText)
                    
                    val epSeason = seasonMatch?.groupValues?.get(1)?.toIntOrNull() 
                        ?: Regex("sezon-(\\d+)").find(epHref)?.groupValues?.get(1)?.toIntOrNull()
                        ?: 1
                        
                    val epEpisode = episodeMatch?.groupValues?.get(1)?.toIntOrNull()
                        ?: Regex("bolum-(\\d+)").find(epHref)?.groupValues?.get(1)?.toIntOrNull()

                    newEpisode(epHref) {
                        this.name = epName
                        this.episode = epEpisode
                        this.season = epSeason
                    }
                }

                return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl = fixUrlNull(poster)
                    this.year = year
                    this.plot = description
                    this.tags = tags
                    this.score = if (rating?.isNotEmpty() == true) Score.from10(rating) else null
                    this.duration = duration
                }
            } else { 
                val title = document.selectFirst("h1, [property='og:title']")?.let {
                    it.attr("content").ifEmpty { it.text().trim() }
                } ?: return null

                return newMovieLoadResponse(title, url, TvType.Movie, url) {
                    this.posterUrl = fixUrlNull(poster)
                    this.year = year
                    this.plot = description
                    this.tags = tags
                    this.score = if (rating?.isNotEmpty() == true) Score.from10(rating) else null
                    this.duration = duration
                }
            }
        } catch (e: Exception) {
            Log.e("DZP", "load error: ${e.message}")
            return null
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("DZP", "data » $data")
        
        try {
            val document = app.get(
                data, 
                interceptor = interceptor,
                headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            ).document
            
            val iframe = document.selectFirst("iframe[src], .player iframe, #player iframe, [class*='player'] iframe")?.attr("src")
                
            if (iframe == null) {
                Log.e("DZP", "No iframe found")
                return false
            }
                
            Log.d("DZP", "iframe » $iframe")

            val iSource = app.get(iframe, referer="$mainUrl/").text
            
            val m3uLink = Regex("""file["']?\s*:\s*["']([^"']+\.m3u8[^"']*)["']""").find(iSource)?.groupValues?.get(1)
                ?: Regex("""src["']?\s*:\s*["']([^"']+\.m3u8[^"']*)["']""").find(iSource)?.groupValues?.get(1)
                ?: Regex("""["']([^"']*\.m3u8[^"']*)["']""").find(iSource)?.groupValues?.get(1)

            if (m3uLink == null) {
                Log.d("DZP", "M3U8 not found, trying extractors...")
                return loadExtractor(iframe, "$mainUrl/", subtitleCallback, callback)
            }

            val subtitles = Regex("""subtitle["']?\s*:\s*["']([^"']+)["']""").find(iSource)?.groupValues?.get(1)
            
            if (subtitles != null) {
                subtitles.split(",").forEach {
                    val subLang = it.substringAfter("[").substringBefore("]")
                    val subUrl = it.replace("[${subLang}]", "").trim()
                    if (subUrl.isNotEmpty()) {
                        subtitleCallback.invoke(SubtitleFile(lang = subLang.ifEmpty { "tr" }, url = fixUrl(subUrl)))
                    }
                }
            }

            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = m3uLink,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = "$mainUrl/"
                    this.quality = Qualities.Unknown.value
                }
            )

            return true
        } catch (e: Exception) {
            Log.e("DZP", "loadLinks error: ${e.message}")
            return false
        }
    }
}
