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
    // ! GÜNCELLENDİ: Domain 1541 olarak değiştirildi
    override var mainUrl              = "https://dizipal1541.com"
    override var name                 = "DiziPal"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.TvSeries, TvType.Movie)

    override var sequentialMainPage = true
    override var sequentialMainPageDelay       = 100L  // ! ARTTIRILDI: 0.1 saniye (daha stabil)
    override var sequentialMainPageScrollDelay = 100L  // ! ARTTIRILDI: 0.1 saniye

    // ! CloudFlare v2 - Gelişmiş
    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor      by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller): Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request  = chain.request()
            val response = chain.proceed(request)
            
            // ! GÜNCELLENDİ: Daha kapsamlı CloudFlare kontrolü
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

    // ! GÜNCELLENDİ: mainUrl dinamik olarak kullanılıyor
    override val mainPage = mainPageOf(
        "$mainUrl/yabanci-dizi-izle"                                       to "Yeni Diziler",
        "$mainUrl/hd-film-izle"                                       to "Yeni Filmler",
        "$mainUrl/kanal/netflix"                            to "Netflix",
        "$mainUrl/kanal/exxen"                              to "Exxen",
        "$mainUrl/kanal/blutv"                              to "BluTV",
        "$mainUrl/kanal/disney"                             to "Disney+",
        "$mainUrl/kanal/amazon-prime"                       to "Amazon Prime",
        "$mainUrl/kanal/tod-bein"                           to "TOD (beIN)",
        "$mainUrl/kanal/gain"                               to "Gain",
        "$mainUrl/kanal/mubi"                               to "Mubi",
        "$mainUrl/anime"                                    to "Anime",
        "$mainUrl/diziler?kelime=&durum=&tur=5&type=&siralama="  to "Bilimkurgu Dizileri",
        "$mainUrl/tur/bilimkurgu"                                to "Bilimkurgu Filmleri",
        "$mainUrl/diziler?kelime=&durum=&tur=11&type=&siralama=" to "Komedi Dizileri",
        "$mainUrl/tur/komedi"                                    to "Komedi Filmleri",
        "$mainUrl/diziler?kelime=&durum=&tur=4&type=&siralama="  to "Belgesel Dizileri",
        "$mainUrl/tur/belgesel"                                  to "Belgesel Filmleri",
        "$mainUrl/diziler?kelime=&durum=&tur=25&type=&siralama=" to "Erotik Diziler",
        "$mainUrl/tur/erotik"                                    to "Erotik Filmler",
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
            
            val home = if (request.data.contains("/diziler/son-bolumler")) {
                document.select("div.episode-item").mapNotNull { it.sonBolumler() } 
            } else {
                document.select("article.type2 ul li").mapNotNull { it.diziler() }
            }

            return newHomePageResponse(request.name, home, hasNext=false)
        } catch (e: Exception) {
            Log.e("DZP", "getMainPage error: ${e.message}")
            return newHomePageResponse(request.name, emptyList(), hasNext=false)
        }
    }

    private fun Element.sonBolumler(): SearchResponse? {
        try {
            val name      = this.selectFirst("div.name")?.text() ?: return null
            val episode   = this.selectFirst("div.episode")?.text()?.trim()?.replace(". Sezon ", "x")?.replace(". Bölüm", "") ?: return null
            val title     = "$name $episode"

            val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
            val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

            return newTvSeriesSearchResponse(title, href.substringBefore("/sezon"), TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        } catch (e: Exception) {
            Log.e("DZP", "sonBolumler error: ${e.message}")
            return null
        }
    }

    private fun Element.diziler(): SearchResponse? {
        try {
            val title     = this.selectFirst("span.title")?.text() ?: return null
            val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
            val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

            return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
        } catch (e: Exception) {
            Log.e("DZP", "diziler error: ${e.message}")
            return null
        }
    }

    private fun SearchItem.toPostSearchResult(): SearchResponse {
        val title     = this.title
        val href      = "${mainUrl}${this.url}"
        val posterUrl = this.poster

        return if (this.type == "series") {
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

            val poster      = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
            val year        = document.selectXpath("//div[text()='Yapım Yılı']//following-sibling::div").text().trim().toIntOrNull()
            val description = document.selectFirst("div.summary p")?.text()?.trim()
            val tags        = document.selectXpath("//div[text()='Türler']//following-sibling::div").text().trim().split(" ").map { it.trim() }
            val rating      = document.selectXpath("//div[text()='IMDB Puanı']//following-sibling::div").text().trim()
            val duration    = Regex("(\\d+)").find(document.selectXpath("//div[text()='Ortalama Süre']//following-sibling::div").text())?.value?.toIntOrNull()

            if (url.contains("/dizi/")) {
                val title = document.selectFirst("div.cover h5")?.text() 
                    ?: document.selectFirst("h1")?.text() 
                    ?: return null

                // ! GÜNCELLENDİ: Daha esnek episode seçimi
                val episodes = document.select("div.episode-item, div.episodes-list .episode-item, .episode-list .episode").mapNotNull {
                    val epName    = it.selectFirst("div.name")?.text()?.trim() 
                        ?: it.selectFirst(".episode-name")?.text()?.trim()
                        ?: "Bölüm"
                    
                    val epHref    = fixUrlNull(it.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
                    
                    val epText    = it.selectFirst("div.episode")?.text()?.trim() ?: ""
                    val epEpisode = Regex("(\\d+)\\.\\s*Bölüm").find(epText)?.groupValues?.get(1)?.toIntOrNull()
                        ?: Regex("Bölüm\\s*(\\d+)").find(epText)?.groupValues?.get(1)?.toIntOrNull()
                    
                    val epSeason  = Regex("(\\d+)\\.\\s*Sezon").find(epText)?.groupValues?.get(1)?.toIntOrNull()
                        ?: 1

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
                    this.score     = Score.from10(rating)
                    this.duration  = duration
                }
            } else { 
                val title = document.selectXpath("//div[@class='g-title'][2]/div").text().trim()
                    ?: document.selectFirst("h1")?.text()?.trim()
                    ?: document.selectFirst("div.cover h5")?.text()?.trim()
                    ?: return null

                return newMovieLoadResponse(title, url, TvType.Movie, url) {
                    this.posterUrl = poster
                    this.year      = year
                    this.plot      = description
                    this.tags      = tags
                    this.score     = Score.from10(rating)
                    this.duration  = duration
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
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                )
            ).document
            
            // ! GÜNCELLENDİ: Daha fazla iframe seçeneği eklendi
            val iframe = document.selectFirst(".series-player-container iframe")?.attr("src")
                ?: document.selectFirst("div#vast_new iframe")?.attr("src")
                ?: document.selectFirst(".player-container iframe")?.attr("src")
                ?: document.selectFirst("iframe[src*='player']")?.attr("src")
                ?: document.selectFirst("iframe")?.attr("src")
                ?: return false
                
            Log.d("DZP", "iframe » $iframe")

            // ! GÜNCELLENDİ: Referer kontrolü
            val iSource = app.get(
                iframe, 
                referer="${mainUrl}/",
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                    "Accept" to "*/*"
                )
            ).text
            
            // ! GÜNCELLENDİ: Daha esnek regex pattern'ler
            val m3uLink = Regex("""file["']?\s*:\s*["']([^"']+\.m3u8[^"']*)["']""").find(iSource)?.groupValues?.get(1)
                ?: Regex("""src["']?\s*:\s*["']([^"']+\.m3u8[^"']*)["']""").find(iSource)?.groupValues?.get(1)
                ?: Regex("""["']([^"']*\.m3u8[^"']*)["']""").find(iSource)?.groupValues?.get(1)

            if (m3uLink == null) {
                Log.d("DZP", "M3U8 not found, trying extractors...")
                Log.d("DZP", "iSource preview » ${iSource.take(500)}")
                return loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)
            }

            // ! GÜNCELLENDİ: Daha esnek altyazı parsing
            val subtitles = Regex("""["']subtitle["']?\s*:\s*["']([^"']+)["']""").find(iSource)?.groupValues?.get(1)
                ?: Regex("""tracks.*?file["']?\s*:\s*["']([^"']+)[""]""").find(iSource)?.groupValues?.get(1)

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
            return false
        }
    }
}
