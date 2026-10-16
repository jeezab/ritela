# Проверки

Delayed baseline test пересоздаёт обе файловые БД/runtime после 10 минут,
проверяет renewal без нового согласия, затем истечение всех routes/repair через
мастер и продолжение прежнего автонабора. Новые immutable packet IDs/sequence
заменяют отменённый draft; повторное согласие в чате не появляется.

Упрощение Sync: RelayRuntimeTest проверяет wizard invitation/response/confirmation
без дополнительных setup files, отсутствие plaintext capability в приглашении,
идемпотентность повтора, DATA с настоящей PeriodEntity, foreground delivery при
переключении профилей, сохранение бюджета при пересоздании controller, upload при
исчерпанном fetch budget. Automatic flow проверяет обязательный recipient approval,
baseline, изменение/DELETE в чат, отсутствие невыбранных полей, отказ расширенному
scope и pause. ForegroundSyncBudgetTest — burst/refill/hour/clock rollback.
Отдельные сценарии отвергают malformed control до сохранения в чат и проверяют
ручные pairing/DATA без HTTP после активации пилота при отключённой сети.
Добавлены CyclePredictionTest и DAO/ViewModel recalculation tests: алгоритмы не
менялись, выбранные прогнозы подготавливаются прежним owner provider.

Этап 8: [матрица надёжности и допуска к пилоту](architecture/RITELA_SYNC_STAGE_8.md)
связывает recovery/replay/revoke tests, bounded ciphertext/control corpus,
burst/cursor/D1 budgets и условия физического испытания. Relay CI проверяет local
workerd/D1; release проверяет relay до signing. Общий APK verifier требует network
permissions и cleartext=false; release.py также проверяет certificate/version/debug.
Это не доказывает production E2E, billed CPU или физическое install/update.

2026-10-16, Android relay runtime: `RelayRuntimeTest` использует две файловые Room
БД, настоящие JCA подписи/HPKE и synthetic relay exchange без DNS/HTTP. Проверяет
полный DATA/Doodle/ACK/OPENED roundtrip, неизменяемый retry после потерянного PUT
и reopen, Retry-After, opt-out/current-profile gate, atomic page rollback,
отмену in-flight, peer revoke/delete и оба порядка manual/HPKE replay. Очередь
40 сообщений проверяет отсутствие starvation и продолжение full page; отдельный
сценарий — byte-limited short page и 32 задания без маршрута перед доступным peer.
Потеря storage ACK после apply и оба wire hashes после clear также проверяются.
Отдельный runtime test готовит record baseline настоящим queue encoder, явно
подтверждает author grant, регистрирует server fence и baseline reservation,
проверяет отдельную received projection и обратную E2E ACK без ложной доставки.
Продолжение DELTA после истечения baseline reservation и несовпадение saved/build
origin также покрыты: прежнее согласие не разрешает обращения к другому relay.
`SyncWorkerTest` через TestDriver проверяет CONNECTED, unique work, cancel и
неактивный профиль без открытия его БД и durable immediate continuation.
SyncSettingsUiTest проверяет отмену consent без key creation/HTTP и регистрацию
только после подтверждения; используется общий PartnerModal без UI captures.
Home privacy assertion открывает Settings, где действительно находится текст;
normal permission проверяется по manifest, а не по эмуляции grants в Robolectric.
`SyncMigrationTest` расширен на все
1…10→11, включая заполненную sync_message_intents v10, rollback из 6…10 и
fresh/migrated DDL пяти relay-таблиц. Compose проверяется по semantics без PNG.
Артефакты и точный результат текущего запуска — STATUS; ниже история проверок.

Локальная часть этапа 7: `SyncSettingsTest`, `SyncSettingsUiTest` — consent defaults без seed, metadata-only composition, atomic disable/rollback/reopen, отдельные cancel/pause с сохранением STORED/исходных сообщений, peer/profile isolation, статусы без ложной доставки; EN/RU и 320 dp/fontScale 2.0 по semantics/scroll, без captureToImage/PNG. Неработающий internet enable явно disabled; проверка runtime opt-in/relay deletion ждёт этапы 5–6. Новый UI визуально не просматривается до запроса пользователя.

Этап 6 (первая локальная часть, Room 10): `SyncMessageQueueTest` — confirmed send/no preview/no opt-out backfill, transactional rollback, reopen/ID stability, revoke/epoch/grants/device/global off, legacy large Doodle manual exchange, E2E ACK/replay/late ACK и delayed OPENED. `SyncMigrationTest` проверяет все 1…9→10, populated v8/v9 queue/inbox/projection и BLOB bytes, fresh/migrated DDL/index, DDL rollback/retry из 6/7/8/9. Runtime HTTP/WorkManager ещё отсутствуют; эти тесты не доказывают интернет-доставку. UI/PNG/эмулятор не запускаются.

