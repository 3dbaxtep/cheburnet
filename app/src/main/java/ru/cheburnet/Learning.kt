package ru.cheburnet

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LearningTarget(
    val id: String,
    val title: String,
    val host: String,
    val group: String,
    val probe: ProbeType,
)

data class LearningResult(
    val target: LearningTarget,
    val ms: Int?,
    val ok: Boolean,
)

data class LearningState(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val results: List<LearningResult> = emptyList(),
    val report: String? = null,
)

/**
 * Режим обучения: прогоняем расширенный список российских и зарубежных хостов,
 * чтобы понять, что именно доступно в режиме «белых списков» у конкретного
 * оператора. Отчёт отправляется в Telegram, по нему обновляются lists.json.
 */
object LearningCatalog {

    /** Список из lists.json (GitHub). Если пуст — используем встроенный. */
    @Volatile
    var remote: List<LearningTarget>? = null

    private fun t(id: String, title: String, host: String, group: String, probe: ProbeType) =
        LearningTarget(id, title, host, group, probe)

    private val extraRu = listOf(
        // Банки вне белого списка
        t("sber", "Сбер", "sberbank.ru", "RU", ProbeType.TCP),
        t("tbank", "Т-Банк", "tinkoff.ru", "RU", ProbeType.TCP),
        t("rshb", "РСХБ", "rshb.ru", "RU", ProbeType.TCP),
        t("sovcom", "Совкомбанк", "sovcombank.ru", "RU", ProbeType.TCP),
        t("raiffeisen", "Райффайзен", "raiffeisen.ru", "RU", ProbeType.TCP),
        t("mkb", "МКБ", "mkb.ru", "RU", ProbeType.TCP),
        t("otkritie", "Открытие", "open.ru", "RU", ProbeType.TCP),
        t("rosbank", "Росбанк", "rosbank.ru", "RU", ProbeType.TCP),
        t("uralsib", "Уралсиб", "uralsib.ru", "RU", ProbeType.TCP),
        t("gazprom", "Газпромбанк", "gazprombank.ru", "RU", ProbeType.TCP),
        // Маркетплейсы и сервисы
        t("avito", "Авито", "avito.ru", "RU", ProbeType.TCP),
        t("dns", "DNS", "dns-shop.ru", "RU", ProbeType.TCP),
        t("mvideo", "М.Видео", "mvideo.ru", "RU", ProbeType.TCP),
        t("citilink", "Ситилинк", "citilink.ru", "RU", ProbeType.TCP),
        t("magnit", "Магнит", "magnit.ru", "RU", ProbeType.TCP),
        t("pyaterochka", "Пятёрочка", "5ka.ru", "RU", ProbeType.TCP),
        t("samokat", "Самокат", "samokat.ru", "RU", ProbeType.TCP),
        t("sbermarket", "СберМаркет", "sbermarket.ru", "RU", ProbeType.TCP),
        t("rzd", "РЖД", "rzd.ru", "RU", ProbeType.TCP),
        t("aeroflot", "Аэрофлот", "aeroflot.ru", "RU", ProbeType.TCP),
        t("s7", "S7", "s7.ru", "RU", ProbeType.TCP),
        t("cian", "Циан", "cian.ru", "RU", ProbeType.TCP),
        t("domclick", "Домклик", "domclick.ru", "RU", ProbeType.TCP),
        t("hh", "hh.ru", "hh.ru", "RU", ProbeType.TCP),
        t("superjob", "SuperJob", "superjob.ru", "RU", ProbeType.TCP),
        // СМИ и контент
        t("rbc", "РБК", "rbc.ru", "RU", ProbeType.TCP),
        t("lenta", "Лента.ру", "lenta.ru", "RU", ProbeType.TCP),
        t("rambler", "Рамблер", "rambler.ru", "RU", ProbeType.TCP),
        t("ria", "РИА", "ria.ru", "RU", ProbeType.TCP),
        t("gazeta", "Газета.ру", "gazeta.ru", "RU", ProbeType.TCP),
        t("kp", "КП", "kp.ru", "RU", ProbeType.TCP),
        t("habr", "Хабр", "habr.com", "RU", ProbeType.TCP),
        t("vc", "vc.ru", "vc.ru", "RU", ProbeType.TCP),
        t("banki", "Банки.ру", "banki.ru", "RU", ProbeType.TCP),
        // Операторы
        t("mts", "МТС", "mts.ru", "RU", ProbeType.TCP),
        t("megafon", "МегаФон", "megafon.ru", "RU", ProbeType.TCP),
        t("beeline", "Билайн", "beeline.ru", "RU", ProbeType.TCP),
        t("tele2", "Tele2", "tele2.ru", "RU", ProbeType.TCP),
        // Региональные провайдеры и местные СМИ: в БС t2 часть из них
        // отключается, хотя федеральные сервисы остаются доступными.
        t("z_35media_ru", "35media.ru", "35media.ru", "RU", ProbeType.TCP),
        t("z_53news_ru", "53news.ru", "53news.ru", "RU", ProbeType.TCP),
        t("z_72_ru", "72.ru", "72.ru", "RU", ProbeType.TCP),
        t("z_59_ru", "59.ru", "59.ru", "RU", ProbeType.TCP),
        t("z_63_ru", "63.ru", "63.ru", "RU", ProbeType.TCP),
        t("z_akado_ru", "akado.ru", "akado.ru", "RU", ProbeType.TCP),
        t("z_74_ru", "74.ru", "74.ru", "RU", ProbeType.TCP),
        t("z_29_ru", "29.ru", "29.ru", "RU", ProbeType.TCP),
        t("z_161_ru", "161.ru", "161.ru", "RU", ProbeType.TCP),
        t("z_76_ru", "76.ru", "76.ru", "RU", ProbeType.TCP),
        t("z_93_ru", "93.ru", "93.ru", "RU", ProbeType.TCP),
        t("z_adigea_ru", "adigea.ru", "adigea.ru", "RU", ProbeType.TCP),
        t("z_360tv_ru", "360tv.ru", "360tv.ru", "RU", ProbeType.TCP),
        t("z_amurmedia_ru", "amurmedia.ru", "amurmedia.ru", "RU", ProbeType.TCP),
        t("z_baikal_daily_ru", "baikal-daily.ru", "baikal-daily.ru", "RU", ProbeType.TCP),
        t("z_bryansk_ru", "bryansk.ru", "bryansk.ru", "RU", ProbeType.TCP),
        t("z_bel_ru", "bel.ru", "bel.ru", "RU", ProbeType.TCP),
        t("z_dom_ru", "dom.ru", "dom.ru", "RU", ProbeType.TCP),
        t("z_chita_ru", "chita.ru", "chita.ru", "RU", ProbeType.TCP),
        t("z_bashkortostan_ru", "bashkortostan.ru", "bashkortostan.ru", "RU", ProbeType.TCP),
        t("z_eaomedia_ru", "eaomedia.ru", "eaomedia.ru", "RU", ProbeType.TCP),
        t("z_buryatia_ru", "buryatia.ru", "buryatia.ru", "RU", ProbeType.TCP),
        t("z_chelyabinsk_ru", "chelyabinsk.ru", "chelyabinsk.ru", "RU", ProbeType.TCP),
        t("z_irk_ru", "irk.ru", "irk.ru", "RU", ProbeType.TCP),
        t("z_is74_ru", "is74.ru", "is74.ru", "RU", ProbeType.TCP),
        t("z_kaluga_ru", "kaluga.ru", "kaluga.ru", "RU", ProbeType.TCP),
        t("z_66_ru", "66.ru", "66.ru", "RU", ProbeType.TCP),
        t("z_irkutsk_ru", "irkutsk.ru", "irkutsk.ru", "RU", ProbeType.TCP),
        t("z_izhevsk_ru", "izhevsk.ru", "izhevsk.ru", "RU", ProbeType.TCP),
        t("z_kirov_ru", "kirov.ru", "kirov.ru", "RU", ProbeType.TCP),
        t("z_khmao_ru", "khmao.ru", "khmao.ru", "RU", ProbeType.TCP),
        t("z_kalm_ru", "kalm.ru", "kalm.ru", "RU", ProbeType.TCP),
        t("z_komi_ru", "komi.ru", "komi.ru", "RU", ProbeType.TCP),
        t("z_kursk_ru", "kursk.ru", "kursk.ru", "RU", ProbeType.TCP),
        t("z_kurgan_ru", "kurgan.ru", "kurgan.ru", "RU", ProbeType.TCP),
        t("z_lipetsk_ru", "lipetsk.ru", "lipetsk.ru", "RU", ProbeType.TCP),
        t("z_lanta_net_ru", "lanta-net.ru", "lanta-net.ru", "RU", ProbeType.TCP),
        t("z_m24_ru", "m24.ru", "m24.ru", "RU", ProbeType.TCP),
        t("z_magadanmedia_ru", "magadanmedia.ru", "magadanmedia.ru", "RU", ProbeType.TCP),
        t("z_goodline_ru", "goodline.ru", "goodline.ru", "RU", ProbeType.TCP),
        t("z_kbr_ru", "kbr.ru", "kbr.ru", "RU", ProbeType.TCP),
        t("z_astrakhan_ru", "astrakhan.ru", "astrakhan.ru", "RU", ProbeType.TCP),
        t("z_ingushetia_ru", "ingushetia.ru", "ingushetia.ru", "RU", ProbeType.TCP),
        t("z_murmansk_ru", "murmansk.ru", "murmansk.ru", "RU", ProbeType.TCP),
        t("z_novgorod_ru", "novgorod.ru", "novgorod.ru", "RU", ProbeType.TCP),
        t("z_kam24_ru", "kam24.ru", "kam24.ru", "RU", ProbeType.TCP),
        t("z_nnov_ru", "nnov.ru", "nnov.ru", "RU", ProbeType.TCP),
        t("z_ngs_ru", "ngs.ru", "ngs.ru", "RU", ProbeType.TCP),
        t("z_nsk_ru", "nsk.ru", "nsk.ru", "RU", ProbeType.TCP),
        t("z_orenburg_ru", "orenburg.ru", "orenburg.ru", "RU", ProbeType.TCP),
        t("z_otr_online_ru", "otr-online.ru", "otr-online.ru", "RU", ProbeType.TCP),
        t("z_pskov_ru", "pskov.ru", "pskov.ru", "RU", ProbeType.TCP),
        t("z_primamedia_ru", "primamedia.ru", "primamedia.ru", "RU", ProbeType.TCP),
        t("z_pskovline_ru", "pskovline.ru", "pskovline.ru", "RU", ProbeType.TCP),
        t("z_pskovedu_ru", "pskovedu.ru", "pskovedu.ru", "RU", ProbeType.TCP),
        t("z_rostov_ru", "rostov.ru", "rostov.ru", "RU", ProbeType.TCP),
        t("z_sakhalin_info", "sakhalin.info", "sakhalin.info", "RU", ProbeType.TCP),
        t("z_sib_fm", "sib.fm", "sib.fm", "RU", ProbeType.TCP),
        t("z_sibnet_ru", "sibnet.ru", "sibnet.ru", "RU", ProbeType.TCP),
        t("z_smolensk_ru", "smolensk.ru", "smolensk.ru", "RU", ProbeType.TCP),
        t("z_tambov_ru", "tambov.ru", "tambov.ru", "RU", ProbeType.TCP),
        t("z_tomsk_ru", "tomsk.ru", "tomsk.ru", "RU", ProbeType.TCP),
        t("z_tatarstan_ru", "tatarstan.ru", "tatarstan.ru", "RU", ProbeType.TCP),
        t("z_tvtver_ru", "tvtver.ru", "tvtver.ru", "RU", ProbeType.TCP),
        t("z_ufanet_ru", "ufanet.ru", "ufanet.ru", "RU", ProbeType.TCP),
        t("z_tvernews_ru", "tvernews.ru", "tvernews.ru", "RU", ProbeType.TCP),
        t("z_tyumen_ru", "tyumen.ru", "tyumen.ru", "RU", ProbeType.TCP),
        t("z_vologda_ru", "vologda.ru", "vologda.ru", "RU", ProbeType.TCP),
        t("z_vladnews_ru", "vladnews.ru", "vladnews.ru", "RU", ProbeType.TCP),
        t("z_vostokmedia_com", "vostokmedia.com", "vostokmedia.com", "RU", ProbeType.TCP),
        t("z_vrn_ru", "vrn.ru", "vrn.ru", "RU", ProbeType.TCP),
        t("z_yamal_ru", "yamal.ru", "yamal.ru", "RU", ProbeType.TCP),
        t("z_yakutia_ru", "yakutia.ru", "yakutia.ru", "RU", ProbeType.TCP),
        // Расширенный набор менее популярных российских сайтов: именно они
        // помогают найти то, что отключается в БС у конкретного оператора.
        t("l_1c_ru", "1c.ru", "1c.ru", "RU", ProbeType.TCP),
        t("l_1prime_ru", "1prime.ru", "1prime.ru", "RU", ProbeType.TCP),
        t("l_5_tv_ru", "5-tv.ru", "5-tv.ru", "RU", ProbeType.TCP),
        t("l_78_ru", "78.ru", "78.ru", "RU", ProbeType.TCP),
        t("l_aif_ru", "aif.ru", "aif.ru", "RU", ProbeType.TCP),
        t("l_apteka_ru", "apteka.ru", "apteka.ru", "RU", ProbeType.TCP),
        t("l_argumenti_ru", "argumenti.ru", "argumenti.ru", "RU", ProbeType.TCP),
        t("l_auchan_ru", "auchan.ru", "auchan.ru", "RU", ProbeType.TCP),
        t("l_auto_ru", "auto.ru", "auto.ru", "RU", ProbeType.TCP),
        t("l_bankiros_ru", "bankiros.ru", "bankiros.ru", "RU", ProbeType.TCP),
        t("l_bcs_express_ru", "bcs-express.ru", "bcs-express.ru", "RU", ProbeType.TCP),
        t("l_beget_com", "beget.com", "beget.com", "RU", ProbeType.TCP),
        t("l_bfm_ru", "bfm.ru", "bfm.ru", "RU", ProbeType.TCP),
        t("l_buhgalteria_ru", "buhgalteria.ru", "buhgalteria.ru", "RU", ProbeType.TCP),
        t("l_business_gazeta_ru", "business-gazeta.ru", "business-gazeta.ru", "RU", ProbeType.TCP),
        t("l_chelny_izvest_ru", "chelny-izvest.ru", "chelny-izvest.ru", "RU", ProbeType.TCP),
        t("l_consultant_ru", "consultant.ru", "consultant.ru", "RU", ProbeType.TCP),
        t("l_dagpravda_ru", "dagpravda.ru", "dagpravda.ru", "RU", ProbeType.TCP),
        t("l_delivery_club_ru", "delivery-club.ru", "delivery-club.ru", "RU", ProbeType.TCP),
        t("l_detmir_ru", "detmir.ru", "detmir.ru", "RU", ProbeType.TCP),
        t("l_dodo_ru", "dodo.ru", "dodo.ru", "RU", ProbeType.TCP),
        t("l_dominos_ru", "dominos.ru", "dominos.ru", "RU", ProbeType.TCP),
        t("l_don24_ru", "don24.ru", "don24.ru", "RU", ProbeType.TCP),
        t("l_drom_ru", "drom.ru", "drom.ru", "RU", ProbeType.TCP),
        t("l_dzen_ru", "dzen.ru", "dzen.ru", "RU", ProbeType.TCP),
        t("l_e1_ru", "e1.ru", "e1.ru", "RU", ProbeType.TCP),
        t("l_eldorado_ru", "eldorado.ru", "eldorado.ru", "RU", ProbeType.TCP),
        t("l_etagi_com", "etagi.com", "etagi.com", "RU", ProbeType.TCP),
        t("l_fedpress_ru", "fedpress.ru", "fedpress.ru", "RU", ProbeType.TCP),
        t("l_finam_ru", "finam.ru", "finam.ru", "RU", ProbeType.TCP),
        t("l_finmarket_ru", "finmarket.ru", "finmarket.ru", "RU", ProbeType.TCP),
        t("l_fontanka_ru", "fontanka.ru", "fontanka.ru", "RU", ProbeType.TCP),
        t("l_forbes_ru", "forbes.ru", "forbes.ru", "RU", ProbeType.TCP),
        t("l_fssp_gov_ru", "fssp.gov.ru", "fssp.gov.ru", "RU", ProbeType.TCP),
        t("l_garant_ru", "garant.ru", "garant.ru", "RU", ProbeType.TCP),
        t("l_gibdd_ru", "gibdd.ru", "gibdd.ru", "RU", ProbeType.TCP),
        t("l_goldapple_ru", "goldapple.ru", "goldapple.ru", "RU", ProbeType.TCP),
        t("l_gorodrabot_ru", "gorodrabot.ru", "gorodrabot.ru", "RU", ProbeType.TCP),
        t("l_interfax_ru", "interfax.ru", "interfax.ru", "RU", ProbeType.TCP),
        t("l_ivi_ru", "ivi.ru", "ivi.ru", "RU", ProbeType.TCP),
        t("l_iz_ru", "iz.ru", "iz.ru", "RU", ProbeType.TCP),
        t("l_kazanfirst_ru", "kazanfirst.ru", "kazanfirst.ru", "RU", ProbeType.TCP),
        t("l_kinopoisk_ru", "kinopoisk.ru", "kinopoisk.ru", "RU", ProbeType.TCP),
        t("l_kion_ru", "kion.ru", "kion.ru", "RU", ProbeType.TCP),
        t("l_klerk_ru", "klerk.ru", "klerk.ru", "RU", ProbeType.TCP),
        t("l_kolesa_ru", "kolesa.ru", "kolesa.ru", "RU", ProbeType.TCP),
        t("l_kommersant_ru", "kommersant.ru", "kommersant.ru", "RU", ProbeType.TCP),
        t("l_kontur_ru", "kontur.ru", "kontur.ru", "RU", ProbeType.TCP),
        t("l_kuban24_tv", "kuban24.tv", "kuban24.tv", "RU", ProbeType.TCP),
        t("l_kuper_ru", "kuper.ru", "kuper.ru", "RU", ProbeType.TCP),
        t("l_lenta_com", "lenta.com", "lenta.com", "RU", ProbeType.TCP),
        t("l_letu_ru", "letu.ru", "letu.ru", "RU", ProbeType.TCP),
        t("l_life_ru", "life.ru", "life.ru", "RU", ProbeType.TCP),
        t("l_metro_cc_ru", "metro-cc.ru", "metro-cc.ru", "RU", ProbeType.TCP),
        t("l_mirkvartir_ru", "mirkvartir.ru", "mirkvartir.ru", "RU", ProbeType.TCP),
        t("l_mk_ru", "mk.ru", "mk.ru", "RU", ProbeType.TCP),
        t("l_more_tv", "more.tv", "more.tv", "RU", ProbeType.TCP),
        t("l_mosreg_ru", "mosreg.ru", "mosreg.ru", "RU", ProbeType.TCP),
        t("l_mskagency_ru", "mskagency.ru", "mskagency.ru", "RU", ProbeType.TCP),
        t("l_mvd_ru", "mvd.ru", "mvd.ru", "RU", ProbeType.TCP),
        t("l_nakanune_ru", "nakanune.ru", "nakanune.ru", "RU", ProbeType.TCP),
        t("l_nalog_ru", "nalog.ru", "nalog.ru", "RU", ProbeType.TCP),
        t("l_newizv_ru", "newizv.ru", "newizv.ru", "RU", ProbeType.TCP),
        t("l_ng_ru", "ng.ru", "ng.ru", "RU", ProbeType.TCP),
        t("l_nic_ru", "nic.ru", "nic.ru", "RU", ProbeType.TCP),
        t("l_nn_ru", "nn.ru", "nn.ru", "RU", ProbeType.TCP),
        t("l_oblgazeta_ru", "oblgazeta.ru", "oblgazeta.ru", "RU", ProbeType.TCP),
        t("l_ok_ru", "ok.ru", "ok.ru", "RU", ProbeType.TCP),
        t("l_okko_tv", "okko.tv", "okko.tv", "RU", ProbeType.TCP),
        t("l_onetwotrip_ru", "onetwotrip.ru", "onetwotrip.ru", "RU", ProbeType.TCP),
        t("l_perekrestok_ru", "perekrestok.ru", "perekrestok.ru", "RU", ProbeType.TCP),
        t("l_pfr_gov_ru", "pfr.gov.ru", "pfr.gov.ru", "RU", ProbeType.TCP),
        t("l_pobeda_aero", "pobeda.aero", "pobeda.aero", "RU", ProbeType.TCP),
        t("l_pravda_ru", "pravda.ru", "pravda.ru", "RU", ProbeType.TCP),
        t("l_premier_one", "premier.one", "premier.one", "RU", ProbeType.TCP),
        t("l_prime_ru", "prime.ru", "prime.ru", "RU", ProbeType.TCP),
        t("l_prm_ru", "prm.ru", "prm.ru", "RU", ProbeType.TCP),
        t("l_rabota_ru", "rabota.ru", "rabota.ru", "RU", ProbeType.TCP),
        t("l_re_store_ru", "re-store.ru", "re-store.ru", "RU", ProbeType.TCP),
        t("l_reg_ru", "reg.ru", "reg.ru", "RU", ProbeType.TCP),
        t("l_rg_ru", "rg.ru", "rg.ru", "RU", ProbeType.TCP),
        t("l_ridus_ru", "ridus.ru", "ridus.ru", "RU", ProbeType.TCP),
        t("l_rosbalt_ru", "rosbalt.ru", "rosbalt.ru", "RU", ProbeType.TCP),
        t("l_ruvds_com", "ruvds.com", "ruvds.com", "RU", ProbeType.TCP),
        t("l_sber_ru", "sber.ru", "sber.ru", "RU", ProbeType.TCP),
        t("l_sbis_ru", "sbis.ru", "sbis.ru", "RU", ProbeType.TCP),
        t("l_selectel_ru", "selectel.ru", "selectel.ru", "RU", ProbeType.TCP),
        t("l_sravni_ru", "sravni.ru", "sravni.ru", "RU", ProbeType.TCP),
        t("l_start_ru", "start.ru", "start.ru", "RU", ProbeType.TCP),
        t("l_sudrf_ru", "sudrf.ru", "sudrf.ru", "RU", ProbeType.TCP),
        t("l_synergy_ru", "synergy.ru", "synergy.ru", "RU", ProbeType.TCP),
        t("l_t2_ru", "t2.ru", "t2.ru", "RU", ProbeType.TCP),
        t("l_tass_ru", "tass.ru", "tass.ru", "RU", ProbeType.TCP),
        t("l_technopark_ru", "technopark.ru", "technopark.ru", "RU", ProbeType.TCP),
        t("l_timeweb_ru", "timeweb.ru", "timeweb.ru", "RU", ProbeType.TCP),
        t("l_tsargrad_tv", "tsargrad.tv", "tsargrad.tv", "RU", ProbeType.TCP),
        t("l_tutu_ru", "tutu.ru", "tutu.ru", "RU", ProbeType.TCP),
        t("l_ura_news", "ura.news", "ura.news", "RU", ProbeType.TCP),
        t("l_utair_ru", "utair.ru", "utair.ru", "RU", ProbeType.TCP),
        t("l_utkonos_ru", "utkonos.ru", "utkonos.ru", "RU", ProbeType.TCP),
    )

