package com.keyiflerolsun

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class CDNJWPlayer : ExtractorApi() {

    override val name = "CDN JWPlayer"
    override val mainUrl = "https://cdn.jwplayer.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: SubtitleCallback,
        callback: (ExtractorLink) -> Unit
    ) {
        callback.invoke(
            newExtractorLink(
                source = name,
                name = name,
                url = url,
                ExtractorLinkType.M3U8
            ) {
                this.referer = referer ?: ""
                this.quality = Qualities.Unknown.value
            }
        )
    }
}