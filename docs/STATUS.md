# Точка продолжения

Актуально 2026-10-08: подготовлена версия 0.3.0/code 3000, включающая исправление иконки, статьи о фазах и Home/Calendar polish. Release metadata, 7 release tests, checkKotlin/assembleDebug/lintDebug, workspace/diff и APK verification — PASS. Пользователь явно поручил push master, создание и push нового v0.3.0; отправка выполняется после контрольного коммита. Следующий шаг — проверить Release APK workflow и production APK для v0.3.0.

2026-10-07. Выполнен пакет доработок скриншотов, резервных копий, календаря, длительности прогноза и редактируемого дневника. Все локальные обязательные Android checks прошли. По новому явному запросу пользователя подготовлен выпуск 0.2.2/code 2002 для отправки master и нового тега v0.2.2. Существующие теги не меняются; публикацию APK должен подтвердить GitHub workflow.

## Результат

- FLAG_SECURE удалён по прямому запросу: системные скриншоты разрешены. Системный cloud backup остаётся отключён, сетевых permissions нет.
- Пароль экспорта необязателен, в форме нет ограничений длины. Пустой пароль создаёт открытый JSON envelope ritela.backup.plain; любой непустой включает прежний AES-256-GCM/PBKDF2. Импорт читает оба варианта и прежний payload v1, проверяет данные и сохраняет атомарный merge без перезаписи конфликтов. Новый payload v2 переносит структуру дневника, custom tags и иконки.
- Календарь: отмеченные месячные красные, прогноз светло-красный, предполагаемое фертильное окно светло-зелёное. Ожидаемые месячные занимают заданную длительность, а не расширяющийся диапазон возможного начала. Bounds сохранены в расчёте/подробностях: статистическая неопределённость не скрыта. Просмотр будущих дней работает, добавление будущих дат запрещено. Из sheet убраны «Нет записи» и пояснение про будущие даты.
- Вместо прежнего отсутствия оценки — предполагаемое фертильное окно и качественная подпись без персональных процентов и safe days. До точки отсчёта/при отсутствии прогноза овуляция не подтверждается. Семидневный шаблон — инженерная эвристика, источники и ограничения зафиксированы в PREDICTION/HEALTH_CONTENT; клинической валидации нет.
- Settings: длительность календарного прогноза default 7, изменение 1–60 дней. Новый forecastPeriodDuration отделён от старого periodDuration. Цикл default 28, медиана/MAD годового окна сохранены. Главная отдельно показывает среднюю длительность по завершённым записям; график боли за семь дней убран.
- Дневник: редактируемый заголовок, добавление/переименование/удаление разделов и тегов, карандаш у раздела, покачивание, красный минус над тегом, минимум один тег, удержание/перетаскивание и доступное действие «Раньше». Разделов может быть ноль, заметка остаётся. Иконки раздела и отдельного дня отображаются в календаре. Save/Cancel имени и иконки не меняет данные до подтверждения; структуру дневника сохраняют отдельные операции без закрытия формы дня. Удалён отдельный disclaimer у секс-тегов.
- Room v3: явные MIGRATION_1_2 + MIGRATION_2_3 сохраняют периоды и старые day_logs. Новые поля custom/calendarIcon и journal_layout с постоянными ID. Изменение интерфейсных тегов не удаляет исторические значения; custom tag не получает медицинскую семантику встроенного enum.
- Документация, карта, решения, privacy/backup/style и два существующих скилла обновлены; lock содержит новые версии/hash. Новые редакторские PNG зарегистрированы в preview-ui.ps1.

## Проверки

