package com.keyiflerolsun

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

class Dzen : ExtractorApi() {

    override val name = "Dzen"
    override val mainUrl = "https://dzen.ru/"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: SubtitleCallback,
        callback: (ExtractorLink) -> Unit
    ) {

        val doc = app.get(url, referer = mainUrl).document

        val script = doc.select("script")
            .find { it.data().contains("streams") }
            ?.data() ?: return

        val content = script.substringAfter("\"streams\":")
            .substringBefore("],") + "]"

        val streams = tryParseJson<List<Stream>>(content) ?: return

        streams.forEach {
            val streamUrl = it.url ?: return@forEach

            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = streamUrl,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = ""
                }
            )
        }
    }
}

data class Stream(
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("type") val type: String? = null
)