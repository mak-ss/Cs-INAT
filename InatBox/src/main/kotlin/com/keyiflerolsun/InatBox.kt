package com.keyiflerolsun

import android.util.Base64
import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class InatBox : MainAPI() {
    private val contentUrl = "https://dizibox.rest" // Güncel domain 
    override var name = "InatBox"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Live)
    
    private val aesKey = "C3V4HUpUbGDOjxEl" // bakalim.py'den gelen anahtar 
    private val urlToSearchResponse = mutableMapOf<String, SearchResponse>()

    override val mainPage = mainPageOf(
        "https://boxyz.cfd/CDN/001_STR/boxbc.sbs/spor_v2.php" to "Spor Kanalları",
        "${contentUrl}/amz/index.php" to "Amazon Prime",
        "${contentUrl}/nf/index.php" to "Netflix",
        "${contentUrl}/yabanci-dizi/index.php" to "Yabancı Diziler",
        "${contentUrl}/film/yerli-filmler.php" to "Yerli Filmler"
    )

    private fun getJsonFromEncryptedInatResponse(response: String): String? {
        return try {
            val keyBytes = aesKey.toByteArray()
            val keySpec = SecretKeySpec(keyBytes, "AES")
            val ivSpec = IvParameterSpec(keyBytes) // IV ve Key aynı 
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")

            // 1. Kademe Çözme 
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val firstPart = response.split(":")[0]
            val firstDecrypted = cipher.doFinal(Base64.decode(firstPart, Base64.DEFAULT))

            // 2. Kademe Çözme 
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val secondPart = String(firstDecrypted).split(":")[0]
            val secondDecrypted = cipher.doFinal(Base64.decode(secondPart, Base64.DEFAULT))

            String(secondDecrypted)
        } catch (e: Exception) {
            Log.e("InatBox", "Dizzy Decryption Failed: ${e.message}")
            null
        }
    }

    private suspend fun makeInatRequest(url: String): String? {
        val hostName = URI(url).host ?: return null
        
        val headers = mapOf(
            "Host" to hostName,
            "Referer" to "https://speedrestapi.com/",
            "X-Requested-With" to "com.bp.box",
            "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"
        )

        val requestBody = "1=${aesKey}&0=${aesKey}".toRequestBody(headers["Content-Type"]?.toMediaType())

        val response = app.post(url, headers = headers, requestBody = requestBody)
        return if (response.isSuccessful) {
            getJsonFromEncryptedInatResponse(response.text)
        } else null
    }

    // ... (load, search ve diğer yardımcı fonksiyonlar mevcut yapıda kalabilir)
}