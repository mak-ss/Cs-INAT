package com.keyiflerolsun

import android.util.Base64
import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class InatBox : MainAPI() {

    override var mainUrl = "https://dizibox.rest"
    override var name = "InatBox"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes =
        setOf(TvType.Movie, TvType.TvSeries, TvType.Live)

    private val aesKey = "C3V4HUpUbGDOjxEl"

    override val mainPage = mainPageOf(
        "$mainUrl/amz/index.php" to "Amazon Prime",
        "$mainUrl/nf/index.php" to "Netflix"
    )

    private fun decryptInat(encrypted: String): String? {
        return try {
            val key = aesKey.toByteArray()
            val keySpec = SecretKeySpec(key, "AES")
            val ivSpec = IvParameterSpec(key)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")

            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val first = cipher.doFinal(Base64.decode(encrypted.split(":")[0], 0))

            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val second = cipher.doFinal(Base64.decode(String(first).split(":")[0], 0))

            String(second)
        } catch (e: Exception) {
            Log.e("InatBox", "Decrypt error: ${e.message}")
            null
        }
    }
}