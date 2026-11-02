package ru.cheburnet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class GeoPoint(val lat: Double, val lon: Double)

data class UserLocation(
    val point: GeoPoint,
    val city: String?,
    val country: String?,
)

/** Публичный IP и провайдер — чтобы отчёт обучения сам себя идентифицировал. */
data class NetworkInfo(
    val ip: String?,
    val isp: String?,
)

/**
 * Примерное местоположение по IP (без GPS и без разрешений).
 * Пробуем два бесплатных сервиса, пока один не ответит.
 */
object IpLocation {

    private val endpoints = listOf(
        "https://ipwho.is/",
        "https://ipapi.co/json/",
    )

    suspend fun locate(): UserLocation? = withContext(Dispatchers.IO) {
        for (endpoint in endpoints) {
            val body = runCatching { fetch(endpoint) }.getOrNull() ?: continue
            val parsed = parse(body)
            if (parsed != null) return@withContext parsed
        }
        null
    }

    suspend fun networkInfo(): NetworkInfo? = withContext(Dispatchers.IO) {
        for (endpoint in endpoints) {
            val body = runCatching { fetch(endpoint) }.getOrNull() ?: continue
            val info = runCatching {
                val json = JSONObject(body)
                val ip = json.optString("ip").takeIf { it.isNotBlank() }
                val isp = json.optJSONObject("connection")?.optString("isp")?.takeIf { it.isNotBlank() }
                    ?: json.optString("org").takeIf { it.isNotBlank() }
                if (ip == null) null else NetworkInfo(ip, isp)
            }.getOrNull()
            if (info != null) return@withContext info
        }
        null
    }

    private fun fetch(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 3000
        connection.readTimeout = 3000
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "Cheburnet/1.2")
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    private fun parse(raw: String): UserLocation? = runCatching {
        val json = JSONObject(raw)

        val lat = json.optDouble("latitude", Double.NaN)
        val lon = json.optDouble("longitude", Double.NaN)
        if (!lat.isFinite() || !lon.isFinite()) return null

        val city = json.optString("city").takeIf { it.isNotBlank() }
        val country = (json.optString("country").takeIf { it.isNotBlank() }
            ?: json.optString("country_name").takeIf { it.isNotBlank() })

        UserLocation(
            point = GeoPoint(lat, lon),
            city = city,
            country = country,
        )
    }.getOrNull()
}
