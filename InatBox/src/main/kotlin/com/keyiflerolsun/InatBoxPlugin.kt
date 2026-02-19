package com.keyiflerolsun

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class InatBoxPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(InatBox())
        // Mevcut ayıklayıcıları (Extractors) kaydediyoruz
        registerExtractorAPI(DiskYandexComTr())
        registerExtractorAPI(Vk())
        registerExtractorAPI(Dzen())
        registerExtractorAPI(CDNJWPlayer())
    }
}