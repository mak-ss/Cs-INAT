package com.keyiflerolsun

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
import com.lagradost.cloudstream3.Episode as CloudstreamEpisode

class Dizilla : MainAPI() {
    override var mainUrl = "https://dizilla.to"
    override var name = "Dizilla"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val mapper: ObjectMapper by lazy {
        ObjectMapper().registerModule(KotlinModule.Builder().build()).apply {
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }

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

    private fun cleanPosterUrl(url: String?): String? {
        return url?.replace("images-macellan-online.cdn.ampproject.org/i/s/", "")?.let { fixUrlNull(it) }
    }

    private fun decodeSecureData(data: String?): String {
        if (data.isNullOrBlank()) return ""
        val cleanData = data.replace("\"", "")
        return try {
            val decodedBytes = base64Decode(cleanData).toByteArray(Charsets.ISO_8859_1)
            String(decodedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            try {
                String(base64Decode(cleanData).toByteArray(), Charsets.UTF_8)
            } catch (inner: Exception) { "" }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val type = if (request.name.contains("Dizi")) "findSeries" else "findMovies"
        val url = "$mainUrl/api/bg/$type?releaseYearStart=1900&releaseYearEnd=$year&imdbPointMin=1&imdbPointMax=10&categoryIdsComma=${request.data}&orderType=date_desc&currentPage=$page&currentPageCount=12"

        val response = app.post(url, headers = mapOf("X-Requested-With" to "XMLHttpRequest")).toString()
        val searchResult: SearchResult = mapper.readValue(response)
        val listItems: ListItems = mapper.readValue(decodeSecureData(searchResult.response))
        
        return newHomePageResponse(request.name, listItems.result.map { it.toSearchResponse() })
    }

    private fun ContentItem.toSearchResponse(): SearchResponse {
        val title = this.originalTitle ?: this.cultureTitle ?: ""
        val href = fixUrl(this.usedSlug ?: "")
        val poster = cleanPosterUrl(this.posterUrl)
        
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
        val url = "$mainUrl/api/bg/searchcontent?searchterm=$query"
        val response = app.post(url, headers = mapOf("X-Requested-With" to "XMLHttpRequest")).toString()
        val searchResult: SearchResult = mapper.readValue(response)
        val content: SearchData = mapper.readValue(decodeSecureData(searchResult.response))

        return content.result?.filter { it.slug?.contains("/seri-filmler/") == false }?.map {
            val isMovie = it.type == "Movies"
            val title = it.title ?: ""
            val href = fixUrl(it.slug ?: "")
            val poster = cleanPosterUrl(it.poster)
            
            if (isMovie) {
                newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = poster }
            } else {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = poster }
            }
        } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val jsonStr = doc.selectFirst("script#__NEXT_DATA__")?.data() ?: throw ErrorLoadingException("Data error")
        val secureData = mapper.readTree(jsonStr).at("/props/pageProps/secureData").asText()
        val root: Root = mapper.readValue(decodeSecureData(secureData))
        
        val item = root.contentItem
        val title = item.originalTitle ?: item.cultureTitle ?: ""
        val poster = cleanPosterUrl(item.posterUrl)
        
        val actors = root.relatedResults.getMovieCastsById?.result?.map {
            Actor(it.name ?: "", cleanPosterUrl(it.castImage))
        }
        val trailer = root.relatedResults.getContentTrailers?.result?.firstOrNull()?.rawUrl

        if (root.relatedResults.getSerieSeasonAndEpisodes != null) {
            val episodes = mutableListOf<CloudstreamEpisode>()
            root.relatedResults.getSerieSeasonAndEpisodes.seasons?.forEach { season ->
                season.episodes?.forEach { ep ->
                    episodes.add(newEpisode(fixUrlNull(ep.usedSlug)) {
                        this.name = ep.epText
                        this.season = season.seasonNo
                        this.episode = ep.episodeNo
                    })
                }
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = item.releaseYear
                this.plot = item.description
                this.tags = item.categories?.split(",")
                this.score = Score.from10(item.imdbPoint)
                addActors(actors)
                addTrailer(trailer)
            }
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
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
        val jsonStr = doc.selectFirst("script#__NEXT_DATA__")?.data() ?: return false
        val secureData = mapper.readTree(jsonStr).at("/props/pageProps/secureData").asText()
        val decoded = decodeSecureData(secureData)
        val root: Root = mapper.readValue(decoded)
        
        val sources = mutableListOf<SourceItem>()
        if (data.contains("/dizi/")) {
            root.relatedResults.getEpisodeSources?.result?.forEach {
                sources.add(SourceItem(it.sourceContent ?: "", it.qualityName ?: ""))
            }
        } else {
            root.relatedResults.getMoviePartsById?.result?.forEach { part ->
                val partSources = mapper.readTree(decoded).at("/RelatedResults/getMoviePartSourcesById_${part.id}/result")
                partSources.forEach { src ->
                    sources.add(SourceItem(src.get("source_content").asText(), src.get("quality_name").asText()))
                }
            }
        }

        sources.forEach { item ->
            val iframeUrl = Jsoup.parse(item.sourceContent).select("iframe").attr("src").let { fixUrlNull(it) }
            if (iframeUrl != null) {
                loadExtractor(iframeUrl, "$mainUrl/", subtitleCallback, callback)
            }
        }
        return true
    }
}
