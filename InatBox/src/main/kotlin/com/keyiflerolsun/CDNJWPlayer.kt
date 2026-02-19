package com.keyiflerolsun

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class CDNJWPlayer : ExtractorApi() {

    override val name = "CDNJWPlayer"
    override val mainUrl = "https://cdnjwplayer.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url)

        val m3u8 = Regex("""file:\s*["']([^"']+\.m3u8[^"']*)""")
            .find(response.text)
            ?.groupValues?.get(1)

        if (m3u8 != null) {
            callback.invoke(
                ExtractorLink(
                    name,
                    name,
                    m3u8,
                    "",
                    Qualities.Unknown.value,
                    ExtractorLinkType.M3U8
                )
            )
        }
    }
}
