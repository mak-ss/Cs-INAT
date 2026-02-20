package com.keyiflerolsun

import android.util.Base64
import android.util.Log
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.network.CloudflareKiller
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.Calendar
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class Dizilla : MainAPI() {
    override var mainUrl = "https://dizilla.to"
    override var name = "Dizilla"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries)

    override var sequentialMainPage = true
    
    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller): Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val response = chain.proceed(request)
            val doc = Jsoup.parse(response.peekBody(1024 * 1024).string())

            if (doc.html().contains("verifying") || doc.html().contains("challenge-running")) {
                return cloudflareKiller.intercept(chain)
            }
            return response
        }
    }

    override val mainPage = mainPageOf(
        "${mainUrl}/tum-bolumler" to "Son Bölümler",
        "${mainUrl}/arsiv" to "Yeni Eklenen Diziler",
        "${mainUrl}/dizi-turu/bilim-kurgu" to "Bilim Kurgu",
        "${mainUrl}/dizi-turu/aksiyon" to "Aksiyon",
        "${mainUrl}/dizi-turu/komedi" to "Komedi"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data + if(page > 1) "?page=$page" else "", interceptor = interceptor).document
        
        val home = if (request.data.contains("/tum-bolumler")) {
            document.select("div.grid div.relative").mapNotNull { element ->
                val title = element.selectFirst("h2")?.text() ?: return@mapNotNull null
                val href = fixUrlNull(element.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
                val poster = fixUrlNull(element.selectFirst("img")?.attr("data-src") ?: element.selectFirst("img")?.attr("src"))
                
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                    this.posterUrl = poster
                }
            }
        } else {
            document.select("a.w-full").mapNotNull { element ->
                val title = element.selectFirst("h2")?.text() ?: return@mapNotNull null
                val href = fixUrlNull(element.attr("href")) ?: return@mapNotNull null
                val poster = fixUrlNull(element.selectFirst("img")?.attr("data-src") ?: element.selectFirst("img")?.attr("src"))
                
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                    this.posterUrl = poster
                }
            }
        }

        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // Arama API'si genellikle User-Agent ve Referer konusunda çok seçicidir
        val searchReq = app.get(
            "${mainUrl}/api/bg/searchcontent?searchterm=$query",
            headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to "$mainUrl/"
            ),
            interceptor = interceptor
        )

        return try {
            val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            
            val searchResult: SearchResult = objectMapper.readValue(searchReq.text)
            val decodedSearch = base64Decode(searchResult.response ?: "")
            val contentJson: SearchData = objectMapper.readValue(decodedSearch)
            
            contentJson.result?.map {
                newTvSeriesSearchResponse(it.title ?: "", fixUrl(it.slug ?: ""), TvType.TvSeries) {
                    this.posterUrl = fixUrlNull(it.poster)
                }
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e("Dizilla", "Search Error: ${e.message}")
            emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, interceptor = interceptor).document
        
        val title = document.selectFirst("h1")?.text()?.trim() ?: "Bilinmiyor"
        val poster = fixUrlNull(document.selectFirst("div.poster img")?.attr("src"))
        val description = document.selectFirst("div.text-sm.opacity-80")?.text()

        val episodes = mutableListOf<Episode>()
        // Sezonları bul ve bölümleri çek
        document.select("div.seasons a").forEach { seasonAnchor ->
            val seasonNum = seasonAnchor.text().filter { it.isDigit() }.toIntOrNull()
            val seasonHref = fixUrl(seasonAnchor.attr("href"))
            val seasonDoc = app.get(seasonHref, interceptor = interceptor).document
            
            seasonDoc.select("div.episodes a").forEach { epAnchor ->
                val href = fixUrl(epAnchor.attr("href"))
                val name = epAnchor.selectFirst("span")?.text() ?: epAnchor.text()
                
                episodes.add(newEpisode(href) {
                    this.name = name
                    this.season = seasonNum
                })
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val response = app.get(data, interceptor = interceptor)
        val document = response.document
        
        // 1. Yöntem: NEXT_DATA üzerinden secureData çekme
        val script = document.selectFirst("script#__NEXT_DATA__")?.data()
        if (script != null) {
            try {
                val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                val node = mapper.readTree(script)
                val secureData = node.at("/props/pageProps/secureData").asText()
                
                val decrypted = decryptDizillaResponse(secureData)
                if (decrypted != null) {
                    val sourceHtml = mapper.readTree(decrypted).at("/RelatedResults/getEpisodeSources/result/0/source_content").asText()
                    val iframeUrl = Jsoup.parse(sourceHtml).selectFirst("iframe")?.attr("src")
                    if (iframeUrl != null) {
                        return loadExtractor(fixUrl(iframeUrl), "$mainUrl/", subtitleCallback, callback)
                    }
                }
            } catch (e: Exception) {
                Log.e("Dizilla", "Decryption/Parsing failed")
            }
        }

        // 2. Yöntem: Düz Iframe tarama
        document.select("iframe").forEach {
            val src = it.attr("src")
            if (src.contains("contentx") || src.contains("playru")) {
                loadExtractor(fixUrl(src), "$mainUrl/", subtitleCallback, callback)
            }
        }

        return true
    }

    private val privateAESKey = "9bYMCNQiWsXIYFWYAu7EkdsSbmGBTyUI"

    private fun decryptDizillaResponse(response: String): String? {
        return try {
            val keySpec = SecretKeySpec(privateAESKey.toByteArray(), "AES")
            val ivSpec = IvParameterSpec(ByteArray(16))
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            String(cipher.doFinal(Base64.decode(response, Base64.DEFAULT)))
        } catch (e: Exception) {
            null
        }
    }
}