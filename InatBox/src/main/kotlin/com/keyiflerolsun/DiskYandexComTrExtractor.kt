package com.keyiflerolsun

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import java.util.regex.Pattern

class DiskYandexComTr : ExtractorApi() {
    override val name: String = "DiskYandexComTr"
    override val mainUrl: String = "https://disk.yandex.com.tr"
    override val requiresReferer: Boolean = false
    private val masterPlaylistRegex = Pattern.compile("https?://[^\\s\"]*?master-playlist\\.m3u8")

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val request = app.get(url, referer = "https://disk.yandex.com.tr/", headers = mapOf("X-Requested-With" to "XMLHttpRequest"))
        if (!request.isSuccessful) return

        val matcher = masterPlaylistRegex.matcher(request.text)
        if (matcher.find()) {
            callback.invoke(
                ExtractorLink(
                    "Yandex Disk",
                    "Yandex Disk",
                    matcher.group(),
                    referer ?: "",
                    Qualities.Unknown.value,
                    type = ExtractorLinkType.M3U8
                )
            )
        }
    }
}
