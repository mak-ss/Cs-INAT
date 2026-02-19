package com.keyiflerolsun

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Vk : ExtractorApi() {
    override val name = "Vk"
    override val mainUrl = "https://vk.com/"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val response = app.get(url, headers = mapOf("X-Requested-With" to "XMLHttpRequest"), referer = this.mainUrl)
        val m3u8Regex = Regex(""""([^"]*m3u8[^"]*)"""")
        val m3u8SourceUrl = m3u8Regex.find(response.text)?.groupValues?.get(1)?.replace("\\/", "/")

        if (m3u8SourceUrl != null) {
            callback.invoke(
                ExtractorLink(
                    this.name,
                    this.name,
                    m3u8SourceUrl,
                    this.mainUrl,
                    Qualities.Unknown.value,
                    type = ExtractorLinkType.M3U8
                )
            )
        }
    }
}