    fun build(base: List<Target>): List<LearningTarget> {
        val fromBase = base.map {
            LearningTarget(
                id = it.id,
                title = it.title,
                host = it.host,
                group = when (it.group) {
                    TargetGroup.WHITELIST -> "WL"
                    TargetGroup.RU_BLOCKED -> "RU"
                    TargetGroup.INTERNATIONAL -> "INT"
                },
                probe = it.probe,
            )
        }
        return (fromBase + (remote ?: extraRu)).distinctBy { it.host }
    }
}

object LearningReport {

    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)

    fun deviceInfo(context: Context, netInfo: NetworkInfo? = null): String {
        val telephony = context.getSystemService(TelephonyManager::class.java)
        // Оператор активной data-SIM, а не SIM по умолчанию: на dual-SIM это
        // разные вещи, из-за чего отчёт мог показывать не того оператора.
        val operator = runCatching {
            val subId = SubscriptionManager.getDefaultDataSubscriptionId()
            if (subId > 0) telephony?.createForSubscriptionId(subId)?.networkOperatorName else null
        }.getOrNull()?.ifBlank { null }
            ?: telephony?.networkOperatorName?.ifBlank { telephony.simOperatorName }?.ifBlank { null }
            ?: "—"
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val caps = connectivity?.activeNetwork?.let { connectivity.getNetworkCapabilities(it) }
        val networkType = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "mobile"
            else -> "—"
        }
        val extra = netInfo?.let {
            "; ip=${it.ip ?: "-"}; isp=${it.isp ?: "-"}"
        } ?: ""
        return "device=${Build.MANUFACTURER} ${Build.MODEL}; android=${Build.VERSION.RELEASE}; " +
            "sim=$operator; network=$networkType$extra"
    }

    fun build(
        context: Context,
        network: NetworkInfo?,
        verdict: Verdict,
        intlOk: Int, intlTotal: Int,
        ruBlockedOk: Int, ruBlockedTotal: Int,
        wlOk: Int, wlTotal: Int,
        ruAllOk: Int, ruAllTotal: Int,
        results: List<LearningResult>,
    ): String {
        val header = buildString {
            appendLine("CHEBURNET LEARNING v" + BuildConfig.VERSION_NAME)
            appendLine("time=${iso.format(Date())}")
            appendLine(deviceInfo(context, network))
            appendLine("verdict=$verdict")
            appendLine("scores: INT=$intlOk/$intlTotal RU=$ruBlockedOk/$ruBlockedTotal WL=$wlOk/$wlTotal")
            appendLine("ru_all=$ruAllOk/$ruAllTotal (все российские хосты обучения)")
            appendLine("--")
        }
        val body = results
            .sortedWith(compareBy({ it.target.group }, { it.target.host }))
            .joinToString("\n") { r ->
                val status = if (r.ok) "ok" else "fail"
                val g = when (r.target.group) {
                    "INT" -> "I"
                    "RU" -> "R"
                    else -> "W"
                }
                if (r.ok) "$g ${r.target.host}" else "$g ${r.target.host}!"
            }
        return header + body
    }
}
