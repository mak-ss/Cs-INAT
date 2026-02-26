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
    // ! DÜZELTİLDİ: Sonundaki boşluk kaldırıldı
    override var mainUrl              = "https://dizipal1541.com"
    override var name                 = "DiziPal"
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

    // ! DÜZELTİLDİ: URL yapıları siteye göre ayarlandı
    override val mainPage = mainPageOf(
        "$mainUrl/yeni-eklenen-bolumler" to "Son Bölümler",
        "$mainUrl/yabanci-dizi-izle"     to "Yeni Diziler",
        "$mainUrl/hd-film-izle"          to "Yeni Filmler",
        "$mainUrl/kanal/netflix"         to "Netflix",
        "$mainUrl/kanal/exxen"           to "Exxen",
        "$mainUrl/kanal/blutv"           to "BluTV",
        "$mainUrl/kanal/disney"          to "Disney+",
        "$mainUrl/kanal/amazon-prime"    to "Amazon Prime",
        "$mainUrl/kanal/tod-bein"        to "TOD (beIN)",
        "$mainUrl/kanal/gain"            to "Gain",
        "$mainUrl/kanal/mubi"            to "Mubi",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        try {
            val document = app.get(
                request.data,
                interceptor = interceptor,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                )
            ).document
            
            val home = when {
                // ! DÜZELTİLDİ: Yeni eklenen bölümler için doğru selector
                request.data.contains("/yeni-eklenen-bolumler") -> {
                    document.select("div.episode-item").mapNotNull { it.yeniBolumler() }
                }
                // ! DÜZELTİLDİ: Dizi ve film sayfaları için doğru selector
                request.data.contains("/yabanci-dizi-izle") || request.data.contains("/hd-film-izle") || request.data.contains("/kanal/") -> {
                    document.select("div.series-item, div.movie-item, article.type2 ul li").mapNotNull { it.diziler() }
                }
                else -> {
                    document.select("article.type2 ul li, div.series-item, div.movie-item").mapNotNull { it.diziler() }
                }
            }

            return newHomePageResponse(request.name, home, hasNext=false)
        } catch (e: Exception) {
            Log.e("DZP", "getMainPage error: ${e.message}")
            return newHomePageResponse(request.name, emptyList(), hasNext=false)
        }
    }

    // ! DÜZELTİLDİ: Yeni eklenen bölümler parser'ı
    private fun Element.yeniBolumler(): SearchResponse? {
        try {
            // Dizi adı ve bölüm bilgisi
            val name = this.selectFirst("div.name")?.text()?.trim()
                ?: this.selectFirst("h3.title")?.text()?.trim()
                ?: return null
            
            val episodeText = this.selectFirst("div.episode")?.text()?.trim() ?: ""
            
            // "3. Sezon 8. Bölüm" -> "3x8" formatına çevir
            val seasonMatch = Regex("(\\d+)\\.?\\s*Sezon").find(episodeText)
            val episodeMatch = Regex("(\\d+)\\.?\\s*Bölüm").find(episodeText)
            
            val season = seasonMatch?.groupValues?.get(1) ?: "1"
            val episode = episodeMatch?.groupValues?.get(1) ?: ""
            
            val title = if (episode.isNotEmpty()) "$name ${season}x$episode" else name

            // ! ÖNEMLİ: Bölüm linkini al
            val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
            
            // ! DÜZELTİLDİ: Bölüm linkinden dizi linkini oluştur
            // Örnek: /bolum/supergirl-6-sezon-12-bolum-izle -> /dizi/supergirl
            val seriesHref = if (href.contains("/bolum/")) {
                val diziAdi = href.substringAfter("/bolum/")
                    .replace(Regex("-\\d+-sezon-.*"), "") // "-6-sezon-12-bolum-izle" kısmını temizle
                    .replace(Regex("-\\d+x\\d+.*"), "")   // alternatif format
                "$mainUrl/dizi/$diziAdi"
            } else {
                href
            }
            
            val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

            return newTvSeriesSearchResponse(title, seriesHref, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        } catch (e: Exception) {
            Log.e("DZP", "yeniBolumler error: ${e.message}")
            return null
        }
    }

    // ! DÜZELTİLDİ: Dizi/Film listesi parser'ı
    private fun Element.diziler(): SearchResponse? {
        try {
            val title = this.selectFirst("span.title")?.text()?.trim()
                ?: this.selectFirst("h3.title")?.text()?.trim()
                ?: this.selectFirst(".title")?.text()?.trim()
                ?: return null
            
            val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
            
            val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

            // Film mi dizi mi kontrolü
            val type = when {
                href.contains("/film/") -> TvType.Movie
                href.contains("/dizi/") -> TvType.TvSeries
                else -> TvType.TvSeries // Varsayılan dizi
            }

            return if (type == TvType.Movie) {
                newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
            } else {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
            }
        } catch (e: Exception) {
            Log.e("DZP", "diziler error: ${e.message}")
            return null
        }
    }

    private fun SearchItem.toPostSearchResult(): SearchResponse {
        val title = this.title
        val href = "${mainUrl}${this.url}"
        val posterUrl = this.poster

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

            val poster = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content")
                ?: document.selectFirst(".cover img, .poster img")?.attr("src"))
            
            val year = document.selectXpath("//div[text()='Yapım Yılı']//following-sibling::div").text().trim().toIntOrNull()
                ?: document.selectFirst(".year")?.text()?.trim()?.toIntOrNull()
            
            val description = document.selectFirst("div.summary p, .description")?.text()?.trim()
            
            val tags = document.selectXpath("//div[text()='Türler']//following-sibling::div").text().trim()
                .split(" ").map { it.trim() }.filter { it.isNotEmpty() }
            
            val rating = document.selectXpath("//div[text()='IMDB Puanı']//following-sibling::div").text().trim()
            
            val duration = Regex("(\\d+)").find(document.selectXpath("//div[text()='Ortalama Süre']//following-sibling::div").text())?.value?.toIntOrNull()

            // Dizi mi kontrolü
            val isSeries = url.contains("/dizi/") || document.select("div.episodes-list, div.episode-item").isNotEmpty()

            if (isSeries) {
                val title = document.selectFirst("div.cover h5, h1.title")?.text()?.trim() 
                    ?: return null

                // Bölümleri çek
                val episodes = document.select("div.episode-item").mapNotNull {
                    val epName = it.selectFirst("div.name")?.text()?.trim() ?: "Bölüm"
                    val epHref = fixUrlNull(it.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
                    
                    val epText = it.selectFirst("div.episode")?.text()?.trim() ?: ""
                    
                    val seasonMatch = Regex("(\\d+)\\.?\\s*Sezon").find(epText)
                    val episodeMatch = Regex("(\\d+)\\.?\\s*Bölüm").find(epText)
                    
                    val epSeason = seasonMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    val epEpisode = episodeMatch?.groupValues?.get(1)?.toIntOrNull()

                    newEpisode(epHref) {
                        this.name = epName
                        this.episode = epEpisode
                        this.season = epSeason
                    }
                }

                return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = description
                    this.tags = tags
                    this.score = if (rating.isNotEmpty()) Score.from10(rating) else null
                    this.duration = duration
                }
            } else { 
                // Film
                val title = document.selectFirst("h1.title, div.cover h5")?.text()?.trim()
                    ?: return null

                return newMovieLoadResponse(title, url, TvType.Movie, url) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = description
                    this.tags = tags
                    this.score = if (rating.isNotEmpty()) Score.from10(rating) else null
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
            
            // İframe ara
            val iframe = document.selectFirst(".series-player-container iframe")?.attr("src")
                ?: document.selectFirst("div#vast_new iframe")?.attr("src")
                ?: document.selectFirst(".player-container iframe")?.attr("src")
                ?: document.selectFirst("iframe")?.attr("src")
                
            if (iframe == null) {
                Log.e("DZP", "No iframe found")
                return false
            }
                
            Log.d("DZP", "iframe » $iframe")

            val iSource = app.get(iframe, referer="${mainUrl}/").text
            
            // M3U8 link ara
            val m3uLink = Regex("""file["']?\s*:\s*["']([^"']+\.m3u8[^"']*)["']""").find(iSource)?.groupValues?.get(1)
                ?: Regex("""["']([^"']*\.m3u8[^"']*)["']""").find(iSource)?.groupValues?.get(1)

            if (m3uLink == null) {
                Log.d("DZP", "M3U8 not found, trying extractors...")
                return loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)
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
                    this.referer = "${mainUrl}/"
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