Этап 3 Sync: `SyncReceiveTest` — real synthetic JCA envelopes/двустороннее negotiation и author approval, части baseline/restart/missing/duplicate/collision/atomic rollback, gaps/DELETE/epoch/v1 precedence/clear, successor fencing/retirement/restore, два устройства/ACK binding и embedded Doodle receivedAt/late OPENED. `SyncQueueTest` дополнен 260-record multipart baseline/delta и ошибкой второй части. `SyncMigrationTest` теперь проверяет 1…8→9, сохранение старых outbox/journal/projection, все sync DDL fresh/migrated и фактический rollback/retry из 6/7/8; compatibility tests используют Room 9. Полный выбранный набор — Sync*Test, Partner*Test, UserProfilesTest, DayLogBackupTest, PeriodRepositoryTest, PeriodViewModelTest, JournalChangesTest, JournalLayoutTest; `-Pritela.skipScreenshots=true --max-workers=1 --no-parallel`, JVM Xmx1536m. Вместе с format/checkKotlin/assembleDebug/lintDebug/schema review/APK/workspace/diff. Точные итоговые результаты — STATUS. Физический Bluetooth, UI/PNG/эмулятор, HPKE/relay/HTTP не подтверждены этим прогоном.

Этап 2 Sync: `SyncEngineTest`, `SyncJournalTest`, `SyncQueueTest`; migration helpers обновлены для Room 8. Покрыты DAO/import atomicity и rollback, no-op/bulk/delete, ограниченная opt-out history, snapshot при конкурентной записи, coalescing, scope/tag/label/date filtering, ошибки encoder и queue limits, immutable retry/restart/lease/backoff, stale executor, revoke/reenable/epoch, compaction/tombstones, изоляция двух очередей и обязательный forecast provider. Транспорт/encoder fake, без сети. В migration failure tests для источников 6 и 7 отдельно проверяется факт вызова DDL до искусственной ошибки, а не просто отсутствие пути миграции.

Для регрессии этапа 2 использовать предыдущий целевой Sync/Partner/Profile/Backup набор и дополнительно фильтры `app.ritela.PeriodRepositoryTest`, `app.ritela.PeriodViewModelTest`, `app.ritela.JournalChangesTest`, `app.ritela.JournalLayoutTest`. Вместе с format/checkKotlin/assembleDebug/lintDebug, `-Pritela.skipScreenshots=true`, workspace/diff и APK inspection. Точные итоговые counts — STATUS; UI/PNG/эмулятор не запускаются. Старые migration/compatibility assertions сохраняются, версия destination теперь 8.

Этап 1 Sync (Room 7): `SyncModelsTest`, `SyncStateStoreTest`, `SyncMigrationTest`, `SyncCompatibilityTest`. Проверяют overflow/consent/author invariants и отдельный internet Doodle budget; fail-closed metadata, атомарные defaults, все пути Room 1…6→7, fresh/migrated schema, rollback/retry, профильную изоляцию, старые encrypted packets/ACK/OPENED/tombstones и сохранность файлов vault/DataStore/15-item doodle library. `SyncTestDatabases.kt` создаёт file-backed legacy fixtures из сохранённых schema JSON. Библиотека рисунков для compatibility test предварительно записана в существующем v1 формате и перечитана до upgrade; тест не полагается на особенности повторного AtomicFile replace на Windows-хосте.

Целевой прогон: `& ./scripts/gradle.ps1 testDebugUnitTest '--tests' 'app.ritela.Sync*Test' '--tests' 'app.ritela.UserProfilesTest' '--tests' 'app.ritela.DayLogBackupTest' '--tests' 'app.ritela.PartnerTest' '--tests' 'app.ritela.PartnerVaultTest' '--tests' 'app.ritela.PartnerPairingTest' '--tests' 'app.ritela.PartnerInvitationsTest' '--tests' 'app.ritela.PartnerReliabilityTest' '--tests' 'app.ritela.PartnerShareSelectionTest' '--tests' 'app.ritela.PartnerMessageDeletionTest' '-Pritela.skipScreenshots=true'`. Вместе с checkKotlin/assembleDebug/lintDebug и verify-apk это storage milestone; UI/PNG/эмулятор не требуются. JVM-тесты не подтверждают физическое Bluetooth-соединение и будущий distributed author fencing. Сеть/Cloudflare на этом этапе отсутствуют.