- scripts/gradle.ps1 formatKotlin — PASS.
- scripts/gradle.ps1 checkKotlin assembleDebug testDebugUnitTest lintDebug — BUILD SUCCESSFUL. 65 tests, 0 failures/errors; lint 0 errors/warnings, 9 informational hints.
- Автоматически: screenshots разрешены при сохранённом manifest privacy, defaults/reactive recalculation, семидневные fertile/bleeding границы всех 12 горизонтов, отделение preset/измеренных данных, ID/reorder/minimum tag, custom selections, plaintext и пароли 1/300 символов, старый payload, миграции v1/v2→v3, config перенос, CRUD/recreation, drag после удаления тега, Save/Cancel иконки и изменение длительности после перезапуска.
- Просмотрены настоящие PNG: календарь light/dark, редактор, форма без разделов, dark EN 320dp/200%, графики и отдельная средняя длительность. Галерея preview-ui.ps1 -SkipRender — PASS, 53 PNG.
- verify-apk.ps1 — PASS: signed app.ritela debug APK, API 26–37, no network permissions; 11 866 174 bytes, SHA256 7152be6b2c76129aeb4083d6f05dd832f77edd5c960c2bc393e5bfb01dfdfe23. Артефакт: app/build/outputs/apk/debug/app-debug.apk.
- check-workspace и check-health-content — PASS; quick_validate для двух изменённых скиллов — PASS; git diff --check — PASS.

## Следующий шаг / ограничения

Следующий шаг: проверить Android/Release APK workflow для v0.2.2, опубликованный production APK и обновление через Obtainium. Затем проверить APK на устройстве: системный скриншот, клавиатура и поля редактора, drag/TalkBack, SAF export/import обоих вариантов и переход с установленной базы v2. JVM PNG/тесты не заменяют device smoke test. Новый CI run и production APK для этих изменений не проверены; клинический review не проводился. Менять численные проценты риска по одному календарю нельзя считать подтверждённым расчётом.

Ручная копия не переносит язык, тему или preset длительности: настройки устройства сохраняются. Конфликт сохранённых layouts отменяет импорт вместе с остальными конфликтами; автоматического объединения несовпадающих структур нет. Пользовательские названия общие для EN/RU, стандартные подписи локализованы. Удалённые интерфейсные разделы скрывают значения, но не удаляют старые записи.

## Выпуски и Obtainium

Ранее опубликован Release v0.2.1 (draft=false, prerelease=false), APK uploaded; старый tag не менялся. jeezab/ritela — private. Obtainium требуется отдельный fine-grained token для этого repository с Contents Read-only; без авторизации GitHub release API возвращает 404. Секреты/ключи не добавлялись в Git, production подпись отдельно не проверялась. Подготовлен отдельный v0.2.2; публикация ещё не подтверждена.

## Выпуск 0.2.2

2026-10-07: git fetch origin --tags подтвердил отсутствие входящих коммитов (master опережал origin/master на 2). version.properties → 0.2.2, versionCode 2002. release.py check --tag v0.2.2 и 7 release tests — PASS; полный checkKotlin/assembleDebug/testDebugUnitTest/lintDebug повторён после смены версии — PASS, 65 tests без ошибок. verify-apk и preview-ui -SkipRender — PASS. Отправка master и нового тега явно разрешена пользователем; production secrets не читались.

## Новый пакет 2026-10-07 — завершён

Пользователь: более мягкий красный; короткие подписи фертильности и отдельный предполагаемый пик; стабильная карточка дня без конкуренции scroll/dismiss; countdown вместо «Мало данных»; теги без покачивания/«Раньше», минус справа сверху и рабочий drag; ritela.png как launcher icon. Не выполнять просмотр интерфейса до отдельного запроса; только необходимые тесты, небольшие коммиты по задачам. Предыдущие master и v0.2.2 отправлены и подтверждены на e06f844. Следующий шаг — календарные цвета и классификация предполагаемого пика.

### Завершено: мягкая палитра и предполагаемый пик

Месячные #D38A92 с тёмным текстом, предполагаемая овуляция nextStart−14 выделена #BBD39F. Короткие EN/RU подписи «ниже/выше/пик по прогнозу», длинное пояснение убрано, UNKNOWN сохранён без точки отсчёта. Format/checkKotlin/assembleDebug/lintDebug и CyclePredictionTest (12 tests) — PASS; check-health-content — PASS. Просмотр/генерация UI не выполнялись. Следующий шаг — стабильная карточка дня.

### Завершено: стабильная карточка дня

Карточка фиксированной высоты 90%, отдельная прокрутка содержимого, sheetGesturesEnabled=false и явный крестик в закреплённой шапке. Прокрутка не запускает сворачивание/перетягивание; Back, касание вне окна и кнопки закрытия сохранены. CheckKotlin/assembleDebug/lintDebug и один regression test с прокруткой до конца и повторными свайпами вверх — PASS. Без просмотра UI/PNG. Следующий шаг — countdown на главной.

