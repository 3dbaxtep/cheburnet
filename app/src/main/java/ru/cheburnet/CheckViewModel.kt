package ru.cheburnet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

class CheckViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _learning = MutableStateFlow(LearningState())
    val learning: StateFlow<LearningState> = _learning.asStateFlow()

    init {
        // Сначала списки с GitHub (или кэш/встроенные), только потом проверка.
        _state.update { it.copy(listsLoading = true) }
        viewModelScope.launch {
            RemoteLists.refresh(getApplication())
            _state.update { it.copy(listsLoading = false) }
            locateUser()
            check()
        }
    }

    /** IP-геолокация, без GPS и без разрешений; если недоступна — Москва. */
    fun locateUser() {
        viewModelScope.launch {
            val location = IpLocation.locate()
            val label = if (location != null) {
                listOfNotNull(location.city, location.country).joinToString(", ").ifBlank { null }
            } else {
                "Москва (примерно)"
            }
            _state.update {
                it.copy(
                    user = location?.point ?: GeoPoint(55.75, 37.62),
                    userLabel = label,
                )
            }
        }
    }

    fun check() {
        if (_state.value.verdict == Verdict.CHECKING) return
        // Не начинаем проверку, пока списки не загружены.
        if (_state.value.listsLoading) return

        val targets = TargetCatalog.all
        val collected = Collections.synchronizedList(mutableListOf<TargetResult>())
        val counter = AtomicInteger(0)

        _state.update {
            it.copy(
                verdict = Verdict.CHECKING,
                results = emptyList(),
                done = 0,
                total = targets.size,
                listsLoading = false,
            )
        }

        val probesJob = viewModelScope.launch {
            val gate = Semaphore(16)
            targets.forEach { target ->
                if (_state.value.verdict != Verdict.CHECKING) return@forEach
                launch(Dispatchers.IO) {
                    val ms = gate.withPermit { Net.probe(target) }
                    if (_state.value.verdict != Verdict.CHECKING) return@launch
                    collected += TargetResult(target = target, ms = ms, ok = ms != null)
                    _state.update {
                        it.copy(
                            results = collected.toList(),
                            done = counter.incrementAndGet(),
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            val deadline = System.currentTimeMillis() + 20_000
            while (System.currentTimeMillis() < deadline && !probesJob.isCompleted) {
                delay(100)
            }
            if (_state.value.verdict == Verdict.CHECKING) {
                probesJob.cancel()
                finishCheck(collected.toList())
            }
        }
    }

    private fun finishCheck(results: List<TargetResult>) {
        val intl = results.filter { it.target.group == TargetGroup.INTERNATIONAL }
        val ruBlocked = results.filter { it.target.group == TargetGroup.RU_BLOCKED }
        val whitelist = results.filter { it.target.group == TargetGroup.WHITELIST }

        val verdict = VerdictLogic.decide(
            intlOk = intl.count { it.ok },
            intlTotal = maxOf(intl.size, TargetCatalog.international.size),
            ruBlockedOk = ruBlocked.count { it.ok },
            ruBlockedTotal = maxOf(ruBlocked.size, TargetCatalog.ruBlocked.size),
            whitelistOk = whitelist.count { it.ok },
        )
        _state.update {
            it.copy(
                verdict = verdict,
                results = results,
                done = results.size,
                total = TargetCatalog.all.size,
            )
        }
    }

    /** Режим обучения: расширенный прогон, отчёт уходит в Telegram. */
    fun startLearning() {
        if (_learning.value.running) return
        val targets = LearningCatalog.build(TargetCatalog.all)
        _learning.value = LearningState(running = true, total = targets.size)
        viewModelScope.launch {
            val collected = Collections.synchronizedList(mutableListOf<LearningResult>())
            val counter = AtomicInteger(0)
            val gate = Semaphore(12)
            val jobs = targets.map { target ->
                launch(Dispatchers.IO) {
                    val ms = gate.withPermit {
                        Net.probeRaw(target.host, target.probe, allowHttpFallback = target.group == "WL")
                    }
                    collected += LearningResult(target = target, ms = ms, ok = ms != null)
                    _learning.update {
                        it.copy(results = collected.toList(), done = counter.incrementAndGet())
                    }
                }
            }
            withTimeoutOrNull(240_000) { jobs.forEach { it.join() } }
            val network = runCatching { IpLocation.networkInfo() }.getOrNull()
            val done = collected.toList()
            // Вердикт считаем строго по боевым спискам (15 IP + 6 маркеров + 10
            // белого списка), а не по всем 138 хостам обучения.
            val intlHosts = TargetCatalog.international.map { it.host }.toSet()
            val markerHosts = TargetCatalog.ruBlocked.map { it.host }.toSet()
            val wlHosts = TargetCatalog.whitelist.map { it.host }.toSet()
            fun okCount(hosts: Set<String>) = done.count { it.target.host in hosts && it.ok }
            val intlOk = okCount(intlHosts)
            val ruOk = okCount(markerHosts)
            val wlOk = okCount(wlHosts)
            val ruAll = done.filter { it.target.group == "RU" }
            val report = LearningReport.build(
                context = getApplication(),
                network = network,
                verdict = VerdictLogic.decide(intlOk, intlHosts.size, ruOk, markerHosts.size, wlOk),
                intlOk = intlOk,
                intlTotal = intlHosts.size,
                ruBlockedOk = ruOk,
                ruBlockedTotal = markerHosts.size,
                wlOk = wlOk,
                wlTotal = wlHosts.size,
                ruAllOk = ruAll.count { it.ok },
                ruAllTotal = ruAll.size,
                results = done,
            )
            _learning.update {
                it.copy(running = false, results = collected.toList(), report = report, done = collected.size)
            }
        }
    }

    /** Только для debug-сборок: переключение сценариев. */
    fun demoNext() {
        if (!BuildConfig.DEBUG) return
        val next = when (_state.value.verdict) {
            Verdict.IDLE -> Verdict.OPEN
            Verdict.OPEN -> Verdict.PARTIAL
            Verdict.PARTIAL -> Verdict.CHEBURNET
            Verdict.CHEBURNET -> Verdict.WHITELIST_ONLY
            Verdict.WHITELIST_ONLY -> Verdict.OFFLINE
            else -> Verdict.OPEN
        }
        val base = _state.value
        _state.value = demoState(next).copy(user = base.user, userLabel = base.userLabel)
    }

    private fun demoState(verdict: Verdict): UiState {
        val intlTotal = TargetCatalog.international.size
        val whitelistTotal = TargetCatalog.whitelist.size
        val intlOk: Int
        val ruBlockedOk: Int
        val whitelistOk: Int
        when (verdict) {
            Verdict.OPEN -> { intlOk = (intlTotal * 0.85).toInt(); ruBlockedOk = 6; whitelistOk = whitelistTotal }
            Verdict.PARTIAL -> { intlOk = (intlTotal * 0.4).toInt(); ruBlockedOk = 6; whitelistOk = whitelistTotal }
            Verdict.CHEBURNET -> { intlOk = 0; ruBlockedOk = 4; whitelistOk = whitelistTotal }
            Verdict.WHITELIST_ONLY -> { intlOk = 0; ruBlockedOk = 0; whitelistOk = whitelistTotal - 1 }
            else -> { intlOk = 0; ruBlockedOk = 0; whitelistOk = 0 }
        }
        val results = TargetCatalog.international.mapIndexed { index, target ->
            val ok = index < intlOk
            TargetResult(target, if (ok) 35 + index * 7 else null, ok)
        } + TargetCatalog.ruBlocked.mapIndexed { index, target ->
            val ok = index < ruBlockedOk
            TargetResult(target, if (ok) 20 + index * 6 else null, ok)
        } + TargetCatalog.whitelist.mapIndexed { index, target ->
            val ok = index < whitelistOk
            TargetResult(target, if (ok) 18 + index * 5 else null, ok)
        }
        return UiState(verdict = verdict, results = results, done = TargetCatalog.all.size, total = TargetCatalog.all.size)
    }
}