Если пользователь просит проверить взаимодействия без генерации PNG, передай Gradle `'-Pritela.skipScreenshots=true'` в PowerShell (в кавычках). HomeScreenTest пропускает только сохранение изображений, все assertions остаются; по умолчанию false, поэтому CI продолжает генерировать прежние артефакты. Новые DaySelectionUiTest/PeriodEntryUiTest не сохраняют PNG. Не использовать флаг для заявленной визуальной проверки.

Windows, из корня проекта:

```powershell
& ./scripts/gradle.ps1 formatKotlin
& ./scripts/gradle.ps1 checkKotlin assembleDebug testDebugUnitTest lintDebug
powershell -NoProfile -File scripts/check-workspace.ps1
powershell -NoProfile -File scripts/verify-apk.ps1
git diff --check
```

Скрипт использует локальные Java/SDK и восстанавливает переменные окружения. При первом запуске:

```powershell
powershell -NoProfile -File scripts/bootstrap-toolchain.ps1
powershell -NoProfile -File scripts/setup-sdk.ps1 -AcceptLicenses
```

Linux/macOS: подготовь JDK 17 и Android SDK, установи `platforms;android-37.0`, `build-tools;36.0.0`, `platform-tools`, задай ANDROID_HOME и запусти:

```sh
./gradlew checkKotlin assembleDebug testDebugUnitTest lintDebug --console=plain
```

Тесты запускают Activity/Compose/SQLite в Robolectric на API 35. `HomeScreenTest` проверяет FLAG_SECURE/backup/INTERNET, add → Activity recreation → finish, редактирование с восстановлением формы и удаление с отменой/подтверждением. Календарные сценарии: выбранный прошлый день, открытие существующей записи, будущий день без кнопки записи, диапазон прогноза, свайп месяца и узкий экран со шрифтом 130%/200%. Все семь столбцов помещаются по ширине; месяцы переключаются вертикальными свайпами/стрелками и через заголовок с выбором года и месяца. Проверяются bounds первого/последнего столбцов.

`PeriodRulesTest` покрывает будущие/обратные даты, один день и 29 февраля. Repository tests проверяют DST, конкурентные вставки/правки, сохранность UUID/createdAt, отсутствие изменений при ошибке, reopen/delete и восстановление базы; история из 31 записи не усекается. ViewModel tests проверяют сохранение/ошибки, повторное нажатие и пересчёт прогноза из Room после правки/удаления. `CyclePredictionTest` проверяет расчёт и ограничения из [PREDICTION.md](PREDICTION.md), включая растущие диапазоны и отсутствие выдуманных прошедших циклов.

PNG в `app/build/reports/screenshots/`: пустой главный экран, заполненные записи и текущая менструация в светлой/тёмной теме, узкий экран со шрифтом 200%, формы записи/окончания/правки/удаления, календарь с записями/прогнозом/тёмной темой и узкий календарь со шрифтом 130%/200%. Добавлены EN home/calendar/form, карточка прогноза в обеих темах/языках и EN forecast 320dp/fontScale 2.0 с доступным действием после прокрутки. Английский сценарий выполняет add/edit/reopen/delete и системный date picker. Это изображения для просмотра с синтетическими данными; pixel regression baseline ещё не создан. Room schemas v1/v2 сохраняются в `app/schemas/`; DayLogBackupTest проверяет настоящую миграцию 1→2 с сохранением записи.

Просмотр интерфейса без APK на телефоне:

```powershell
& ./scripts/preview-ui.ps1 -Open
# Если тесты уже выполнены и PNG актуальны:
& ./scripts/preview-ui.ps1 -SkipRender -Open
```

Без `-SkipRender` скрипт принудительно запускает HomeScreenTest и формирует `app/build/reports/screenshots/index.html`. Требуются настроенные локальные Java/SDK. Без `-Open` браузер не запускается. Галерея содержит 53 снимка настоящего Compose render; отображает время генерации каждого PNG, изображения открываются в полном размере. Это статический просмотр. CI создаёт ту же галерею после тестов; скачай `verification-reports`, распакуй ZIP и открой `screenshots/index.html` вместе с соседними PNG. PNG и HTML не коммитятся.

