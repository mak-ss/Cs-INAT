package com.keyiflerolsun

import android.util.Base64
import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class InatBox : MainAPI() {
    private val contentUrl = "https://dizibox.rest"
    override var name = "InatBox"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Live)
    
    private val aesKey = "C3V4HUpUbGDOjxEl"

    override val mainPage = mainPageOf(
        "https://boxyz.cfd/CDN/001_STR/boxbc.sbs/spor_v2.php" to "Spor Kanalları",
        "${contentUrl}/amz/index.php" to "Amazon Prime",
        "${contentUrl}/nf/index.php" to "Netflix",
        "${contentUrl}/yabanci-dizi/index.php" to "Yabancı Diziler",
        "${contentUrl}/film/yerli-filmler.php" to "Yerli Filmler"
    )

    private fun decryptInat(encryptedData: String): String? {
        return try {
            val keyBytes = aesKey.toByteArray()
            val keySpec = SecretKeySpec(keyBytes, "AES")
            val ivSpec = IvParameterSpec(keyBytes)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")

            // 1. Kademe
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val firstBase64 = encryptedData.split(":")[0]
            val firstDecrypted = cipher.doFinal(Base64.decode(firstBase64, Base64.DEFAULT))

            // 2. Kademe
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val secondBase64 = String(firstDecrypted).split(":")[0]
            val secondDecrypted = cipher.doFinal(Base64.decode(secondBase64, Base64.DEFAULT))

            String(secondDecrypted)
        } catch (e: Exception) {
            Log.e("InatBox", "Çözme hatası: ${e.message}")
            null
        }
    }

    private suspend fun makeInatRequest(url: String): String? {
        val hostName = URI(url).host ?: return null
        val headers = mapOf(
            "Host" to hostName,
            "Referer" to "https://speedrestapi.com/",
            "X-Requested-With" to "com.bp.box",
            "User-Agent" to "speedrestapi",
            "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"
        )
        val body = "1=${aesKey}&0=${aesKey}".toRequestBody(headers["Content-Type"]?.toMediaType())
        val response = app.post(url, headers = headers, requestBody = body)
        
        return if (response.isSuccessful) decryptInat(response.text) else null
    }

    // Arama ve Yükleme fonksiyonları makeInatRequest'i kullanacak şekilde devam eder...
}
