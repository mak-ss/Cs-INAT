package com.keyiflerolsun

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import java.util.regex.Pattern

class DiskYandexComTr : ExtractorApi() {

    override val name = "Yandex Disk"
    override val mainUrl = "https://disk.yandex.com.tr"
    override val requiresReferer = false

    private val masterPlaylistRegex =
        Pattern.compile("https?://[^\\s\"]*?master-playlist\\.m3u8")

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        val response = app.get(
            url,
            referer = mainUrl,
            headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        )

        if (!response.isSuccessful) return

        val matcher = masterPlaylistRegex.matcher(response.text)

        if (matcher.find()) {
            callback.invoke(
                ExtractorLink(
                    source = name,
                    name = name,
                    url = matcher.group(),
                    referer = mainUrl,
                    quality = Qualities.Unknown.value,
                    type = ExtractorLinkType.M3U8
                )
            )
        }
    }
}
