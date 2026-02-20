@file:OptIn(com.lagradost.cloudstream3.utils.ExperimentalExtractorApi::class)
package com.keyiflerolsun

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.*

class CDNJWPlayer : ExtractorApi() {
    override val name = "JWPlayer"
    override val mainUrl = "https://cdn-jwplayer.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String, 
        referer: String?, 
        subtitleCallback: (SubtitleFile) -> Unit, 
        callback: (ExtractorLink) -> Unit
    ) {
        // Parametre sıralaması ve tipi CloudStream standartlarına göre düzeltildi
        callback.invoke(
            ExtractorLink(
                this.name,
                this.name,
                url,
                referer ?: "",
                Qualities.P1080.value,
                false // isM3u8 parametresi (genellikle false veya url içeriğine göre)
            )
        )
    }
}