### Завершено: отсчёт на главной

Плашка качества истории заменена «Примерно через N дней», «Ожидаются сегодня» или «Ожидались N дней назад». Счётчик использует state.today, EN/RU plurals; даты и диапазоны сохранены. Стартовая подпись кратко указывает 28-дневную основу без повторения нехватки данных. Format/checkKotlin/assembleDebug/lintDebug и один тест трёх вариантов — PASS. Без UI-просмотра. Следующий шаг — правка тегов и drag.

### Завершено: минус в углу и перетаскивание тегов

Покачивание и видимая кнопка «Раньше» удалены. Минус в правом верхнем углу, последний тег защищён. Drag использует координату пальца, зафиксированную до смещения, ближайшую цель с небольшим допуском и исключение удалённых ID; активный тег поверх соседних. TalkBack перестановка доступна через custom actions без дополнительных кнопок. Format/checkKotlin/assembleDebug/lintDebug и один тест long-press drag, повторного drag после удаления и Save/Cancel иконки — PASS. Без PNG/просмотра. Следующий шаг — launcher icon из ritela.png.

### Завершено: иконка из ritela.png

Пользовательский PNG 1254×1254 сохранён и скопирован без перекодирования, SHA-256 копии совпадает. Adaptive icon с безопасным отступом, фон #F7F3EF; manifest icon/roundIcon подключены. Повторяемая замена: scripts/update-launcher-icon.ps1. CheckKotlin/assembleDebug/lintDebug — PASS. Verify-apk — PASS: app.ritela, API 26–37, подпись debug, без network permissions; APK 13 431 488 байт, SHA-256 dc95c8c966fff587a156e17611b27df087847ecb0013409bec2cbceb82a98623. Aapt2 подтвердил application-icon. Workspace/skill hashes и diff checks — PASS. Первые проверки выявили лишний v26-каталог и отсутствие monochrome; каталог удалён, отдельная одноцветная маска не создавалась, конкретное исключение объяснено в XML/STYLE_GUIDE.

Новый пакет завершён: 12 domain-тестов и 3 целевых Compose-теста прошли раздельно, полный набор не запускался. PNG интерфейса не создавались и UI не просматривался. Следующий шаг — проверка пользователем на устройстве; push/новый тег только по отдельному запросу.

## Доработка 2026-10-07: swipe и небесный hero — завершена

Сворачивание карточки дня возвращено свайпом вниз по закреплённой шапке (64dp), порог 32dp. Прокрутка тела не двигает sheet. Format/checkKotlin/assembleDebug/lintDebug и один целевой тест: прокрутка до конца, повторные свайпы тела, закрытие свайпом шапки — PASS. UI не просматривался. Следующий шаг — компактный hero по новому приложенному промпту. Пользователь самостоятельно обновил иконку и версию до 0.2.3, исходный коммит c9aaf9e сохранён.

### Готова орбитальная сцена

CycleSceneState — чистая модель представления: ранняя часть до preset длительности, окно овуляции nextStart−19..−13, до него предполагаемая фолликулярная часть, после — лютеиновая. При недоступном прогнозе UNKNOWN; просроченный день не оборачивается в новый цикл. Формулы дат/диапазонов не менялись. CycleOrbitScene кеширует геометрию/кисти, рисует 6 точек, солнце, полумесяц и текущую точку. Дыхание 5.2 с, смещение/цвет фазы 900 мс, состояния анимации читаются в draw. OrbitMotion следит за animator scale и lifecycle, выключает движение при low-RAM. Format/checkKotlin/assembleDebug/lintDebug и 2 CycleSceneTest — PASS; интеграционные проверки отсчёта и крупного шрифта также прошли на рабочем дереве. Просмотр и FPS-замеры не проводились. Следующий шаг — сохранить подключение hero к Home.

### Завершено: подключение компактного hero

