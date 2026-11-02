package ru.cheburnet

/** Группа ресурса. */
enum class TargetGroup { INTERNATIONAL, RU_BLOCKED, WHITELIST }

/** Чем проверяем: ICMP для зарубежных IP, TCP:443 для сайтов (банки режут ping). */
enum class ProbeType { ICMP, TCP }

data class Target(
    val id: String,
    val title: String,
    val host: String,
    val country: String,
    val group: TargetGroup,
    val probe: ProbeType = ProbeType.ICMP,
)

data class TargetResult(
    val target: Target,
    val ms: Int?,
    val ok: Boolean,
)

enum class Verdict { IDLE, CHECKING, OPEN, PARTIAL, CHEBURNET, WHITELIST_ONLY, OFFLINE }

data class UiState(
    val verdict: Verdict = Verdict.IDLE,
    val results: List<TargetResult> = emptyList(),
    val done: Int = 0,
    val total: Int = TargetCatalog.all.size,
    val user: GeoPoint? = null,
    val userLabel: String? = null,
    /** true, пока тянем списки с GitHub — до этого проверку не запускаем. */
    val listsLoading: Boolean = false,
) {
    val international: List<TargetResult>
        get() = results.filter { it.target.group == TargetGroup.INTERNATIONAL }
    val ruBlocked: List<TargetResult>
        get() = results.filter { it.target.group == TargetGroup.RU_BLOCKED }
    val whitelist: List<TargetResult>
        get() = results.filter { it.target.group == TargetGroup.WHITELIST }

    val intlOk: Int get() = international.count { it.ok }

    /** Балльная оценка: 10000 за мир, 100 за Россию вне БС, 1 за белый список. */
    val score: Int get() = VerdictLogic.score(intlOk, ruBlockedOk, ruOk)
    val ruBlockedOk: Int get() = ruBlocked.count { it.ok }
    val ruOk: Int get() = whitelist.count { it.ok }
}

object VerdictLogic {

    /** Веса: мир — 10000, Россия вне БС — 100, белый список — 1 балл за ресурс. */
    const val WORLD_WEIGHT = 10_000
    const val RU_WEIGHT = 100
    const val WL_WEIGHT = 1

    fun score(intlOk: Int, ruBlockedOk: Int, whitelistOk: Int): Int =
        WORLD_WEIGHT * intlOk + RU_WEIGHT * ruBlockedOk + WL_WEIGHT * whitelistOk

    /** Доли, при которых меняется режим. */
    const val WORLD_OPEN_SHARE = 0.6    // > 60% мира — открытый интернет
    const val RU_CHEBURNET_SHARE = 0.5  // > 50% России — чебурнет, иначе БС

    fun decide(
        intlOk: Int,
        intlTotal: Int,
        ruBlockedOk: Int,
        ruBlockedTotal: Int,
        whitelistOk: Int,
    ): Verdict {
        if (intlTotal <= 0) return Verdict.IDLE

        // Внешний интернет вообще не отвечает — делим Россию и белый список.
        if (intlOk == 0) {
            val ruShare = if (ruBlockedTotal > 0) {
                ruBlockedOk.toDouble() / ruBlockedTotal
            } else {
                0.0
            }
            return when {
                ruBlockedOk == 0 && whitelistOk == 0 -> Verdict.OFFLINE
                ruShare > RU_CHEBURNET_SHARE -> Verdict.CHEBURNET
                whitelistOk > 0 -> Verdict.WHITELIST_ONLY
                else -> Verdict.OFFLINE
            }
        }

        // Мир отвечает: смотрим, какая его доля доступна.
        val worldShare = intlOk.toDouble() / intlTotal
        return if (worldShare > WORLD_OPEN_SHARE) Verdict.OPEN else Verdict.PARTIAL
    }

    fun litCountries(results: List<TargetResult>, verdict: Verdict): Set<String> = when (verdict) {
        Verdict.OPEN, Verdict.WHITELIST_ONLY, Verdict.OFFLINE, Verdict.IDLE -> emptySet()
        Verdict.CHEBURNET -> setOf("RUS")
        else -> results.filter { it.ok }.map { it.target.country }.toSet()
    }
}
