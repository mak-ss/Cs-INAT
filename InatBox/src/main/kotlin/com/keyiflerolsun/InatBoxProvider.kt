package com.keyiflerolsun

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson

class InatBoxProvider : MainAPI() {
    override var name = "InatBox"
    override var mainUrl = "https://inat-api-url.com" // Dex içindeki API buraya
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val items = mutableListOf<HomePageList>()
        val response = app.get("$mainUrl/api/index.php?get_all_contents").text
        val data = parseJson<MainResponse>(response)

        val homeItems = data.contents?.map { 
            newMovieSearchResponse(it.title ?: "", it.id ?: "", TvType.Movie) {
                this.posterUrl = it.poster
            }
        } ?: emptyList()

        items.add(HomePageList("Yeni Eklenenler", homeItems))
        return newHomePageResponse(items)
    }

    override suspend fun load(url: String): LoadResponse {
        val detail = app.get("$mainUrl/api/content_detail.php?id=$url").parsed<Content>()
        
        return if (detail.contentType == "dizi") {
            newTvSeriesLoadResponse(detail.title!!, url, TvType.TvSeries, listOf()) {
                this.posterUrl = detail.poster
                this.plot = detail.description
            }
        } else {
            newMovieLoadResponse(detail.title!!, url, TvType.Movie, url) {
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
        val links = app.get("$mainUrl/api/get_links.php?id=$data").parsed<List<MediaResponse>>()
        links.forEach { media ->
            loadExtractor(media.videoUrl ?: "", "$mainUrl/", subtitleCallback, callback)
        }
        return true
    }
}