ForecastCard полностью заменён: surfaceContainerLow, строка дня/предполагаемой фазы, орбита 116dp/88dp, дата 36sp, короткий диапазон, отсчёт; база/число циклов доступны по info. Отдельный ForecastDurationInsight после CTA, капля и secondary-цвет основного действия. Прежний CycleOrbit удалён. EN/RU строки добавлены. Статичные Preview light/dark/200% созданы, существующие screenshot-проверки Home обновлены для CI. Промпт сохранён побайтно, CRLF разрешён для originals в gitattributes.

Format/checkKotlin/assembleDebug/lintDebug — PASS. 5 необходимых тестов: 1 swipe/scroll (отдельный прогон), 2 CycleSceneTest, 1 countdown, 1 narrow/large-font/info — PASS; итоговый совместный прогон последних четырёх без failures/errors. После удаления старого неиспользуемого composable повторены только build/check/lint. Verify-apk — PASS: debug app.ritela API 26–37, без сетевых permissions, 13 274 283 байта, SHA-256 0a468903f629c9346594d8568bf922d7e4325255ad4c9d27d6a0b85f1696748b. Workspace, оригинал промпта и diff checks — PASS.

Полный screenshot-набор, создание/просмотр PNG и эмулятор не запускались по запросу пользователя. Фактический FPS/TalkBack/ощущение свайпа на устройстве не подтверждены. Следующий шаг — проверка пользователем на устройстве; push и тег только по отдельному запросу.

## Выпуск v0.2.4

2026-10-07: пользователь явно запросил push и новый тег 0.2.4. Git fetch подтвердил master впереди origin/master на 3 коммита без входящих изменений; тег v0.2.4 отсутствует. GitHub API подтвердил опубликованные стабильные v0.2.1/v0.2.2/v0.2.3. version.properties обновлён до 0.2.4, code 2004. release.py check --tag v0.2.4 --release-pages — PASS; 7 release tests — PASS. CheckKotlin/assembleDebug/lintDebug после смены версии — PASS; verify-apk/workspace/diff — PASS. Новый debug APK: 12 968 048 байт, SHA-256 5322bd79ac983a6ba998a4b407e055770278ba64217117935bb135a779ae513e. UI и полный screenshot-набор не запускались. Предыдущие 5 целевых Android-тестов уже прошли; полный набор запустит CI. Следующий шаг — отправить master/новый v0.2.4, проверить remote SHA и Release APK run. Существующие теги не менять; production secrets локально не читаются.

## Исправление CI после v0.2.4

2026-10-07: Release APK упал на единственном englishForecastKeepsDatesAndHistoryLocalized (69 остальных тестов прошли по приложенному логу). Проверка ожидала Based on 6 completed cycles на главной после переноса этой подписи в info-dialog. Тест теперь проверяет отсутствие подписи на hero, нажимает forecast-info и проверяет английский текст в диалоге. Код приложения не менялся. CheckKotlin/compileDebugUnitTestKotlin — PASS; тесты и PNG не запускались по запросу пользователя, полный повтор остаётся CI. Следующий шаг — подготовить 0.2.5 и дать команды push/нового тега, самостоятельно не отправлять.

### Кандидат v0.2.5

version.properties → 0.2.5/code 2005. release.py check --tag v0.2.5 — PASS. Локального v0.2.5 нет; тег не создавался, push не выполнялся. Существующий v0.2.4 не переносить: его повторный запуск не содержит исправления. Следующий шаг пользователя: git push origin master; git tag v0.2.5; git push origin v0.2.5. Обновлён только тест, выполнение тестов/рендеры/полная сборка не повторялись.

## Переработка hero: гладкая смысловая орбита — завершена

CycleOrbitScene рисует один математически гладкий drawOval с наклоном −10°, без ломаной, деформации, дрейфующих узлов и лишней дуги. OrbitGeometry.pointOnOrbit использует параметрический эллипс и ту же rotation matrix. Пять упорядоченных вех: начало/солнце, фолликулярная планета, овуляционная жемчужина, лютеиновая планета, конец/полумесяц. Текущая точка — кольцо с контрастным центром; сохранён progress (day−1)/length с clamping, день 8/29 между фолликулярным и овуляционным маркером. EN/RU легенда в info объясняет символические вехи. Domain, прогноз, даты и диапазоны не изменены.

