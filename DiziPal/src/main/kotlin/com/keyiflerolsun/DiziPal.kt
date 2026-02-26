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

    // ! DİZİPAL2027 YAPISINA GÖRE GÜNCELLENDİ
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
            
            val document = app.get(
                url,
                interceptor = interceptor,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                )
            ).document
            
            Log.d("DZP", "Loading: $url")

            val home = when {
                request.data.contains("/bolumler") -> {
                    // Son bölümler sayfası
                    document.select("div.episode-item, .episode-card, article.episode").mapNotNull { it.sonBolumler() }
                }
                request.data.contains("/filmler") || request.data.contains("/kategori/") -> {
                    // Film ve kategori sayfaları
                    document.select("div.movie-item, .film-card, article.movie, div.content-item").mapNotNull { it.filmVeDizi() }
                }
                else -> {
                    // Dizi sayfaları
                    document.select("div.series-item, .dizi-card, article.series, div.content-item").mapNotNull { it.filmVeDizi() }
                }
            }

            // Sonraki sayfa var mı kontrol et
            val hasNext = document.select("a.next.page-numbers, .pagination a[href]").any { 
                it.text().contains("Sonraki") || it.text().contains("Next") || it.text().toIntOrNull() != null 
            }

            return newHomePageResponse(request.name, home, hasNext=hasNext)
        } catch (e: Exception) {
            Log.e("DZP", "getMainPage error: ${e.message}")
            e.printStackTrace()
            return newHomePageResponse(request.name, emptyList(), hasNext=false)
        }
    }

    // ! DİZİPAL2027 İÇİN POSTER ÇEKME FONKSİYONU
    private fun extractPosterUrl(element: Element): String? {
        // Önce data-src gibi lazy loading attribute'larını dene
        var poster = element.selectFirst("img")?.attr("data-src")
            ?: element.selectFirst("img")?.attr("data-original")
            ?: element.selectFirst("img")?.attr("data-lazy-src")
        
        // Sonra src dene
        if (poster.isNullOrEmpty()) {
            poster = element.selectFirst("img")?.attr("src")
        }
        
        // Eğer relative URL ise absolute yap
        poster?.let {
            if (it.startsWith("//")) {
                poster = "https:$it"
            } else if (it.startsWith("/")) {
                poster = "$mainUrl$it"
            }
        }
        
        return poster?.let { fixUrlNull(it) }
    }

    // ! SON BÖLÜMLER İÇİN PARSER
    private fun Element.sonBolumler(): SearchResponse? {
        try {
            // Dizi adı ve bölüm bilgisi
            val name = this.selectFirst("h3.title, .episode-title, div.name, a.title")?.text()?.trim()
                ?: return null
            
            // Bölüm bilgisini içeren text (örn: "4. Sezon 5. Bölüm")
            val metaText = this.selectFirst(".episode-meta, .meta, div.episode")?.text()?.trim() ?: ""
            
            val seasonMatch = Regex("(\\d+)\\.?\\s*Sezon").find(metaText)
            val episodeMatch = Regex("(\\d+)\\.?\\s*Bölüm").find(metaText)
            
            val season = seasonMatch?.groupValues?.get(1) ?: "1"
            val episode = episodeMatch?.groupValues?.get(1) ?: ""
            
            val title = if (episode.isNotEmpty()) "$name $season.$episode" else name

            // Bölüm linki
            val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
            
            // Poster
            val posterUrl = extractPosterUrl(this)
            
            Log.d("DZP", "Bölüm: $title - Poster: $posterUrl")

            // Bölüm linkinden dizi linkini çıkar
            // /bolum/dizi-adi-4-sezon-5-bolum -> /dizi/dizi-adi
            val seriesHref = if (href.contains("/bolum/")) {
                val diziAdi = href.substringAfter("/bolum/")
                    .replace(Regex("-\\d+-sezon-.*"), "")
                    .replace(Regex("-\\d+\\-bolum.*"), "")
                    .replace(Regex("-\\d+x\\d+.*"), "")
                "$mainUrl/dizi/$diziAdi"
            } else {
                href
            }

            return newTvSeriesSearchResponse(title, seriesHref, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        } catch (e: Exception) {
            Log.e("DZP", "sonBolumler error: ${e.message}")
            return null
        }
    }

    // ! DİZİ VE FİLM LİSTESİ İÇİN PARSER
    private fun Element.filmVeDizi(): SearchResponse? {
        try {
            val title = this.selectFirst("h3.title, .title, a.title, h2")?.text()?.trim()
                ?: return null
            
            val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
            
            val posterUrl = extractPosterUrl(this)
            
            Log.d("DZP", "İçerik: $title - Poster: $posterUrl - URL: $href")

            // Tür belirleme
            val type = when {
                href.contains("/film/") -> TvType.Movie
                href.contains("/dizi/") -> TvType.TvSeries
                else -> TvType.TvSeries
            }

            return if (type == TvType.Movie) {
                newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
            } else {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
            }
        } catch (e: Exception) {
            Log.e("DZP", "filmVeDizi error: ${e.message}")
            return null
        }
    }

    // SearchItem data class'ı aynı kalıyor
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

            // Poster - önce og:image dene
            var poster = document.selectFirst("[property='og:image']")?.attr("content")
            
            // Yoksa sayfadaki posteri dene
            if (poster.isNullOrEmpty()) {
                poster = document.selectFirst(".poster img, .cover img, .movie-poster img")?.let { 
                    extractPosterUrl(it.parent()!!) 
                }
            }
            
            val year = document.selectFirst(".year, .release-year, .date")?.text()?.trim()?.toIntOrNull()
            
            val description = document.selectFirst(".summary p, .description, .plot, [property='og:description']")?.attr("content")
                ?: document.selectFirst(".summary p, .description, .plot")?.text()?.trim()
            
            val tags = document.select(".genre a, .categories a, .tags a").map { it.text().trim() }
            
            val rating = document.selectFirst(".rating, .imdb, .score")?.text()?.trim()
            
            val duration = Regex("(\\d+)").find(document.selectFirst(".duration, .runtime")?.text() ?: "")?.value?.toIntOrNull()

            // Dizi mi kontrolü
            val isSeries = url.contains("/dizi/") || document.select(".episodes-list, .episode-list, .seasons").isNotEmpty()

            if (isSeries) {
                val title = document.selectFirst("h1.title, .series-title, [property='og:title']")?.attr("content")
                    ?: document.selectFirst("h1.title, .series-title")?.text()?.trim()
                    ?: return null

                // Bölümleri çek
                val episodes = document.select(".episode-item, .episode-list .episode, .episodes .episode").mapNotNull {
                    val epName = it.selectFirst(".episode-title, .title, h3, h4")?.text()?.trim() ?: "Bölüm"
                    val epHref = fixUrlNull(it.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
                    
                    val epText = it.selectFirst(".episode-meta, .meta, .episode-number")?.text()?.trim() ?: ""
                    
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
                // Film
                val title = document.selectFirst("h1.title, .movie-title, [property='og:title']")?.attr("content")
                    ?: document.selectFirst("h1.title, .movie-title")?.text()?.trim()
                    ?: return null

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
            e.printStackTrace()
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
            
            // Player iframe'i ara
            val iframe = document.selectFirst(".player iframe, #player iframe, .video-player iframe, iframe[src*='player']")?.attr("src")
                ?: document.selectFirst("iframe")?.attr("src")
                
            if (iframe == null) {
                Log.e("DZP", "No iframe found")
                return false
            }
                
            Log.d("DZP", "iframe » $iframe")

            val iSource = app.get(iframe, referer="$mainUrl/").text
            
            // M3U8 link ara
            val m3uLink = Regex("""file["']?\s*:\s*["']([^"']+\.m3u8[^"']*)["']""").find(iSource)?.groupValues?.get(1)
                ?: Regex("""src["']?\s*:\s*["']([^"']+\.m3u8[^"']*)["']""").find(iSource)?.groupValues?.get(1)
                ?: Regex("""["']([^"']*\.m3u8[^"']*)["']""").find(iSource)?.groupValues?.get(1)

            if (m3uLink == null) {
                Log.d("DZP", "M3U8 not found, trying extractors...")
                return loadExtractor(iframe, "$mainUrl/", subtitleCallback, callback)
            }

            // Altyazıları işle
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
            e.printStackTrace()
            return false
        }
    }
}
