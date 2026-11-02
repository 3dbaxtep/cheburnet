package ru.cheburnet

import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Проверка доступности.
 *  - ICMP (3 пакета, успех при любом ответе) — для зарубежных IP.
 *  - HTTPS-запрос — для российских сайтов: DPI в БС пропускает TCP-хэндшейк,
 *    но рвёт TLS/HTTP, поэтому «порт открыт» ничего не значит. Считаем сайт
 *    доступным только если пришёл реальный HTTP-ответ.
 */
object Net {

    private val timeRegex = Regex("""time[=<]\s*([0-9]+(?:\.[0-9]+)?)\s*ms""")

    private val pingBinary: String by lazy {
        listOf("/system/bin/ping", "/system/xbin/ping", "ping")
            .firstOrNull { it == "ping" || File(it).exists() } ?: "ping"
    }

    private val dnsPool = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "cheburnet-dns").apply { isDaemon = true }
    }

    suspend fun probe(target: Target): Int? = probeRaw(
        host = target.host,
        probe = target.probe,
        allowHttpFallback = target.group == TargetGroup.WHITELIST,
    )

    suspend fun probeRaw(
        host: String,
        probe: ProbeType,
        allowHttpFallback: Boolean = false,
    ): Int? = withContext(Dispatchers.IO) {
        when (probe) {
            ProbeType.ICMP -> icmpProbe(host, 2)
            ProbeType.TCP -> {
                // Повтор на случай обрыва; успех — только реальный HTTP-ответ.
                var result = httpsProbe(host, 4000) ?: httpsProbe(host, 4000)
                // Некоторые сайты (например, ВТБ) режут HTTPS, но отвечают по HTTP.
                if (result == null && allowHttpFallback) {
                    result = httpProbe(host, 4000)
                }
                result
            }
        }
    }

    // ---------------------------------------------------------------- ICMP

    private fun icmpProbe(host: String, timeoutSec: Int): Int? {
        return try {
            val process = ProcessBuilder(pingBinary, "-c", "3", "-W", timeoutSec.toString(), host)
                .redirectErrorStream(true)
                .start()
            // Ждём не дольше, чем нужно трём пакетам: иначе зависший ping
            // съедает весь бюджет проверки.
            if (!waitFor(process, (timeoutSec * 3 + 2) * 1000L)) {
                process.destroy()
                return null
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val times = timeRegex.findAll(output)
                .mapNotNull { it.groupValues[1].toDoubleOrNull() }
                .filter { it.isFinite() && it in 0.0..30_000.0 }
                .toList()
            times.minOrNull()?.toInt()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
    }

    // ----------------------------------------------------------------- TCP

    private fun httpsProbe(host: String, timeoutMs: Int): Int? {
        val started = System.nanoTime()
        var connection: HttpsURLConnection? = null
        return try {
            connection = (URL("https://$host/").openConnection() as HttpsURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("Range", "bytes=0-1")
                setRequestProperty("User-Agent", "Cheburnet/1.27")
            }
            val code = connection.responseCode
            if (code <= 0) {
                null
            } else {
                runCatching { connection.inputStream?.close() }
                ((System.nanoTime() - started) / 1_000_000L).toInt()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    /**
     * DNS с жёстким таймаутом; при наличии выбираем IPv4 — иначе на сетях без
     * рабочего IPv6 TCP-коннект висит до таймаута (те самые «3 секунды»).
     */
    private fun resolve(host: String, timeoutMs: Long): InetAddress? {
        val future = dnsPool.submit(Callable {
            val all = runCatching { InetAddress.getAllByName(host) }.getOrNull()
            all?.firstOrNull { it is java.net.Inet4Address } ?: all?.firstOrNull()
        })
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            null
        } catch (e: CancellationException) {
            future.cancel(true)
            throw e
        } catch (_: Throwable) {
            future.cancel(true)
            null
        }
    }

    private fun httpProbe(host: String, timeoutMs: Int): Int? {
        val started = System.nanoTime()
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL("http://$host/").openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("Range", "bytes=0-1")
                setRequestProperty("User-Agent", "Cheburnet/1.30")
            }
            val code = connection.responseCode
            if (code <= 0) {
                null
            } else {
                runCatching { connection.inputStream?.close() }
                ((System.nanoTime() - started) / 1_000_000L).toInt()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    /** Process.waitFor(timeout) есть только с API 26. */
    private fun waitFor(process: Process, timeoutMs: Long): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        }
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            try {
                process.exitValue()
                return true
            } catch (_: IllegalThreadStateException) {
                Thread.sleep(20)
            }
        }
        return false
    }
}
