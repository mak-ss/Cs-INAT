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
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        callback.invoke(
            ExtractorLink(
                source = name,
                name = name,
                url = url,
                referer = referer ?: "",
                quality = Qualities.Unknown.value,
                type = ExtractorLinkType.M3U8
            )
        )
    }
}