Для нажатий на компьютере: открой репозиторий в Android Studio, подготовь SDK/JDK по требованиям проекта, создай виртуальный телефон в Device Manager, выбери его и нажми Run. Обновления устанавливаются через Run/Apply Changes, без скачивания artifacts на телефон. [Официальная инструкция эмулятора](https://developer.android.com/studio/run/emulator). Эмулятор в текущем workspace не установлен и не проверен. Для отдельных состояний открой `RitelaApp.kt` в режиме Design/Split: добавлены previews пустого экрана, крупного текста и заполненной тёмной темы. Превью не открывает Room или реальные данные.

Новые сценарии: Настройки → тёмная тема → Activity recreation → светлая → системная; проверяется фактический цвет фона и отсутствие полей цикла/длительности; выбор года/месяца без клавиатуры; swipe up/down; запись диапазона двумя тапами через 29 февраля/границу месяца. Domain tests проверяют стартовую оценку после первого начала, measured duration и предполагаемые дни. ViewModel/settings tests проверяют повторное открытие preferences и пересчёт без изменения записей. Snapshot helper выбирает активный Dialog, чтобы не захватить уже закрытое окно выбора месяца.

Отчёты: `app/build/reports/tests/testDebugUnitTest/index.html` и `app/build/reports/lint-results-debug.html`. APK: `app/build/outputs/apk/debug/app-debug.apk`.

`verify-apk.ps1` проверяет подпись, application ID, min/target SDK, отсутствие сетевых разрешений и пишет SHA-256 рядом с APK. Это проверка debug артефакта, не release signing audit.

Установка на подключённое тестовое устройство Windows:

```powershell
& ./.toolchain/android-sdk/platform-tools/adb.exe devices
& ./.toolchain/android-sdk/platform-tools/adb.exe install -r ./app/build/outputs/apk/debug/app-debug.apk
& ./.toolchain/android-sdk/platform-tools/adb.exe shell am start -n app.ritela/.MainActivity
```

Instrumentation-набор и emulator пока не настроены; device testing нельзя считать пройденным по JVM-тесту. CI повторяет локальные build/unit/lint/format checks, сохраняет debug APK, SHA-256 и отчёты. Пользователь подтвердил успешный CI после исправлений setup-java и Prepare SDK и скачал первый APK. Это подтверждение предыдущего workflow; удалённый запуск текущего изменения UI/галереи ещё не подтверждён.

Для setup-java указывай Adoptium `version_data.semver`, а не имя релиза или каталога JDK: `17.0.20+101` соответствует `jdk-17.0.20.1+1`. Локальный bootstrap сохраняет исходное обозначение релиза и закреплённый архив. При обновлении CI JDK проверяй SemVer и наличие Linux x64 JDK в Adoptium API; затем подтверждай полный удалённый запуск, включая APK artifacts.

На Ubuntu runner вызывай `"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"`, проверив executable, с `--sdk_root="$ANDROID_HOME"`. Не полагайся на наличие sdkmanager в PATH. apksigner/aapt2 также вызываются внутри ANDROID_HOME. Используются предустановленные command-line tools и лицензии образа GitHub; обновления образа могут менять версии этих tools. При изменении run-блоков проверяй Bash syntax и shell-вызов с макетом SDK вне PATH, включая распространение ошибок; такая проверка не заменяет установку пакетов и сборку на Linux runner.

Новые проверки: DayLogBackupTest — миграции v1→v3 и v2→v3 с реальной старой таблицей/identity hash, explicit NONE vs NULL, multi sex, ограничения, шифрованный round-trip в другую базу, неверный пароль/изменение ciphertext/неизвестные версии/размер/work factor, idempotent merge и rollback конфликтов. CyclePredictionTest — окно 12 месяцев и медиана длительности при одной длинной записи. Compose — запись дня сегодня и правка в календаре после recreation, взрослая лекарственная карточка до/после подтверждения, EN/password, узкая форма 320dp/200%. PasswordDialog использует ограниченный полноэкранный layout вместо AlertDialog text slot с повторной измерительной композиционной петлёй. Это проверяется тестом, а не увеличением timeout.

Проверка метаданных статей: `& ./scripts/check-health-content.ps1` (входит в CI Validate workspace); свежие медицинские источники и значения проверяются отдельно по [HEALTH_CONTENT.md](HEALTH_CONTENT.md).

## Проверки polish 2026-10-06

PeriodSelectionTest: диапазон через занятую запись, смежные дни, открытая запись, исключение собственного UUID, годовые измерения и незавершённые периоды. HomeScreenTest: disabled занятые клетки и пересекающийся конец, запрет сохранения до исправления, доступность своей записи, выбранная sparkline-точка, сохранённые сценарии CRUD/recreation. Календарь проверяет свободную прокрутку, детали выбранного дня и все семь столбцов на 320dp при 130%/200%. Темы проверяются через dialog, а не встроенные radio buttons. С прежнего набора в 56 тестов проверка расширена до 65.

Исходные PNG сохранены локально в app/build/reports/polish-before. `& ./scripts/preview-ui.ps1 -SkipRender -CompareBefore` создаёт screenshots/comparison.html с семью парами до/после. CompareBefore требует исходные PNG, в обычном CI используется прежний вызов без этого флага. Галерея включает 53 снимка, новые состояния зарегистрированы в скрипте.

Проверка выявила нулевой viewport при крупных шрифтах: перенесена легенда в отдельный dialog, подписи действий сокращены до Сегодня/История. Зависший диагностический запуск остановлен; после исправления выполнен полный обычный набор тестов. Допуски/timeout/lint не отключались. JVM не подтверждает TalkBack, реальный SAF, скорость прокрутки на устройстве или клинический review.

## Синхронизация Room и ViewModel в тестах

Новая запись в Room Flow может появиться до завершения coroutine сохранения. Перед следующим edit/delete тест должен дождаться обоих сигналов: ожидаемых данных и saved=true/saving=false. clearResult() сбрасывает уведомление, но не завершает текущую операцию. PeriodViewModelTest.awaitPersistedState проверяет это условие и сразу сообщает storage/validation error вместо ожидания таймаута. Условия сохранены в тесте пересчёта после edit/delete; таймаут 5 секунд не увеличен.

Повторить только этот набор с реальным запуском тестов, без повторной компиляции всех зависимостей:

```powershell
& ./scripts/gradle.ps1 testDebugUnitTest --tests app.ritela.PeriodViewModelTest --rerun
```

`--rerun` относится к задаче testDebugUnitTest; обычный запуск без него может вернуть UP-TO-DATE/FROM-CACHE. Повторения нужны при проверке исправления гонки; они не заменяют полный обычный набор тестов и подтверждение нового CI run.

## Снимки интерфейса и build cache

PNG в app/build/reports/screenshots объявлены Gradle output задачи testDebugUnitTest (uiScreenshots). Они должны восстанавливаться вместе с XML/HTML test reports при FROM-CACHE. Без этого тесты могут успешно восстановиться из кэша, но preview-ui.ps1 -SkipRender упадёт на Missing render. Галерея продолжает требовать все снимки; пропуски не скрываются. Для проверки кэша после полного прогона временно перенеси каталог снимков в проверенный игнорируемый путь внутри workspace, повтори testDebugUnitTest и проверь FROM-CACHE, 53 PNG и preview-ui.ps1 -SkipRender.

2026-10-07: JournalLayoutTest проверяет stable ID, перестановку/минимум одного тега и отделение custom tags от медицинских enum. Backup tests — plaintext/пароли 1 и 300 символов, перенос layout/иконок/custom, чтение payload v1, миграция с сохранением записей v2. Compose проверяет название/новый раздел/перезапуск/ноль разделов и заметку, drag после удаления тега, Save/Cancel иконки, изменение длительности прогноза. Dialog с полем ввода использует ограниченный полноэкранный layout: AlertDialog intrinsic text slot зацикливал измерение при наборе. Галерея добавляет journal-editor и journal-note-only.

Текущий локальный профиль по запросу пользователя 2026-10-07: не просматривать UI, не создавать PNG/галерею и не запускать полный набор тестов без необходимости. Сборка/checkKotlin/lint и целевые проверки: CyclePredictionTest (12), calendarDayScrollingDoesNotMoveOrDismissTheSheet (1), homeCountdownUsesTodayAndHandlesExpectedDatePassing (1), journalTagsCanBeReorderedWithLongPressDrag (1) — PASS раздельными прогонами. Полный набор текущего пакета не запускался. CI сохраняет полный набор. Для иконки достаточно сборки, lint, проверки PNG-копии по SHA-256 и verify-apk; визуальная проверка не проводилась.

Небесный hero/swipe: локально прошли 1 тест прокрутки и сворачивания свайпом шапки, 2 CycleSceneTest, отсчёт и новый тест ширины 320dp со шрифтом 200%/dark и info-dialog. Всего 5 целевых тестов по раздельным прогонам; полный набор не запускался. Существующие renderForecastHome проверки обновлены под строку «День 24 цикла» и остаются для CI; PNG не создавались локально. FPS, TalkBack и свайп на устройстве не проверялись. Отключение system animator scale и low-RAM реализовано, но отдельный device smoke test ещё нужен. Original prompts сохраняются побайтно; cr-at-eol разрешён только для docs/prompts в .gitattributes, чтобы Windows CRLF не считался trailing whitespace.
