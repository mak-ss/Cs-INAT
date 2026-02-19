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
        referer: String?
    ): List<ExtractorLink>? {

        val doc = app.get(url, referer = mainUrl).document

        val script = doc.select("script")
            .find { it.data().contains("streams") }
            ?.data() ?: return null

        val content = script.substringAfter("\"streams\":")
            .substringBefore("],") + "]"

        val streams = tryParseJson<List<Stream>>(content) ?: return null

        return streams.mapNotNull {
            val streamUrl = it.url ?: return@mapNotNull null

            ExtractorLink(
                source = name,
                name = name,
                url = streamUrl,
                referer = "",
                quality = Qualities.Unknown.value,
                type = ExtractorLinkType.M3U8
            )
        }
    }
}

data class Stream(
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("type") val type: String? = null
)
