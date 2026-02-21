package com.keyiflerolsun

import android.util.Log
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import java.util.Calendar

class Dizilla : MainAPI() {
    override var mainUrl = "https://dizilla.to"
    override var name = "Dizilla"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "" to "Yeni Eklenen Filmler",
        "49" to "Aile Film",
        "44" to "Animasyon Film",
        "59" to "Aksiyon Film",
        "66" to "Bilim Kurgu Film",
        "48" to "Dram Film",
        "61" to "Fantastik Film",
        "68" to "Gerilim Film",
        "51" to "Gizem Film",
        "63" to "Korku Film",
        "45" to "Komedi Film",
        "65" to "Romantik Film",
        "46" to "Suç Film",
        "69" to "Savaş Film",
        "78" to "Western Film",
        "" to "Yeni Eklenen Diziler",
        "15" to "Aile Dizi",
        "17" to "Animasyon Dizi",
        "9" to "Aksiyon Dizi",
        "5" to "Bilim Kurgu Dizi",
        "2" to "Dram Dizi",
        "12" to "Fantastik Dizi",
        "18" to "Gerilim Dizi",
        "3" to "Gizem Dizi",
        "8" to "Korku Dizi",
        "4" to "Komedi Dizi",
        "7" to "Romantik Dizi",
        "1" to "Suç Dizi",
        "26" to "Savaş Dizi",
        "11" to "Western Dizi",
    )

    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build()).apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0",
        "Accept" to "application/json, text/plain, */*",
        "X-Requested-With" to "XMLHttpRequest"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val yil = Calendar.getInstance().get(Calendar.YEAR)
        var url = "$mainUrl/api/bg/findMovies?releaseYearStart=1900&releaseYearEnd=$yil&imdbPointMin=1&imdbPointMax=10&categoryIdsComma=${request.data}&orderType=date_desc&currentPage=${page}&currentPageCount=12"
        
        if (request.name.contains("Dizi")) {
            url = url.replace("findMovies", "findSeries")
        }

        val response = app.post(url, headers = commonHeaders, referer = "$mainUrl/").toString()
        val searchResult: SearchResult = mapper.readValue(response)
        val decoded = String(base64Decode(searchResult.response ?: "").toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
        val listItems: ListItems = mapper.readValue(decoded)
        
        return newHomePageResponse(request.name, listItems.result.map { it.toMainPageResult() })
    }

    private fun ContentItem.toMainPageResult(): SearchResponse {
        val title = this.originalTitle ?: ""
        val href = fixUrlNull(this.usedSlug) ?: ""
        val poster = fixUrlNull(this.posterUrl?.replace("images-macellan-online.cdn.ampproject.org/i/s/", ""))
        
        return if (href.contains("/dizi/")) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = poster
                this.score = Score.from10(imdbPoint)
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
                this.score = Score.from10(imdbPoint)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.post("$mainUrl/api/bg/searchcontent?searchterm=$query", headers = commonHeaders, referer = "$mainUrl/").toString()
        val searchResult: SearchResult = mapper.readValue(response)
        val decoded = String(base64Decode(searchResult.response ?: "").toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
        val contentJson: SearchData = mapper.readValue(decoded)

        return contentJson.result?.filter { it.slug?.contains("/seri-filmler/") == false }?.map {
            val type = if (it.type == "Movies") TvType.Movie else TvType.TvSeries
            if (type == TvType.Movie) {
                newMovieSearchResponse(it.title ?: "", fixUrl(it.slug ?: ""), type) {
                    this.posterUrl = it.poster?.replace("images-macellan-online.cdn.ampproject.org/i/s/", "")
                }
            } else {
                newTvSeriesSearchResponse(it.title ?: "", fixUrl(it.slug ?: ""), type) {
                    this.posterUrl = it.poster?.replace("images-macellan-online.cdn.ampproject.org/i/s/", "")
                }
            }
        } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val script = doc.selectFirst("script#__NEXT_DATA__")?.data() ?: throw ErrorLoadingException("Data not found")
        val secureData = mapper.readTree(script).get("props").get("pageProps").get("secureData").asText()
        val decoded = String(base64Decode(secureData).toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
        val root: Root = mapper.readValue(decoded)
        
        val item = root.contentItem
        val title = if (item.originalTitle == item.cultureTitle || item.cultureTitle.isNullOrEmpty()) item.originalTitle else "${item.originalTitle} - ${item.cultureTitle}"
        val poster = fixUrlNull(item.posterUrl?.replace("images-macellan-online.cdn.ampproject.org/i/s/", ""))
        
        val actors = root.relatedResults.getMovieCastsById?.result?.map {
            Actor(it.name ?: "", fixUrlNull(it.castImage?.replace("images-macellan-online.cdn.ampproject.org/i/s/", "")))
        }

        val trailer = root.relatedResults.getContentTrailers?.result?.firstOrNull()?.rawUrl

        if (root.relatedResults.getSerieSeasonAndEpisodes != null) {
            val eps = mutableListOf<com.lagradost.cloudstream3.Episode>()
            root.relatedResults.getSerieSeasonAndEpisodes.seasons?.forEach { season ->
                season.episodes?.forEach { ep ->
                    eps.add(newEpisode(fixUrlNull(ep.usedSlug)) {
                        this.name = ep.epText
                        this.season = season.seasonNo
                        this.episode = ep.episodeNo
                    })
                }
            }
            return newTvSeriesLoadResponse(title ?: "", url, TvType.TvSeries, eps) {
                this.posterUrl = poster
                this.year = item.releaseYear
                this.plot = item.description
                this.tags = item.categories?.split(",")
                this.score = Score.from10(item.imdbPoint)
                addActors(actors)
                addTrailer(trailer)
            }
        }

        return newMovieLoadResponse(title ?: "", url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = item.description
            this.year = item.releaseYear
            this.tags = item.categories?.split(",")
            this.score = Score.from10(item.imdbPoint)
            this.duration = item.totalMinutes
            addActors(actors)
            addTrailer(trailer)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val doc = app.get(data).document
        val script = doc.selectFirst("script#__NEXT_DATA__")?.data() ?: return false
        val secureData = mapper.readTree(script).get("props").get("pageProps").get("secureData").asText()
        val decoded = String(base64Decode(secureData).toByteArray(Charsets.UTF_8), Charsets.UTF_8)
        val root: Root = mapper.readValue(decoded)
        
        val sources = mutableListOf<SourceItem>()
        if (data.contains("/dizi/")) {
            root.relatedResults.getEpisodeSources?.result?.forEach {
                sources.add(SourceItem(it.sourceContent ?: "", it.qualityName ?: ""))
            }
        } else {
            root.relatedResults.getMoviePartsById?.result?.forEach { part ->
                val partNode = mapper.readTree(decoded).get("RelatedResults").get("getMoviePartSourcesById_${part.id}")
                partNode?.get("result")?.forEach { src ->
                    sources.add(SourceItem(src.get("source_content").asText(), src.get("quality_name").asText()))
                }
            }
        }

        sources.forEach { 
            val iframe = fixUrlNull(Jsoup.parse(it.sourceContent).select("iframe").attr("src"))
            if (iframe != null) loadExtractor(iframe, "$mainUrl/", subtitleCallback, callback)
        }
        return true
    }
}
