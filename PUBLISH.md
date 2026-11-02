# Публикация «Чебурнет» в Google Play

Пошаговая инструкция под этот проект. Всё, что можно было подготовить заранее, уже
лежит рядом: подписанный AAB, иконка, feature graphic, скриншоты, тексты и
политика конфиденциальности.

## Что готово

| Файл | Куда |
| --- | --- |
| `dist/Cheburnet-1.43-release.aab` | загрузить в Play Console (Production / Testing) |
| `dist/Cheburnet-1.43-release.apk` | для установки вручную, в Play не нужен |
| `dist/Cheburnet-1.29-mapping.txt` | файл деобфускации, загрузить вместе с AAB |
| `upload-keystore.jks` + `keystore.properties` | **бэкап!** ключ подписи, пароль внутри |
| `store/icon-512.png` | иконка приложения (512×512) |
| `store/feature-1024x500.png` | feature graphic |
| `store/screenshots/*.png` | 6 скриншотов (1080×2340) |
| `store/listing-ru.txt`, `store/listing-en.txt` | название, краткое и полное описание |
| `store/privacy-policy.html` (+ `.md`) | политика конфиденциальности для хостинга |

Технически приложение соответствует требованиям 2026 года: `targetSdk 36`
(Android 16), AAB, подпись release-ключом, `minSdk 24`, `versionCode 28`,
`versionName 1.29`. Разрешения: только `INTERNET` и `ACCESS_NETWORK_STATE` —
геолокация из приложения удалена.

## Шаг 1. Аккаунт разработчика

1. Открыть <https://play.google.com/console/signup>.
2. Выбрать тип аккаунта:
   - **Personal** — проще, но для аккаунтов, созданных после 13.11.2023,
     обязателен закрытый тест: **12 тестеров непрерывно 14 дней** до выхода в
     Production.
   - **Organization** — нужен D-U-N-S номер, зато нет требования 12 тестеров.
3. Оплатить разовую регистрацию (около $25) и пройти верификацию личности
   (паспорт/ID) и адреса. Обычно занимает 1–3 дня.

## Шаг 2. Выложить политику конфиденциальности

Play требует публичный URL. Самый простой бесплатный вариант — GitHub Pages:

1. Политика уже лежит в репозитории: `github.com/3dbaxtep/cheburnet`,
   файл `privacy-policy.html`. Осталось включить Pages:
   Settings → Pages → Deploy from a branch → `main` → `/ (root)` → Save.
2. Settings → Pages → Branch `main`, папка `/` (или `/docs`).
3. Ссылка будет `https://3dbaxtep.github.io/cheburnet/privacy-policy.html` —
   её указать в Play Console.
4. Перед публикацией вписать свой email в файл политики (сейчас там
   «укажите свой email»).

Альтернативы: Notion (публичная страница), Telegraph, свой домен.

## Шаг 3. Создать приложение

Play Console → **Create app**:

- App name: `Чебурнет — проверка связи`
- Default language: русский
- App or game: **App**
- Free or paid: **Free**
- Declarations: подтвердить политики Play.

`ru.cheburnet` — окончательный package name, поменять после публикации нельзя.

## Шаг 4. Store listing

Заполнить из `store/listing-ru.txt`:

- Название (≤30), краткое описание (≤80), полное (≤4000).
- Иконка `store/icon-512.png`, feature graphic `store/feature-1024x500.png`.
- Скриншоты: 2–8 штук из `store/screenshots/`.
- Категория: **Tools** (Инструменты).
- Контактный email и сайт (можно GitHub Pages).
- Язык списка: русский; при желании добавить английский из `listing-en.txt`.

## Шаг 5. App content (обязательные разделы)

1. **Privacy policy** — вставить URL из шага 2.
2. **Ads** — «No, my app does not contain ads».
3. **App access** — «All functionality is available without special access»
   (аккаунтов и логина нет).
4. **Content rating** — пройти опросник: категория «Utility / Communication»,
   без насилия, контента 18+, азартных игр и т.д. Ожидаемый рейтинг: Everyone / 3+.
