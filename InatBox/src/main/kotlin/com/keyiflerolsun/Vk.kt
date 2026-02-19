package com.keyiflerolsun

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Vk : ExtractorApi() {

    override val name = "Vk"
    override val mainUrl = "https://vk.com/"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: SubtitleCallback,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url, referer = mainUrl)

        val m3u8 = Regex("\"([^\"]*m3u8[^\"]*)\"")
            .find(res.text)
            ?.groupValues?.get(1)
            ?.replace("\\/", "/")

        if (m3u8 != null) {
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = m3u8,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = mainUrl
                }
            )
        }
    }
}