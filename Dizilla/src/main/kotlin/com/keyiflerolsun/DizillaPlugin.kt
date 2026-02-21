package com.keyiflerolsun

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class DizillaPlugin: Plugin() {
    override fun load(context: Context) {
        // Ana API sınıfını burada kaydediyoruz
        registerMainAPI(Dizilla())
    }
}
