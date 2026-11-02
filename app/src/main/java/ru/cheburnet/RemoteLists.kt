package ru.cheburnet

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Обновление списков с GitHub.
 *
 * Формат JSON:
 * {
 *   "international": [{"id":"google","title":"Google DNS","host":"8.8.8.8","country":"USA","probe":"ICMP"}],
 *   "ruBlocked":     [{"id":"sber","title":"Сбер","host":"sberbank.ru","country":"RUS","probe":"TCP"}],
 *   "whitelist":     [ ... ]
 * }
 *
 * Если сеть недоступна — берётся последняя скачанная версия из кэша,
 * если и её нет — встроенные списки.
 */
object RemoteLists {

    const val URL =
        "https://raw.githubusercontent.com/3dbaxtep/cheburnet/main/lists.json"

    suspend fun refresh(context: Context): Boolean = withContext(Dispatchers.IO) {
        val cache = File(context.filesDir, "lists.json")
        val fetched = runCatching { fetch(URL) }.getOrNull()
        val body = fetched ?: cache.takeIf { it.exists() }?.readText() ?: return@withContext false
        val lists = runCatching { parse(body) }.getOrNull() ?: return@withContext false
        TargetCatalog.apply(lists)
        if (fetched != null) runCatching { cache.writeText(fetched) }
        Log.d(
            "CheburnetLists",
            (if (fetched != null) "remote " else "cache ") +
                "intl=${lists.international.size} ruBlocked=${lists.ruBlocked.size} wl=${lists.whitelist.size}",
        )
        true
    }

    private fun fetch(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 3000
        connection.readTimeout = 3000
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "Cheburnet")
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    private fun parse(body: String): TargetLists {
        val root = JSONObject(body)
        fun parseGroup(name: String, group: TargetGroup, defaultProbe: ProbeType): List<Target> {
            val array = root.optJSONArray(name) ?: return emptyList()
            return (0 until array.length()).mapNotNull { index ->
                val o = array.optJSONObject(index) ?: return@mapNotNull null
                val host = o.optString("host").trim()
                if (host.isEmpty()) return@mapNotNull null
                val probe = when (o.optString("probe").uppercase()) {
                    "TCP" -> ProbeType.TCP
                    "ICMP" -> ProbeType.ICMP
                    else -> defaultProbe
                }
                Target(
                    id = o.optString("id").ifBlank { host },
                    title = o.optString("title").ifBlank { host },
                    host = host,
                    country = o.optString("country").ifBlank { "RUS" },
                    group = group,
                    probe = probe,
                )
            }
        }

        val learning = root.optJSONArray("learning")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                when (val item = array.opt(index)) {
                    is String -> item.trim().ifBlank { null }
                    is JSONObject -> item.optString("host").trim().ifBlank { null }
                    else -> null
                }
            }
        } ?: emptyList()

        val defaults = TargetCatalog.defaults
        val international = parseGroup("international", TargetGroup.INTERNATIONAL, ProbeType.ICMP)
            .ifEmpty { defaults.international }
        val ruBlocked = parseGroup("ruBlocked", TargetGroup.RU_BLOCKED, ProbeType.TCP)
            .ifEmpty { parseGroup("ruOther", TargetGroup.RU_BLOCKED, ProbeType.TCP) }
            .ifEmpty { defaults.ruBlocked }
        val whitelist = parseGroup("whitelist", TargetGroup.WHITELIST, ProbeType.TCP)
            .ifEmpty { defaults.whitelist }

        return TargetLists(international, ruBlocked, whitelist, learning)
    }
}