Три зоны карточки сохранены: компактный заголовок/info; сцена 156dp/124dp; label, дата 36sp, диапазон и countdown. Padding 24/20dp, rose mist surface. При >150% декор скрыт для читаемости. Дыхание 5.2s (0.93–1), текущая точка 4.2s (0.98–1.04); без вращения. Reduced motion/lifecycle/low-RAM оставляют статичную сцену. Геометрия/кисти кешируются, значения анимаций читаются при рисовании. После первой реализации выполнен отдельный polish: контраст moon/ovulation в dark и поверхность карточки; итоговые PNG повторно просмотрены.

Проверки:
- scripts/gradle.ps1 formatKotlin — PASS.
- scripts/gradle.ps1 checkKotlin assembleDebug lintDebug testDebugUnitTest с фильтрами OrbitGeometryTest, CycleSceneTest, HomeScreenTest.forecastHeroKeepsDataReadableWithLargeTextAndMovesBasisIntoDetails, HomeScreenTest.homeCountdownUsesTodayAndHandlesExpectedDatePassing, HomeScreenTest.renderPolishedOrbitHeroInLightDarkAndLargeText — BUILD SUCCESSFUL. 7 tests, 0 failures/errors; lint 0 errors/warnings, прежние 9 Hint.
- Просмотрены три настоящих синтетических PNG orbit-hero-light/dark/large-text до/после polish. Снимки зарегистрированы в preview-ui.ps1; существующий полный набор и галерея не запускались. Preview используют тестовые 8/29, 2 ноября, 30 октября — 5 ноября, 26 дней; рабочие данные не подменены.
- scripts/verify-apk.ps1 — PASS: debug app.ritela, API 26–37, без сетевых permissions; app/build/outputs/apk/debug/app-debug.apk, 13 294 000 bytes, SHA256 7e3ebefe00fe40d5daf366d9c7af2977432c002c131928316c8171589c5436fd.
- scripts/check-workspace.ps1 и git diff --check — PASS.

Следующий шаг: установить debug APK на устройстве и проверить анимацию при обычном и отключённом system animator scale, TalkBack и плавность. Эмулятор/device/FPS не проверялись; новый удалённый CI не запускался. Версия приложения не менялась, push/новый тег не выполнялись. Просмотр ограничен hero по новому явному запросу preview/валидации.

## Подготовка v0.2.6 — без push и тестов

2026-10-07: version.properties обновлён до 0.2.6, versionCode 2006. docs/RELEASES.md содержит команды для нового v0.2.6. Git fetch origin --tags подтвердил отсутствие входящих коммитов (master впереди origin/master на 1 до подготовки версии); v0.2.6 отсутствует локально и на origin. python scripts/release.py check --tag v0.2.6 — PASS: версия/code и Obtainium link. scripts/check-workspace.ps1 и git diff --check — PASS. Это проверки метаданных, не Android тесты.

По явному запросу пользователя тесты, Gradle, сборка, UI/PNG/эмулятор не запускались. Последние проверки hero описаны выше и относятся к версии 0.2.5. APK 0.2.6 локально не создан, production signing и текущие GitHub Releases отдельно не проверялись. Push, создание тега и публикация не выполнялись; существующие теги не менялись. Следующий шаг — пользователь выполняет git push origin master, git tag v0.2.6, git push origin v0.2.6 и проверяет Release APK workflow. CI сохраняет существующие проверки.

## Исправление launcher icon и выпуск 0.2.7

Удалены inset 19.4444% с каждой стороны: bitmap теперь заполняет foreground adaptive icon. Светлый background #F7F3EF заменён сливовым #4B2938, чтобы прозрачные углы исходника не давали белую рамку. ritela.png и launcher_art.png не менялись, их SHA-256 совпадает. Конечную форму/обрезку периферии задаёт launcher; реальный результат на телефоне ещё требует проверки пользователем. Экран приложения не менялся, UI PNG/эмулятор не запускались; просмотрен только исходник иконки в рамках текущего запроса.

