package ru.cheburnet

data class City(val title: String, val lat: Double, val lon: Double)

data class TargetLists(
    val international: List<Target>,
    val ruBlocked: List<Target>,
    val whitelist: List<Target>,
    val learning: List<String> = emptyList(),
)

object TargetCatalog {

    val defaults: TargetLists = TargetLists(
        international = listOf(
            // Северная Америка
            Target("google", "Google DNS", "8.8.8.8", "USA", TargetGroup.INTERNATIONAL),
            Target("cloudflare", "Cloudflare", "1.1.1.1", "USA", TargetGroup.INTERNATIONAL),
            Target("opendns", "OpenDNS", "208.67.222.222", "USA", TargetGroup.INTERNATIONAL),
            // Европа
            Target("quad9", "Quad9", "9.9.9.9", "CHE", TargetGroup.INTERNATIONAL),
            Target("quad9b", "Quad9 (2)", "149.112.112.112", "CHE", TargetGroup.INTERNATIONAL),
            Target("adguard", "AdGuard", "94.140.14.14", "CYP", TargetGroup.INTERNATIONAL),
            // Азия
            Target("alidns", "AliDNS", "223.5.5.5", "CHN", TargetGroup.INTERNATIONAL),
            Target("baidu", "Baidu", "180.76.76.76", "CHN", TargetGroup.INTERNATIONAL),
            Target("kt", "KT (Корея)", "168.126.63.1", "KOR", TargetGroup.INTERNATIONAL),
            // Южная Америка
            Target("google3", "Google 3", "1.0.0.1", "USA", TargetGroup.INTERNATIONAL),
            // Африка
            Target("za1", "ZA DNS", "196.11.240.215", "ZAF", TargetGroup.INTERNATIONAL),
            Target("za2", "ZA DNS 2", "102.128.0.1", "ZAF", TargetGroup.INTERNATIONAL),
            // Океания
            Target("telstra", "Telstra", "139.130.4.5", "AUS", TargetGroup.INTERNATIONAL),
            Target("au2", "AU DNS", "203.2.193.124", "AUS", TargetGroup.INTERNATIONAL),
            Target("google2", "Google DNS 2", "8.8.4.4", "USA", TargetGroup.INTERNATIONAL),
            Target("adguard2", "AdGuard 2", "94.140.15.15", "CYP", TargetGroup.INTERNATIONAL),
            Target("kt2", "KT (Корея) 2", "168.126.63.2", "KOR", TargetGroup.INTERNATIONAL),
            Target("au3", "AU DNS 2", "61.9.194.49", "AUS", TargetGroup.INTERNATIONAL),
        ),
        // Российские сервисы, которые отключаются в режиме БС. Только те,
        // чей HTTPS отвечает в обычном режиме: банки с антибот-защитой
        // (РСХБ, Совкомбанк, МКБ и т.п.) режут не-браузерный TLS и дают
        // ложные «недоступен», поэтому они не используются как маркеры.
        ruBlocked = listOf(
            Target("raiffeisen", "Райффайзен", "raiffeisen.ru", "RUS", TargetGroup.RU_BLOCKED, ProbeType.TCP),
            Target("citilink", "Ситилинк", "citilink.ru", "RUS", TargetGroup.RU_BLOCKED, ProbeType.TCP),
            Target("domclick", "Домклик", "domclick.ru", "RUS", TargetGroup.RU_BLOCKED, ProbeType.TCP),
            Target("habr", "Хабр", "habr.com", "RUS", TargetGroup.RU_BLOCKED, ProbeType.TCP),
            Target("vc", "vc.ru", "vc.ru", "RUS", TargetGroup.RU_BLOCKED, ProbeType.TCP),
            Target("magnit", "Магнит", "magnit.ru", "RUS", TargetGroup.RU_BLOCKED, ProbeType.TCP),
            Target("forbes", "Форбс", "forbes.ru", "RUS", TargetGroup.RU_BLOCKED, ProbeType.TCP),
            Target("interfax", "Интерфакс", "interfax.ru", "RUS", TargetGroup.RU_BLOCKED, ProbeType.TCP),
            Target("fontanka", "Фонтанка", "fontanka.ru", "RUS", TargetGroup.RU_BLOCKED, ProbeType.TCP),
            Target("e1", "E1.ru", "e1.ru", "RUS", TargetGroup.RU_BLOCKED, ProbeType.TCP),
        ),
        // Официальный белый список: работает и в БС-режиме.
        whitelist = listOf(
            Target("yandex", "Яндекс", "ya.ru", "RUS", TargetGroup.WHITELIST, ProbeType.TCP),
            Target("vk", "VK", "vk.com", "RUS", TargetGroup.WHITELIST, ProbeType.TCP),
            Target("mailru", "Mail.ru", "mail.ru", "RUS", TargetGroup.WHITELIST, ProbeType.TCP),
            Target("gosuslugi", "Госуслуги", "gosuslugi.ru", "RUS", TargetGroup.WHITELIST, ProbeType.TCP),
            Target("ozon", "Ozon", "ozon.ru", "RUS", TargetGroup.WHITELIST, ProbeType.TCP),
            Target("wildberries", "Wildberries", "wildberries.ru", "RUS", TargetGroup.WHITELIST, ProbeType.TCP),
            Target("gazprom", "Газпромбанк", "gazprombank.ru", "RUS", TargetGroup.WHITELIST, ProbeType.TCP),
            Target("2gis", "2ГИС", "2gis.ru", "RUS", TargetGroup.WHITELIST, ProbeType.TCP),
            Target("rutube", "Rutube", "rutube.ru", "RUS", TargetGroup.WHITELIST, ProbeType.TCP),
            Target("vkvideo", "VK Видео", "vkvideo.ru", "RUS", TargetGroup.WHITELIST, ProbeType.TCP),
        ),
    )

    @Volatile
    private var current: TargetLists = defaults

    val international: List<Target> get() = current.international
    val ruBlocked: List<Target> get() = current.ruBlocked
    val whitelist: List<Target> get() = current.whitelist
    val all: List<Target> get() = current.international + current.ruBlocked + current.whitelist

    fun apply(lists: TargetLists) {
        current = lists
        LearningCatalog.remote = lists.learning
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .map { host ->
                val slug = "l_" + host.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
                LearningTarget(slug, host, host, "RU", ProbeType.TCP)
            }
            .ifEmpty { null }
    }

    val cheburnetCities: List<City> = listOf(
        City("Москва", 55.75, 37.62),
        City("Санкт-Петербург", 59.94, 30.31),
        City("Екатеринбург", 56.84, 60.61),
        City("Новосибирск", 55.03, 82.92),
        City("Владивосток", 43.12, 131.89),
    )
}
