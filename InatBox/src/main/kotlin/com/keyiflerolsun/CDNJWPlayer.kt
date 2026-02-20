package com.keyiflerolsun

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.ExperimentalExtractorApi

@file:OptIn(ExperimentalExtractorApi::class)
class CDNJWPlayer : ExtractorApi() {
    override val name = "JWPlayer"
    override val mainUrl = "https://cdn-jwplayer.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String, 
        referer: String?, 
        subtitleCallback: (SubtitleFile) -> Unit, 
        callback: (ExtractorLink) -> Unit
    ) {
        callback.invoke(
            newExtractorLink(
                this.name,
                this.name,
                url,
                referer ?: "",
                Qualities.P1080.value
            )
        )
    }
}