version.properties → 0.2.7/code 2007, RELEASES команды обновлены. Git fetch origin --tags: master синхронизирован с origin/master до правки; новый v0.2.7 отсутствует локально и remote. FormatKotlin и checkKotlin/assembleDebug/lintDebug — PASS. Тесты не запускались по предыдущему запросу пользователя, CI сохраняет существующие проверки. release.py check --tag v0.2.7, check-workspace и diff checks — PASS. verify-apk — PASS: debug app.ritela API 26–37 без сетевых permissions; app/build/outputs/apk/debug/app-debug.apk, 12 968 708 bytes, SHA256 c0aa0dd5a41c43cadd6d4cd79e33a0d44b7d9ace2cb4e10147f074ef72c7d0a5. Aapt2 проверяет XML foreground без inset, сливовый background и versionName/code в собранном APK.

Пользователь явно разрешил git push origin master, git tag v0.2.7, git push origin v0.2.7. Команды выполняются после этого контрольного коммита; фактический результат отправки сообщается в ответе. Существующие теги/история не меняются. Следующий шаг — проверить Release APK workflow/production APK v0.2.7 и отсутствие белых краёв после обновления на телефоне. Production signing и device smoke test локально не проверялись.

## 2026-10-08: масштаб иконки

Foreground inset 16.6667% помещает исходный рисунок в центральные 72/108 adaptive icon вместо прежнего full-bleed. Фон #4B2938 сохраняется, исходные ritela.png/launcher_art.png не перекодируются. Маска лаунчера может слегка срезать углы. Локального просмотра PNG/интерфейса не было. Проверка workspace теперь корректно обрабатывает пустые Markdown-файлы (ошибка обнаружена на изначально пустом пользовательском style_brief.md).

Проверено в рабочем пакете: scripts/gradle.ps1 formatKotlin; checkKotlin assembleDebug lintDebug; scripts/verify-apk.ps1; scripts/check-workspace.ps1; git diff --check — PASS. Текущий debug APK app/build/outputs/apk/debug/app-debug.apk, API 26–37, без сетевых permissions. Результат на конкретном лаунчере требует проверки на телефоне. Версия остаётся 0.2.7, публикация/push не выполняются. Следующий шаг — статьи о цикле и фазах.
## 2026-10-08: статьи о цикле и фазах

Добавлены cycle, menstruation, follicular, ovulation, luteal — простые объяснения отсчёта цикла, месячных, созревания/выхода яйцеклетки и периода после овуляции. Фолликулярная фаза включает менструацию; календарь не подтверждает физиологическую фазу. Все тексты EN/RU доступны офлайн, ссылки открываются по явному действию. На главной образовательные статьи следуют после советов по явным симптомам, срочные советы сохраняют приоритет. Каталог вырос с 18 до 23 статей; дозы не менялись.

Свежие первичные источники и ограничения зафиксированы в HEALTH_CONTENT. scripts/check-health-content.ps1 — PASS: 23 статьи, 2 adult-dose entries. HelpArticlesTest — 2 tests PASS (разные EN/RU ресурсы, офлайн-текст, приоритет срочных советов). Format/checkKotlin/assembleDebug/lintDebug — PASS в составе рабочего пакета; lint 0 errors/warnings, 9 hints. Клинический review и визуальная проверка не проводились. Следующий шаг — сохранение UI-доработок.
## 2026-10-08: Home/Calendar по обновлённому UI brief

Исходный промпт сохранён без изменений в docs/prompts/2026-10-08-ui-polish.txt. Прочитаны обновлённый style_brief.md, orbit_spec.md и implementation_brief.md, изучены присланные в чате скриншоты. Пользовательские docs/ui не изменялись и остаются вне коммитов агента.

Главная: вторичная дата в header, интервалы 24dp, компактная сцена орбиты 132/112dp, плитки существующих разделов дневника и заметки, отдельная спокойная поверхность средней длительности, светлые карточки статей. Плитки открывают общую форму дня, не отдельный редактор выбранного поля. Общие RitelaColors/Typography/Shapes/Motion и существующий Spacing собраны в Theme.kt. Сохранены SansSerif, пять орбитальных вех, маркер текущего дня и reduced-motion; геометрия, фазы-прогнозы и расчёты не менялись.