5. **Target audience** — 13+ (не выбирать «до 13», иначе включается Families
   policy и дополнительные требования).
6. **Data safety** — самый важный раздел:
   - Геолокация из приложения удалена: остаётся только город по IP, который
     никуда не сохраняется. Location в Data safety отмечать не нужно.
   - «Does your app collect or share any of the required user data types?» → **No**.
   - В политике уже описано, что IP-адрес уходит сайтам при пинге и сервису
     геолокации при определении города — это не сбор и не хранение данных
     разработчиком.
   - Если ревьюер попросит уточнить: местоположение по IP обрабатывается
     эфемерно (только на экране), не сохраняется и не передаётся.
7. **Government apps** — No. **Financial features** — No. **Health** — No.
   **News app** — No.
8. **Data deletion** — не применимо (аккаунтов нет).

## Шаг 6. Загрузить AAB и включить Play App Signing

1. Testing → **Internal testing** → Create release.
2. Загрузить `dist/Cheburnet-1.43-release.aab`.
3. Согласиться на **Play App Signing** (Google хранит ключ подписи приложения,
   ваш `upload-keystore.jks` остаётся ключом загрузки).
4. Добавить себя в список тестеров, установить по ссылке, проверить.
5. Внутренний тест обычно проверяют за несколько часов.

## Шаг 7. Закрытый тест (для personal-аккаунта)

1. Testing → **Closed testing** → создать релиз с тем же AAB.
2. Добавить минимум **12 тестеров** (email-список или Google Group).
3. Они должны быть подписаны на тест **14 дней подряд**, не выходя.
4. Через 14 дней появится кнопка **Apply for production**.

Для organization-аккаунта этот шаг не обязателен — можно сразу в Production
(но внутренний тест всё равно полезен).

## Шаг 8. Production

1. Production → Create release → загрузить AAB (можно тот же).
2. Rollout: начать с 20%, через день-два — 100%.
3. Review обычно 1–7 дней (первый релиз — дольше).
4. После одобрения приложение появится в Play по ссылке
   `https://play.google.com/store/apps/details?id=ru.cheburnet`.

## Обновления

```bash
cd Cheburnet
export JAVA_HOME=/path/to/jdk17
./gradlew bundleRelease
# dist: app/build/outputs/bundle/release/app-release.aab
```

Перед каждой загрузкой:

- поднять `versionCode` (сейчас 12) и `versionName` в `app/build.gradle.kts`;
- проверить `targetSdk` — Play требует актуальный уровень API для новых
  загрузок;
- прогнать `./gradlew testDebugUnitTest lintDebug`;
- загрузить новый AAB в тот же трек.

## Важные риски

1. **Название.** «Чебурнет» — сатирическое, но политически заряженное.
   Play обычно пропускает сатиру, однако возможны вопросы по политике
   «sensitive events». Если ревью отклонит — быстро переименовать в нейтральное
   (`NetCheck: проверка связи`), сохранив package name и функциональность.
2. **IP-геолокация.** Формально данные не собираются, но сторонний запрос есть;
   поэтому политика конфиденциальности должна быть опубликована и доступна.
3. **Ключ подписи.** `upload-keystore.jks` и пароль из `keystore.properties`
   потерять нельзя — иначе не получится выпускать обновления (придётся
   запрашивать сброс upload-ключа в поддержке Play). Сделайте бэкап в двух местах.
4. **Аккаунт.** Соблюдайте правила Play для developer-аккаунта: один аккаунт,
   реальные данные при верификации; нарушения ведут к блокировке.

## Мини-чеклист

- [ ] Аккаунт разработчика создан и верифицирован
- [ ] Политика конфиденциальности опубликована, email вписан
- [ ] Приложение создано в Play Console
- [ ] Store listing заполнен, иконка и feature graphic загружены
- [ ] Content rating пройден
- [ ] Data safety заполнен (No collection)
- [ ] AAB загружен в Internal testing, проверен на телефоне
- [ ] (Personal) 12 тестеров × 14 дней
- [ ] Production rollout запущен
