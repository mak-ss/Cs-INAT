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
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        val document = app.get(
            url,
            referer = mainUrl,
            headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        ).document

        val script = document.select("script")
            .find { it.data().contains("streams") }
            ?.data() ?: return

        val content = script.substringAfter("\"streams\":")
            .substringBefore("],") + "]"

        val streams = tryParseJson<List<Stream>>(content) ?: return

        streams.forEach { stream ->

            val streamUrl = stream.url ?: return@forEach
            val type = stream.type ?: return@forEach

            val quality = when {
                type.contains("fullhd") -> Qualities.P1080.value
                type.contains("high") -> Qualities.P720.value
                type.contains("medium") -> Qualities.P480.value
                type.contains("low") -> Qualities.P360.value
                else -> Qualities.Unknown.value
            }

            callback.invoke(
                ExtractorLink(
                    source = name,
                    name = name,
                    url = streamUrl,
                    referer = "",
                    quality = quality,
                    type = if (type == "hls")
                        ExtractorLinkType.M3U8
                    else
                        ExtractorLinkType.VIDEO
                )
            )
        }
    }
}

private data class Stream(
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("type") val type: String? = null
)