Обновлённый style_brief уточняет один месяц в основном календаре: добавлен переключатель «Лента месяцев»/«Показать один месяц» с сохранением месяца. История записей отдельно; стрелки, выбор года/месяца и «Сегодня» работают в обоих режимах. Фертильное окно кремово-персиковое, пик насыщеннее; EN/RU легенда обновлена. Prepared Preview Home/Calendar: 360dp, 320dp EN, 412dp dark, 200% RU. Preview не запускались. Существующий CI-тест свободной прокрутки теперь явно включает ленту.

Проверки итогового рабочего пакета:
- scripts/gradle.ps1 formatKotlin — PASS.
- scripts/gradle.ps1 checkKotlin assembleDebug lintDebug testDebugUnitTest с фильтрами HelpArticlesTest, CyclePredictionTest, OrbitGeometryTest, HomeScreenTest.quickJournalActionOpensTheEditableDay, HomeScreenTest.forecastHeroKeepsDataReadableWithLargeTextAndMovesBasisIntoDetails, HomeScreenTest.calendarSwitchesBetweenSingleMonthAndStreamAtTheChosenMonth — BUILD SUCCESSFUL. 19 tests (2+12+2+3), 0 failures/errors. Lint: 0 errors, 0 warnings, 9 прежних hints.
- Первые прогоны выявили гонку загрузки в новом тесте плитки, неоднозначный месяц при прокрутке к дню и неиспользуемый today_empty. Исправлены ожидание загрузки, точное позиционирование тестовой ленты и удалены неиспользуемые EN/RU ресурсы; проверки не отключались.
- scripts/check-health-content.ps1 — PASS: 23 EN/RU статьи, 2 adult-dose entries.
- scripts/verify-apk.ps1 — PASS: signed debug app.ritela, API 26–37, no network permissions. app/build/outputs/apk/debug/app-debug.apk; 13 305 676 bytes; SHA256 1a8afe33b65ffe483d1a3acd809779308b14643fbe2f0659b81b682af78a1071.
- scripts/check-workspace.ps1 и git diff --check — PASS.

Ограничения: локального просмотра/генерации PNG, галереи и эмулятора не было согласно действующему предпочтению пользователя. Полный screenshot-набор не запускался. Отдельный проход по коду проверил адаптивность/состояния и согласованность tokens, но visual loop из промпта не выполнен; сравнение с растровыми docs/ui и устройство/TalkBack/FPS не подтверждены. Первичные источники прочитаны, клинический review не выполнен. Room/схема/данные не менялись. Версия 0.2.7 сохранена; push, тег, удалённый CI и публикация не выполнялись. Следующий шаг — установка APK и проверка иконки, плотности главной и режимов календаря на телефоне.
## 2026-10-08: выпуск 0.3.0

version.properties → 0.3.0/code 3000; RELEASES содержит команды для нового v0.3.0. Git fetch origin --tags: входящих коммитов нет, master впереди origin/master на три завершённых шага; v0.3.0 отсутствует локально и remote перед подготовкой. Пользователь прямо разрешил отправку ветки, создание и отправку тега. Старые теги не меняются, docs/ui остаются пользовательскими untracked файлами и не добавляются в коммит.

Проверки: python scripts/release.py check --tag v0.3.0 — PASS; python -m unittest discover -s scripts -p test_release.py — 7 tests PASS; scripts/gradle.ps1 checkKotlin assembleDebug lintDebug — BUILD SUCCESSFUL. Код приложения после предыдущих 19 успешных тестов не менялся, эти тесты повторно не запускались. scripts/verify-apk.ps1 — PASS: debug app.ritela, API 26–37, без сетевых permissions; aapt2 подтверждает versionName 0.3.0/code 3000. APK app/build/outputs/apk/debug/app-debug.apk, 12 991 840 bytes, SHA256 7402f6b3158add792f013887cfb94b14c5fef43d619f65ea9ff2f2d524658d4a. scripts/check-workspace.ps1 и git diff --check — PASS. UI/PNG/эмулятор не запускались. Production signing и публикацию должен подтвердить Release APK workflow после push тега; локальная debug-сборка этого не подтверждает. Следующий шаг — проверить workflow и обновление через Obtainium.