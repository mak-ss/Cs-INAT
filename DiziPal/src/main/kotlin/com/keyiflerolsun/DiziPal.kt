// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.StringUtils.decodeUri
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup

class DiziPal : MainAPI() {
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
                title.contains("Security check", ignoreCase = true) ||
                response.code == 503) {
                Log.d("DZP", "CloudFlare detected, using killer...")
                return cloudflareKiller.intercept(chain)
            }
            return response
        }
    }

    // ! GÜNCELLENDİ: Son Bölümler URL'si değişti
    override val mainPage = mainPageOf(
        "$mainUrl/yeni-eklenen-bolumler"                          to "Son Bölümler",  // ! ESKİ: /diziler/son-bolumler
        "$mainUrl/yabanci-dizi-izle"                                        to "Yeni Diziler",
        "$mainUrl/hd-film-izle"                                        to "Yeni Filmler",
        "$mainUrl/kanal/netflix"                             to "Netflix",
        "$mainUrl/kanal/exxen"                               to "Exxen",
        "$mainUrl/kanal/blutv"                               to "BluTV",
        "$mainUrl/kanal/disney"                              to "Disney+",
        "$mainUrl/kanal/amazon-prime"                        to "Amazon Prime",
        "$mainUrl/kanal/tod-bein"                            to "TOD (beIN)",
        "$mainUrl/kanal/gain"                                to "Gain",
        "$mainUrl/kanal/mubi"                                       to "Mubi",
            )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        try {
            val document = app.get(
                request.data,
                interceptor = interceptor,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
                    "Accept-Language" to "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7"
                )
            ).document
            
            // ! GÜNCELLENDİ: Yeni yapıya göre selector'ler güncellendi
            val home = when {
                request.data.contains("/yeni-eklenen-bolumler") -> {
                    // Yeni eklenen bölümler sayfası yapısı
                    document.select("div.episode-item, .episode-card, .new-episode-item, .episode-box").mapNotNull { it.yeniBolumler() }
                }
                request.data.contains("/diziler") || request.data.contains("/filmler") -> {
                    document.select("article.type2 ul li, .movie-item, .series-item, .content-item").mapNotNull { it.diziler() }
                }
                else -> {
                    // Koleksiyon ve diğer sayfalar
                    document.select("article.type2 ul li, .movie-item, .series-item, .content-item, .grid-item").mapNotNull { it.diziler() }
                }
            }

            return newHomePageResponse(request.name, home, hasNext=false)
        } catch (e: Exception) {
            Log.e("DZP", "getMainPage error: ${e.message}")
            e.printStackTrace()
            return newHomePageResponse(request.name, emptyList(), hasNext=false)
        }
    }

    // ! YENİ: Yeni eklenen bölümler için güncel parser
    private fun Element.yeniBolumler(): SearchResponse? {
        try {
            // Farklı olası yapılar için kontrol
            val name = this.selectFirst("div.name, .episode-title, h3.title, .title, h2")?.text()?.trim()
                ?: return null
            
            val episodeText = this.selectFirst("div.episode, .episode-info, .episode-number, .meta")?.text()?.trim() ?: ""
            
            // Bölüm bilgisini parse et (örn: "3. Sezon 8. Bölüm" -> "3x8")
            val seasonMatch = Regex("(\\d+)\\.?\\s*Sezon").find(episodeText)
            val episodeMatch = Regex("(\\d+)\\.?\\s*Bölüm").find(episodeText)
            
            val season = seasonMatch?.groupValues?.get(1) ?: "1"
            val episode = episodeMatch?.groupValues?.get(1) ?: ""
            
            val title = if (episode.isNotEmpty()) "$name ${season}x$episode" else name

            val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
            
            // Dizi ana sayfasına yönlendir (bölüm yerine dizi sayfası)
            // URL: /bolum/dizi-adi-sezonx-bolum -> /series/dizi-adi veya /dizi/dizi-adi
            val seriesHref = when {
                href.contains("/bolum/") -> {
                    // Bölüm URL'sinden dizi URL'sini çıkar
                    val diziAdi = href.substringAfter("/bolum/")
                        .replace(Regex("-\\d+x\\d+.*"), "") // sezonxblok kısmını temizle
                        .replace(Regex("-c\\d+.*"), "") // -cXX kısmını temizle
                    "$mainUrl/series/$diziAdi"
                }
                else -> href
            }
            
            val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src") 
                ?: this.selectFirst("img")?.attr("data-src")
                ?: this.selectFirst("img")?.attr("data-original"))

            return newTvSeriesSearchResponse(title, seriesHref, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        } catch (e: Exception) {
            Log.e("DZP", "yeniBolumler error: ${e.message}")
            return null
        }
    }

    private fun Element.diziler(): SearchResponse? {
        try {
            val title = this.selectFirst("span.title, .title, h3, h2, .movie-title, .series-title")?.text()?.trim() 
                ?: return null
            
            val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
            
            val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src") 
                ?: this.selectFirst("img")?.attr("data-src")
                ?: this.selectFirst("img")?.attr("data-original"))

            // Type kontrolü (film mi dizi mi)
            val type = if (href.contains("/film/") || this.hasClass("movie-item")) TvType.Movie else TvType.TvSeries

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
        val title     = this.title
        val href      = "${mainUrl}${this.url}"
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
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                )
            ).document

            val poster      = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content")
                ?: document.selectFirst(".cover img, .poster img, .movie-poster img")?.attr("src"))
            
            val year        = document.selectXpath("//div[text()='Yapım Yılı']//following-sibling::div").text().trim().toIntOrNull()
                ?: document.selectFirst(".year, .release-year, .date")?.text()?.trim()?.toIntOrNull()
            
            val description = document.selectFirst("div.summary p, .description, .plot, .synopsis")?.text()?.trim()
            
            val tags        = document.selectXpath("//div[text()='Türler']//following-sibling::div").text().trim().split(" ").map { it.trim() }
                .ifEmpty { 
                    document.select(".genre, .categories a, .tags a").map { it.text().trim() }
                }
            
            val rating      = document.selectXpath("//div[text()='IMDB Puanı']//following-sibling::div").text().trim()
                ?: document.selectFirst(".rating, .imdb, .score")?.text()?.trim() ?: ""
            
            val duration    = Regex("(\\d+)").find(document.selectXpath("//div[text()='Ortalama Süre']//following-sibling::div").text())?.value?.toIntOrNull()
                ?: Regex("(\\d+)").find(document.selectFirst(".duration, .runtime")?.text() ?: "")?.value?.toIntOrNull()

            // Dizi mi Film mi kontrolü
            val isSeries = url.contains("/dizi/") || url.contains("/series/") || document.select(".episodes-list, .season-list, .episode-list").isNotEmpty()

            if (isSeries) {
                val title = document.selectFirst("div.cover h5, h1.title, .series-title, h1")?.text()?.trim() 
                    ?: return null

                // Bölümleri çek
                val episodes = document.select("div.episode-item, .episode-card, .episode-list .episode, .episodes-list .episode").mapNotNull {
                    val epName    = it.selectFirst("div.name, .episode-title, .title, h3, h4")?.text()?.trim() 
                        ?: "Bölüm"
                    
                    val epHref    = fixUrlNull(it.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
                    
                    val epText    = it.selectFirst("div.episode, .episode-info, .meta, .episode-number")?.text()?.trim() ?: ""
                    
                    // Sezon ve bölüm numaralarını çıkar
                    val seasonMatch  = Regex("(\\d+)\\.?\\s*Sezon").find(epText)
                    val episodeMatch = Regex("(\\d+)\\.?\\s*Bölüm").find(epText)
                    
                    val epSeason  = seasonMatch?.groupValues?.get(1)?.toIntOrNull() 
                        ?: Regex("(\\d+)x\\d+").find(epText)?.groupValues?.get(1)?.toIntOrNull() 
                        ?: 1
                        
                    val epEpisode = episodeMatch?.groupValues?.get(1)?.toIntOrNull()
                        ?: Regex("\\d+x(\\d+)").find(epText)?.groupValues?.get(1)?.toIntOrNull()

                    newEpisode(epHref) {
                        this.name    = epName
                        this.episode = epEpisode
                        this.season  = epSeason
                    }
                }

                return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster
                    this.year      = year
                    this.plot      = description
                    this.tags      = tags
                    this.score     = if (rating.isNotEmpty()) Score.from10(rating) else null
                    this.duration  = duration
                }
            } else { 
                // Film
                val title = document.selectFirst("h1.title, div.cover h5, .movie-title, h1")?.text()?.trim()
                    ?: document.selectXpath("//div[@class='g-title'][2]/div").text().trim()
                    ?: return null

                return newMovieLoadResponse(title, url, TvType.Movie, url) {
                    this.posterUrl = poster
                    this.year      = year
                    this.plot      = description
                    this.tags      = tags
                    this.score     = if (rating.isNotEmpty()) Score.from10(rating) else null
                    this.duration  = duration
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
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                )
            ).document
            
            // Çeşitli player seçeneklerini dene
            val iframe = document.selectFirst(".series-player-container iframe")?.attr("src")
                ?: document.selectFirst("div#vast_new iframe")?.attr("src")
                ?: document.selectFirst(".player-container iframe")?.attr("src")
                ?: document.selectFirst("iframe[src*='player'], iframe[src*='embed'], iframe[src*='video']")?.attr("src")
                ?: document.selectFirst(".video-player iframe, #player iframe")?.attr("src")
                ?: document.selectFirst("iframe")?.attr("src")
                
            if (iframe == null) {
                Log.e("DZP", "No iframe found")
                // Alternatif: Direkt video linki ara
                val directLink = document.selectFirst("video source")?.attr("src")
                    ?: document.selectFirst("video")?.attr("src")
                if (directLink != null) {
                    callback.invoke(
                        newExtractorLink(
                            source  = this.name,
                            name    = this.name,
                            url     = directLink,
                            ExtractorLinkType.M3U8
                        ) {
                            this.referer = "${mainUrl}/"
                            this.quality = Qualities.Unknown.value
                        }
                    )
                    return true
                }
                return false
            }
                
            Log.d("DZP", "iframe » $iframe")

            val iSource = app.get(
                iframe, 
                referer="${mainUrl}/",
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                    "Accept" to "*/*"
                )
            ).text
            
            // M3U8 link ara
            val m3uLink = Regex("""file["']?\s*:\s*["']([^"']+\.m3u8[^"']*)["']""").find(iSource)?.groupValues?.get(1)
                ?: Regex("""src["']?\s*:\s*["']([^"']+\.m3u8[^"']*)["']""").find(iSource)?.groupValues?.get(1)
                ?: Regex("""["']([^"']*\.m3u8[^"']*)["']""").find(iSource)?.groupValues?.get(1)
                ?: Regex("""source\s+src=["']([^"']+)["']""").find(iSource)?.groupValues?.get(1)

            if (m3uLink == null) {
                Log.d("DZP", "M3U8 not found, trying extractors...")
                Log.d("DZP", "iSource preview » ${iSource.take(1000)}")
                return loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)
            }

            // Altyazıları işle
            val subtitles = Regex("""["']subtitle["']?\s*:\s*["']([^"']+)["']""").find(iSource)?.groupValues?.get(1)
                ?: Regex("""tracks.*?file["']?\s*:\s*["']([^"']+)["']""").find(iSource)?.groupValues?.get(1)

            if (subtitles != null) {
                if (subtitles.contains(",")) {
                    subtitles.split(",").forEach {
                        val subLang = it.substringAfter("[").substringBefore("]")
                        val subUrl  = it.replace("[${subLang}]", "").trim()

                        if (subUrl.isNotEmpty()) {
                            subtitleCallback.invoke(
                                SubtitleFile(
                                    lang = subLang.ifEmpty { "tr" },
                                    url  = fixUrl(subUrl)
                                )
                            )
                        }
                    }
                } else {
                    val subLang = subtitles.substringAfter("[").substringBefore("]")
                    val subUrl  = subtitles.replace("[${subLang}]", "").trim()

                    if (subUrl.isNotEmpty()) {
                        subtitleCallback.invoke(
                            SubtitleFile(
                                lang = subLang.ifEmpty { "tr" },
                                url  = fixUrl(subUrl)
                            )
                        )
                    }
                }
            }

            callback.invoke(
                newExtractorLink(
                    source  = this.name,
                    name    = this.name,
                    url     = m3uLink,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = "${mainUrl}/"
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
