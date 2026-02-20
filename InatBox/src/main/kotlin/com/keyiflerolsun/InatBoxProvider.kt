package com.keyiflerolsun

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.fasterxml.jackson.annotation.JsonProperty

// MODELLER (Hata almamak için Provider ile aynı yerde olması en sağlıklısıdır)
data class MainResponse(
    @JsonProperty("icerik") val contents: List<Content>? = null
)

data class Content(
    @JsonProperty("ID") val id: String? = null,
    @JsonProperty("Title") val title: String? = null,
    @JsonProperty("Description") val description: String? = null,
    @JsonProperty("Poster") val poster: String? = null,
    @JsonProperty("ContentType") val contentType: String? = null
)

data class MediaResponse(
    @JsonProperty("video_url") val videoUrl: String? = null,
    @JsonProperty("kalite") val quality: String? = null
)

class InatBoxProvider : MainAPI() {
    override var name = "InatBox"
    override var mainUrl = "https://inat-api-url.com" 
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val response = app.get("$mainUrl/api/index.php?get_all_contents").text
        // parseJson kullanımı düzeltildi
        val data = AppUtils.parseJson<MainResponse>(response)

        val homeItems = data.contents?.map { 
            newMovieSearchResponse(it.title ?: "", it.id ?: "", TvType.Movie) {
                this.posterUrl = it.poster
            }
        } ?: emptyList()

        return newHomePageResponse(listOf(HomePageList("Yeni Eklenenler", homeItems)))
    }

    override suspend fun load(url: String): LoadResponse {
        val detail = app.get("$mainUrl/api/content_detail.php?id=$url").parsed<Content>()
        
        return if (detail.contentType == "dizi") {
            newTvSeriesLoadResponse(detail.title ?: "", url, TvType.TvSeries, listOf()) {
                this.posterUrl = detail.poster
                this.plot = detail.description
            }
        } else {
            newMovieLoadResponse(detail.title ?: "", url, TvType.Movie, url) {
                this.posterUrl = detail.poster
                this.plot = detail.description
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val response = app.get("$mainUrl/api/get_links.php?id=$data").text
        val links = AppUtils.parseJson<List<MediaResponse>>(response)
        
        links.forEach { media ->
            if (!media.videoUrl.isNullOrEmpty()) {
                loadExtractor(media.videoUrl, "$mainUrl/", subtitleCallback, callback)
            }
        }
        return true
    }
}
