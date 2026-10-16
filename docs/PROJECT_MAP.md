# Карта проекта

2026-10-16: локальная часть этапа 8 —
[матрица восстановления и выпуска](architecture/RITELA_SYNC_STAGE_8.md),
bounded tamper corpus в RelayCryptoTest, malformed/atomic ACK/burst tests relay,
устойчивые числовые metrics snapshots. `.github/workflows/relay.yml` проверяет
local workerd/D1 на Node 24; release повторяет relay checks до signing.
Android CI использует cross-platform `scripts/verify-apk.ps1`; release.py проверяет
network permissions/cleartext вместе с version/certificate. Production/device
и independent security review остаются отдельными условиями допуска пилота.

2026-10-16: продолжение этапа 5 — `data/sync/RelayStorage.kt` (5 Room 11
entities/DAO), `RelayRuntime.kt` (registration/protected routes/immutable HPKE/
apply/ACK/cursor/retry), `SyncWorker.kt` (worker и scheduler), явная
MIGRATION_10_11/schema 11. `UserProfiles.kt` восстанавливает work из Room и
отменяет HTTP перед close; `RitelaApplication.kt` — deferred WorkManager config.
Manifest разрешает сеть и запрещает cleartext. `app/build.gradle.kts` задаёт
точный origin через ritela.relayOrigin. В gradle.properties теперь задан
предоставленный оператором https://ritela-relay.zabvenen.workers.dev; пустой
`-Pritela.relayOrigin=` отключает доступность подключения в сборке. SyncSettings и
PartnerSecuritySettings предлагают consent/pilot activation и явный manual setup.
`RelayRuntimeTest` проверяет двухстороннюю доставку/recovery без сокетов,
`SyncWorkerTest` — constraints/unique work/stale profile через TestDriver.
Детали — [этап 5](architecture/RITELA_SYNC_STAGE_5.md); ниже история прежних шагов.

2026-10-15: начат этап 7 — `ui/SyncSettings.kt`, вход из SettingsScreen, EN/RU sync.xml; `data/sync/SyncSettingsRepository.kt` (metadata projection/DAO/Flow, атомарные local cancel/pause/disable), ProfileSession.syncSettings. Room 10/schema прежние. SyncSettingsTest/SyncSettingsUiTest покрывают локальные действия и адаптацию без PNG. Реальное включение интернета заблокировано до runtime этапов 5–6. [Границы этапа 7](architecture/RITELA_SYNC_STAGE_7.md).

2026-10-15: начат этап 6 — `data/sync/SyncMessageQueue.kt` (Room intent/DAO/consent/context/limits/reconcile), `PartnerRepository.kt` (атомарная постановка подтверждённых DATA/DOODLE и ACK/OPENED, отзыв/удаление/E2E status), `SyncMessageQueueTest.kt`. Room 10/MIGRATION_9_10/schema 10.json добавляет только sync_message_intents; 22 прежние entities сохранены. Все migration builders/tests обновлены, 1…9→10 и rollback из 6/7/8/9. Runtime HTTP и WorkManager из этапа 5 ещё не подключены. [Границы и продолжение этапа 6](architecture/RITELA_SYNC_STAGE_6.md).

2026-10-15: начат этап 5 — `data/sync/HttpRelayTransport.kt`: явный HTTPS origin allowlist, подписанные upload/control/fetch/storage ACK, bounded/cancellable HttpsURLConnection и strict page validation; `HttpRelayTransportTest.kt` — synthetic protocol/consent/cancellation/error tests без внешней сети. Пока не подключён к приложению/WorkManager, INTERNET permission отсутствует, Room 9 прежняя. Точная точка продолжения — [этап 5](architecture/RITELA_SYNC_STAGE_5.md).

2026-10-15: локальный этап 4 — `relay/` (Worker/WebCrypto/D1, миграция, роли/квоты/author fencing/cleanup и 16 tests); `data/sync/PartnerEnvelopeCrypto.kt`, `RelayKeyVault.kt`, `RelayRequestAuth.kt`, `RelayTransportCertificate.kt` — отдельные Tink HPKE/API keys и подписанный transport binding; `RelayCryptoTest.kt` — 4 Android crypto/vault/vector tests. Room 9 не менялась, runtime Internet ещё не подключён. [Результат и ограничения](architecture/RITELA_SYNC_STAGE_4.md), [локальные команды relay](../relay/README.md).

2026-10-15: этап 3 Sync завершён локально. `PartnerRepository.kt` — явные capabilities/author APIs, manual encoder, общий receive/ACK/OPENED, RECOVERY_REQUIRED перед identity import и скрытая SYNC_PROJECTION; `PartnerSnapshot.kt` — её приоритет над v1 той же личности; `PartnerBluetooth.kt` — поддержка новых kinds в прежнем ручном RFCOMM. `SyncQueueRepository.kt` — atomic multipart и E2E ACK race; `SyncManualTransport.kt` — явный request/reply SyncTransport без default I/O. `SyncReceiveTest` (10 тестов), multipart SyncQueueTest и regression/migration suites проверяют весь локальный путь. Room 9, старые 15 entities сохранены; интернет/HPKE/relay/automatic send/UI не подключены. Детали и ограничения — [этап 3](architecture/RITELA_SYNC_STAGE_3.md).

2026-10-15: первая часть этапа 3 Sync — `data/sync/SyncEnvelopeCodec.kt` (явный ручной application v2 payload/capabilities), `SyncApplyRepository.kt` (отдельная received projection, chunks/gaps/tombstones), `SyncReceiveStorage.kt` (семь новых таблиц), `SyncAuthorGrants.kt` (явное initial grant/retirement), `domain/sync/SyncConflict.kt`. Room 9/MIGRATION_8_9, schema 9.json; прежние 15 entities сохранены, новые таблицы не активируются при открытии. Migration/compatibility builders и tests обновлены для 1…8→9, включая старые bytes очереди. Интеграция Partner/manual transport, исходящий multipart и их тесты сохраняются следующим коммитом этого же этапа; сеть/HPKE/UI не подключены.

2026-10-15: этап 2 Sync реализован. `data/sync/SyncJournal.kt` — общий transactional ChangeRecorder/журнал; `ShareProjector.kt` — frozen-scope projection/canonical JSON; `SyncQueueStorage.kt`/`SyncQueueRepository.kt` — snapshot/backfill, per-link progress/projection digest, durable bytes/leases/retry/revoke/compaction. PeriodDao/DayLogDao/JournalDao журналируют и import без изменения repository API. Room v8/MIGRATION_7_8 зарегистрирована; `8.json` экспортирована. `SyncJournalTest`/`SyncQueueTest` и обновлённые migration/compatibility tests покрывают локальное хранение/permissions/restart. Production send/receive не подключены. Детали: [этап 2](architecture/RITELA_SYNC_STAGE_2.md), DATA_FORMAT и TESTING.

2026-10-15: `domain/sync/SyncEngine.kt` — чистые queue/transport ports, lease и один явный dispatch step с IO retry/cancellation; `SyncEngineTest` — пять сценариев. Реального transport/scheduler нет; storage-часть этапа 2 сохраняется отдельно.

2026-10-15: этап 1 Sync реализован. `data/sync/SyncEntities.kt`, `SyncStateDao.kt`, `SyncStateStore.kt` — четыре служебные таблицы, транзакционные defaults и fail-closed reader без activation/network; `RitelaDatabase` v7/MIGRATION_6_7 и ProfileSession зарегистрированы. `app/schemas/app.ritela.data.RitelaDatabase/7.json` экспортирована. `SyncMigrationTest`/`SyncStateStoreTest`/`SyncCompatibilityTest` и общий fixture helper `SyncTestDatabases.kt` проверяют сохранность/reopen/rollback/legacy packets/files. Подробнее — DATA_FORMAT, TESTING и обновлённый рабочий план этапа 1. Новые record tag keys — sectionId/tagId; runtime edit/send paths прежние.

2026-10-15: `domain/sync/SyncModels.kt`, `SyncInvariants.kt`, `SyncLimits.kt` — чистые модели consent/author generation/record revision/link policy, правила переходов и отдельные internet budgets; `SyncModelsTest` проверяет инварианты. Runtime обмена не подключён, storage-часть этапа 1 следует отдельно.

2026-10-15: [architecture/RITELA_SYNC_STAGE_1.md](architecture/RITELA_SYNC_STAGE_1.md) — рабочий план только моделей/инвариантов, четырёх служебных Room-таблиц, миграции 6→7 и тестов сохранности; код не создан. Основной Sync-план уточняет delivery, author fencing/recovery, Tink HPKE, pilot registration, отзыв/удаление и лимиты; архитектура принята за основу, реализация ожидает согласования.

2026-10-15: [architecture/RITELA_SYNC_PLAN.md](architecture/RITELA_SYNC_PLAN.md) — аудит текущего partner-протокола и предложение необязательного Ritela Sync через Workers/D1: журнал изменений, разрешения, очередь, E2E, API/схема relay, лимиты и восемь этапов. Это план для согласования, а не реализованные модули; Android и облако не менялись.

2026-10-15: CycleOrbitScene разделяет подписи 18 sp и рисованные стрелки между hero и подробной орбитой. SettingsScreen располагает версию в конце прокручиваемой колонки под WithLoveButton. LoveTapSequence ограничивает интервал 500 мс; UserSettings оборачивает каждый профиль в SettingsGroup. HomeScreen больше не показывает privacy_description.

2026-10-14: PartnerDeleteChoices выбирает контакты галочками перед remove. PartnerManagement называет карточку «Настройки партнёра» и рисует действия кнопками. Карточка «Дополнительно» в PartnerScreen показывает телефоны и код.

2026-10-14: PartnerDialogList показывает app_name тем же titleLarge 32 sp, что Home и Settings. partner_check_connection в меню переписки — «Дополнительно».

2026-10-14: CalendarMonthFeed ставит стрелку у месяца. PartnerCalendarSummaries сворачивает легенду и показывает день цикла и фазу из снимка. MonthGrid красит FERTILE_ESTIMATE так же, как FERTILE_LIKELY. Шапка переписки не показывает пустой статус. SettingsScreen держит `v` + versionName в правом нижнем углу.

2026-10-14: PartnerHome показывает имя единственного контакта обычным текстом HomeColors.text. Кнопка выбора остаётся только при нескольких контактах.

2026-10-13: PartnerSettings включает PartnerSecuritySettings в общую SettingsGroup режима приложения. SettingsGroup поддерживает modifier; служебные действия — OutlinedButton. PartnerControlsTest проверяет общий контейнер и действия при 200%. OrbitGeometry делит проекцию/защиту концов дуг для обеих кликабельных полос пыли; CycleOrbitScene рисует только солнце, овуляцию и луну как landmarks, оставляя доступные кнопки фаз.

2026-10-13: ui/PartnerMessageActions.kt — меню долгого нажатия, необязательный флажок и подтверждение удаления. PartnerRepository.deleteMessage/PartnerDao сохраняют tombstones; conversationMessages скрывает их, partnerDataMessage сохраняет скрытый непустой снимок. Room v6/MIGRATION_5_6 зарегистрирована в ProfileSession и миграционных тестах, схема 6.json сохранена. PartnerMessageDeletionTest проверяет миграцию, рестарт, fallback, replay, поздние квитанции и сохранность собственных данных; PartnerMessageDeleteUiTest — выбор и подтверждения. CalendarMonthFeed расширяет диапазон получателя для всей переданной истории, включая даты старше обычных 100 лет.

2026-10-13: ui/PartnerSendDialog.kt — окно отправки, общая строка действий одинаковой высоты и состояние явного запроса PartnerSendRequests. PartnerScreen привязывает запрос к профилю/переписке; сохранённый packet не открывает окно. PartnerModal поддерживает закреплённый footer редактора дудла.

2026-10-13: ui/CalendarMonthFeed.kt — общий каркас календаря владельца и получателя. CalendarScreen передаёт собственную классификацию, PartnerReceivedCalendar — только полученный PartnerSnapshot. CalendarDesign делит общую легенду; история получателя только для чтения.

2026-10-13, пыль орбиты: CycleOrbitScene рисует две простые дуги с dustFade. Фолликулярная — красный к зелёному, лютеиновая — зелёный к едва розовому. phaseCallout ставит подписи Caveat вне дуги: лютеиновую слева сверху, фолликулярную справа снизу; res/font/caveat.ttf. orbitOvulationProgress ставит жемчужину на день длина−14. OrbitMarker.END = 0.92.

2026-10-13, список чатов: PartnerChats переключает PartnerDialogList и PartnerConversation. orderedDialogs сортирует контакты по последнему DATA/DOODLE. Прокрутка ленты хранится в SaveableStateHolder на время вкладки. BackHandler закрывает переписку. Статус шапки — partner_nearby только при activePeer.

2026-10-13, срок дудла: Doodle.delayHours/delayMinutes добавляются к delayDays в unlockAt. PartnerCodec пишет hours/minutes и читает их как 0, если полей нет. DoodleEditor показывает три обведённые режима и отдельные кнопки штриха. PartnerExchangeScene использует partner_waiting и Button для закрытия.

2026-10-13, послание и дудл в обмене: domain/SavedDoodles.kt — предел 15, сортировка по использованию и послание до 500 знаков. data/SavedDoodleLibrary.kt хранит рисунки профиля в partner-<id>.doodles и удаляется вместе с профилем. confirmData кладёт необязательные note/doodle в DATA; validateData их проверяет. ui/SavedDoodleUi.kt — карусель, редактор с сохранением и галерея удаления. PartnerScopeDialog добавляет кнопку и поле. Пузырь DATA показывает послание и рисунок.

2026-10-13, файловое получение: PartnerViewModel.receiveFile сохраняет пакет и не ставит ACK в транспорт. PartnerScreen открывает «Как отправить» только для исходящих DATA/DOODLE. openDoodle(reply = false) отмечает дудл открытым без файла OPENED. Bluetooth ACK на том же сокете сохранён.

2026-10-13, пузырь DATA: PartnerFormatter.dataTitle/chatStatus(dated) — момент created в заголовке и момент received со статусом внизу. PartnerConversation показывает это только для DATA; DOODLE сохраняет время получения. Строки EN/RU — partner_data_recorded. PartnerMessengerTest проверяет порядок на разных моментах.

2026-10-13, интерфейс партнёра: ForecastCard.kt — общий CycleForecastHero; CycleOrbitDetail.kt — общий CycleOrbitDetails со статьями. PartnerHome/PartnerCalendarPage используют homeSelected; PartnerScreen сохраняет selected для переписки. PartnerSnapshot классифицирует семь фертильных дней и предоставляет фазу фокуса по переданным датам. PartnerExperienceTest — выбор главной отдельно от чата, общий hero/detail/articles и подробности календаря.

2026-10-13, пыль орбиты: CycleOrbitScene рисует кэшированную розово-зелёную фолликулярную область вместо планеты; OrbitGeometry.follicularDustContains/dustClearOfEndpoints задают hit testing и отступы от объектов. OrbitDustInteractionTest проверяет вызов прежней справки и независимость кнопок солнца/овуляции; OrbitGeometryTest проверяет дугу в разных размерах.

2026-10-13, Partner sharing: PartnerIdentity.lastSelection / PartnerCodec — совместимое локальное JSON-поле; PartnerSharing.remembered — фиксированный набор ID тегов; PartnerRepository.confirmData — атомарное подтверждение пакета и выбора; PartnerShareSelectionTest — изоляция адресатов, legacy и отсутствие автоматического расширения. В UI одна команда «Поделиться данными».

2026-10-12, messenger: ui/PartnerConversation.kt — закреплённые шапка/панель, выбор диалога, LazyColumn с датами и DATA/DOODLE, пять Compose Previews. PartnerScreen остаётся координатором настоящего защищённого обмена и модальных сценариев. PartnerFormatter централизует краткое содержание/статусы; PartnerSharing.union расширяет права только после явного подтверждения выбранного. Основной вход grants перемещён из Settings в диалог/профиль. PartnerMessengerTest проверяет ленту, навигацию действий, разрешения и рендеры; имена зарегистрированы в preview-ui.ps1. Схема БД и wire format неизменны.

2026-10-12: PartnerInvitationFile.kt различает публичное подписанное приглашение и зашифрованный обмен. PartnerFiles сохраняет/отправляет приглашение через SAF/FileProvider; PartnerViewModel маршрутизирует импорт. PartnerBluetooth добавляет отдельный ограниченный RFCOMM-сеанс приглашений. PartnerQr.kt и PartnerCaptureActivity.kt удалены вместе с ZXing; новая инструкция — PARTNER_CONNECT.md. Room/форматы ключей и сообщений прежние.

2026-10-12: domain/PartnerSharing.kt — granular draft на прежнем ShareScope; ui/PartnerSharing.kt — разрешения и выбор передачи. data/PartnerSnapshot.kt — полученный DATA без нового расчёта; ui/PartnerHome.kt/PartnerReceivedCalendar.kt — отдельные Today/Calendar с MonthGrid. PartnerSecuritySettings.kt — перенос ключей/Bluetooth/grants/единственные технические подробности; PartnerQr.kt — полный экран QR; PartnerExternalFile.kt/MainActivity — подтверждённые VIEW/SEND content URI. Новые сценарии: PartnerRefinementTest/PartnerControlsTest. Подробности — PARTNER_UX_REWORK.md. Room v5 прежняя.

2026-10-11: `data/PartnerPairing.kt` — канонический контекст подключения и короткий код; PartnerRepository/PartnerCodec/PartnerDevice — сохранение локального и взаимного доверия без изменения Room. `ui/PartnerDesign.kt` — onboarding, действия, карточка близкого и проверка кода с Compose Previews; `ui/PartnerCaptureActivity.kt` — сканер без принудительного landscape. PartnerPairingTest/PartnerControlsTest проверяют протокол, совместимость и кнопки. Исходные требования: [промпт](prompts/2026-10-11-partner-onboarding.txt).

2026-10-11: HelpCards.articleWeights/rankedArticles используют последние 14 дней своих записей; Home и полный каталог разделяют порядок. HelpArticlesTest проверяет затухание, срочный приоритет и исключение будущих/старых событий.

2026-10-09, удаление пользователей: ProfileRegistry добавляет nameVersion/очередь deleted, ProfileManager.delete готовит fallback-сессию, фиксирует каталог и очищает базу/sidecars; очередь повторяется при старте. UserSettings содержит локализованное подтверждение и действия Edit/Delete в адаптивном FlowRow. BackupActions/UserSettings безопасно читают имя при переходе между сессиями. Default qwerty; после удаления последнего — новая личность с новым UUID. UserProfilesTest/UserProfilesUiTest покрывают удаление, изоляцию, отмену, рестарт очистки и крупный шрифт. Это отменяет прежнее ограничение на удаление профилей; схема Room прежняя.

2026-10-09, локальные пользователи: data/UserProfiles.kt — каталог UUID/имён, ProfileManager и закрываемая ProfileSession; RitelaApplication предоставляет репозитории выбранной сессии. data/ProfileSettings.kt и Room v4/MIGRATION_3_4 — независимые настройки в каждой базе, исходный ritela.db остаётся у «Я». ui/ProfileApp.kt — сброс UI по токену сессии и ресурсы языка; ui/UserSettings.kt — выбор/добавление/переименование и язык system/RU/EN, редактор имени через JournalDialog. BackupRepository payload v3 — выбор категорий и атомарный импорт; ui/BackupChoices.kt и BackupActions — категории и привязка системного picker к сессии. UserProfilesTest/UserProfilesUiTest/DayLogBackupTest проверяют изоляцию, миграцию, выбранный импорт и взаимодействия; HomeScreenTest ждёт начальную загрузку профиля. Детали формата: DATA_FORMAT.md/BACKUP.md. Это заменяет прежнюю единственную глобальную базу и настройки SharedPreferences.

2026-10-09, черновик месячных: domain/Period.kt содержит PeriodRange для ещё не записанных дат. PeriodEntry хранит пары epoch-day в rememberSaveable, рисует их через существующий MonthGrid и блокирует пересечения. RitelaApp не пересоздаёт форму после отметки; передаёт весь пакет в PeriodViewModel.savePeriods → PeriodRepository.addAll → атомарный PeriodDao.addAllIfSeparate. Room-схема прежняя. PeriodEntryUiTest проверяет черновик/позицию/Cancel/Back/retry; PeriodRepositoryTest и PeriodViewModelTest — атомарность и пересчёт прогноза. Это заменяет описанную ниже механику немедленного сохранения каждого интервала.

2026-10-09, выбор месячных: `domain/PeriodRangeSelection.kt` — закрытый первый клик, упорядоченная пара в любом направлении, проверка пересечений и возможности открытого периода. PeriodEntry использует HomeTheme/MonthGrid и LazyColumn месяцев 1900..сегодня, фиксированные одинаковые по высоте действия и safeDrawing/IME. RitelaApp различает обычное сохранение и сохранение с продолжением, сбрасывает выбор только после state.saved. PeriodRangeSelectionTest/PeriodEntryUiTest проверяют границы, редактирование, 320dp/200%, последовательные Room-записи.

2026-10-09: `ui/DayFieldEntry.kt` — быстрый выбор одного поля, RitelaApp разделяет поле и полный редактор. `domain/JournalPresentation.kt` — независимые record/intimacy маркеры, совместимый selectable-модель и общий toggle. `ic_mood.xml` / JournalIcon.MOOD — настроение без сердца. CalendarScreen рисует до двух символов, сохраняет цвет и держит Сегодня/История под лентой. JournalPresentationTest/DaySelectionUiTest покрывают сохранение других значений, NONE и значки.

Settings 0.3.4: `ui/LoveSurprise.kt` — Canvas overlay и прокручиваемое письмо; `ui/LoveAnimation.kt` — временный счётчик и генерация частиц без Android. `love_strings.xml` EN/RU содержит кнопку; письмо только в default resources с translatable=false. LoveAnimationTest/LoveSurpriseTest проверяют последовательность, частицы, английскую локаль, закрытие, reduced motion и 320dp/200%.

Уточнение Calendar 0.3.3: compactFertilityWindows больше не фильтрует ориентир по объёму/устойчивости истории — возвращает оценки fertilityWindows, сетка использует исключительно их центральное семидневное likely. PredictionMethodInfo принимает includeOrbit; Calendar передаёт false, Home сохраняет default true.

Calendar day UX 2026-10-08: domain/CalendarPresentation.kt — компактная семидневная подсветка/ограничения определённости и CalendarSelection с единым приоритетом. PredictionEngine не изменён. ui/CalendarDayFormatter.kt — локализованные даты/статусы/краткие описания вне composables, переиспользуется общим formattedDate/periodDates и сеткой. CalendarDayDetails — bottom sheet по содержимому с max-height, safeDrawing/IME insets, раскрываемыми диапазонами и скроллом; CalendarDayRecords в Insights сохраняет видимость архивных разделов. На Home длительность месячных только в малой плитке. Новые CalendarPresentationTest/CalendarDayFormatterTest и целевые HomeScreenTest; selected_day_strings.xml EN/RU.

UI 0.3.2: ForecastCard — дата/countdown в Row, увеличенный день цикла; HomeInsightTiles — две карточки общей высоты через IntrinsicSize; CycleOrbitDetail — центрированные кнопки дня. CalendarScreen окрашивает только месячные, сохраняя расчёт фертильности/цветок/подробности. CalendarDesign содержит соответствующую краткую легенду. Insights — последние интервалы/график и ожидаемая продолжительность месячных, без median/range summary. Данные/алгоритмы без изменений.

Прогноз 2026-10-08: domain/PredictionStatistics.kt — взвешенная медиана, rolling-origin backtesting/MAE/медианные ошибки и Monte Carlo на календарный год; domain/FertilityPrediction.kt — независимые модели овуляции/окна/слизи. CyclePrediction.kt объединяет результат и классифицирует календарь. Новые проверки PersonalizedPredictionTest, дополнительные Room/Compose сценарии в PeriodViewModelTest/HomeScreenTest. EN/RU справка/диапазоны — prediction_strings.xml. Room v3/backup без изменений; SettingsRepository игнорирует прежние ручные presets.

Упрощение UI 2026-10-08: CalendarAtmosphericHeader и CalendarOrbitDecoration удалены; CalendarDesign содержит акценты/анимируемые сводки, CalendarScreen — карточки истории с jump к месяцу начала. RitelaApp использует SaveableStateHolder для Calendar (позиция и сворачивание сохраняются между вкладками). CycleOrbitScene показывает число дня по центру, ForecastCard/CycleOrbitDetail используют одинаковые пропорции эллипса; phaseName — названия без повторяемого suffix, справка сохраняет смысл оценки. HomeDesign содержит две плитки и shortcuts mood/discharge/sex/note. Insights показывает ожидаемую длительность из preset. DayLogEntry/редактор повторно используют HomeTheme; JournalLayout разделяет новые defaults и исторический built-in registry, discharge хранится существующим custom JSON. `refinement_strings.xml` EN/RU и три новые vector icons поддерживают подписи/сворачивание/стрелки. SettingsScreen больше не содержит theme selector. Старые описания декора/трёх плиток/выбора темы ниже относятся к предыдущим шагам.

Home orbit 2026-10-08: `ui/OrbitGeometry.kt` — один замкнутый Path, позиции по длине и обратная проекция жеста; `OrbitMarker` — пять символических вех. `ui/OrbitInteractionState.kt` — текущий день/фокус/дата/существующая оценка фазы, без записи данных. `ui/CycleOrbitScene.kt` — общий Canvas компактного и подробного режима; `ui/CycleOrbitDetail.kt` — read-only подробности, drag, slider, доступные кнопки и справка. `orbit_strings.xml` EN/RU; OrbitGeometryTest/OrbitInteractionStateTest и целевые HomeScreenTest проверяют геометрию, даты, взаимодействия и 200%. `ui/CalendarOrbitDecoration.kt` сохраняет прежний независимый рисунок Calendar; новый Home его не изменяет. Старые записи о drawOval/OrbitPhaseMarkers ниже относятся к прежней реализации.

Начало сессии: `STATUS.md` → `scripts/resume.ps1` → текущие файлы задачи. Не загружай полный промпт каждый раз.

| Путь | Назначение |
|---|---|
| `AGENTS.md` | Роль, устойчивые ограничения и Definition of Done |
| `README.md` | Краткое описание и доступные команды |
| `CONTRIBUTING.md` | Правила проверок и коммитов |
| `docs/STATUS.md` | Последний checkpoint и следующее действие |
| `docs/prompts/2026-10-08-home-rebuild.txt` | Оригинальный промпт; текущий scope пользователя — только Home |
| `app/src/main/kotlin/app/ritela/ui/HomeDesign.kt` | Home как источник общей палитры/типографики трёх экранов; insight-плитки и круглые действия Home |
| `app/src/main/kotlin/app/ritela/ui/CalendarDesign.kt` | Calendar на tokens Home: атмосферная шапка, акценты состояний, прогноз/фаза/легенда |
| `app/src/main/kotlin/app/ritela/ui/SettingsDesign.kt` | Группы и доступные строки Settings на существующих tokens Home |
| `app/src/main/res/values/calendar_weekdays.xml`, `values-ru/calendar_weekdays.xml` | Двухбуквенные EN/RU подписи дней недели основной сетки Calendar |
| `app/src/main/res/values/home_strings.xml`, `values-ru/home_strings.xml` | EN/RU подписи нового Home |
| `app/src/test/kotlin/app/ritela/HomeScreenTest.kt` | Взаимодействия Home и целевые рендеры композиции/320dp/200% |
| `docs/DECISIONS.md` | Решения, причины и статус |
| `docs/PROJECT_BRIEF.md` | Полный исходный промпт: разделы 0–63 |
| `docs/prompts/2026-10-04-role.txt` | Точная копия приложения пользователя, обрывается в разделе 10 |
| `docs/AGENT_SKILLS.md` | Происхождение и назначение установленных скиллов |
| `.agent-skills.lock` | Версии и SHA-256 локальных скиллов; внутренний формат |
| `.agents/skills/ritela-resume/SKILL.md` | Восстановление контекста без повторного исследования |
| `.agents/skills/ritela-checkpoint/SKILL.md` | Обновление памяти и проверенный коммит |
| `.agents/skills/ritela-android-check/SKILL.md` | Format/build/test/lint/render/APK verification |
| `.agents/skills/cycle-prediction-validation/SKILL.md` | Проверка расчёта, неопределённости и пересчёта после правок |
| `docs/PREDICTION.md` | Формулы прогноза, достаточность данных, ограничения и проверки |
| `scripts/resume.ps1` | Краткий read-only отчёт о состоянии и инструментах |
| `scripts/check-workspace.ps1` | Ссылки, синтаксис скриптов и целостность lock |
| `scripts/read-brief.ps1` | Вывод только выбранных разделов полного промпта |
| `scripts/bootstrap-toolchain.ps1` | Подготовка локального toolchain |
| `scripts/toolchain.lock.json` | Закреплённые URL, версии и SHA-256 архивов инструментов |
| `scripts/setup-sdk.ps1` | Установка SDK-пакетов и проверка adb |
| `scripts/gradle.ps1` | Сборка с локальными Java/SDK и восстановлением environment |
| `scripts/verify-apk.ps1` | Проверка APK, сетевых разрешений и checksum |
| `scripts/preview-ui.ps1` | Обновление Compose-снимков и локальная HTML-галерея без телефона |
| `docs/UI_DESIGN.md` | Исследование трекеров, принципы текста и план интерфейса |
| `docs/STYLE_GUIDE.md` | Выбранный по макету стиль, tokens, адаптация механик и EN/RU |
| `.agents/skills/ui-quality-audit/SKILL.md` | Сохранение стиля, двуязычные формы, темы и размеры |
| `app/src/main/res/values-ru/strings.xml` | Полный русский интерфейс; английский fallback в values |
| `app/src/main/kotlin/app/ritela/ui/CycleOrbit.kt` | Адаптивный Canvas-декор карточки цикла |
| `app/src/main/kotlin/app/ritela/ui/SettingsScreen.kt` | Язык, системная/светлая/тёмная тема и backup |
| `app/src/main/kotlin/app/ritela/data/SettingsRepository.kt` | Приватные preferences и Flow исходных значений/темы |
| `gradle/libs.versions.toml` | Закреплённые версии зависимостей и plugins |
| `app/src/main/kotlin/app/ritela/` | Activity и Compose UI |
| `app/src/main/kotlin/app/ritela/domain/` | Чистые модели, проверка дат, расчёт цикла и сетка календаря |
| `app/src/main/kotlin/app/ritela/ui/CalendarScreen.kt` | Один месяц / свободная лента месяцев, детали дня, история и действия |
| `app/src/main/kotlin/app/ritela/ui/ForecastCard.kt` | Дата прогноза, диапазон и качество истории |
| `app/src/main/kotlin/app/ritela/data/` | Room database, DAO, storage entity, repository |
| `app/schemas/` | Экспортированные схемы Room v1/v2/v3 |
| `docs/DATA_FORMAT.md` | Фактический формат базы и инварианты дат |
| `app/src/test/kotlin/app/ritela/` | JVM/Compose тесты и render checks |
| `.github/workflows/android.yml` | Проверки, debug APK, checksum и отчёты |
| `.github/workflows/release.yml` | Подписанный APK по тегу, проверки и GitHub Release для Obtainium |
| `version.properties` | Единая версия приложения; versionCode вычисляется из SemVer |
| `scripts/release.py`, `scripts/test_release.py` | Проверки версий/подписи, упаковка APK, ссылка Obtainium и regression checks |
| `docs/RELEASES.md` | Настройка подписи и повторяемый выпуск через Obtainium |
| `docs/TESTING.md` | Доступные команды и ограничения проверок |
| `ARCHITECTURE.md`, `PRIVACY.md` | Фактическая архитектура и меры приватности |

## Этапы

| Этап | Результат | Статус |
|---|---|---|
| M0 | Toolchain, выбранные skills, Android skeleton, первая зелёная сборка, CI APK | Завершён: локальные проверки, успешный CI и скачанный APK подтверждены пользователем |
| M1 | Room и запись менструации | Add/finish/history/edit/delete реализованы и проверены; схема v1 сохранена |
| M2 | Prediction engine, календарь, главный экран | Первая версия реализована: день цикла, медиана/MAD, диапазоны до 12 циклов, календарь и выбранный день; проверка на устройстве впереди |
| M3 | События, симптомы, сексуальная активность | Первая версия: дневные отметки и офлайн-карточки помощи |
| M4 | Insights и настройки | Графики цикла/длительности/боли, язык/тема и backup реализованы; дальнейшие insights впереди |
| M5 | Экспорт/импорт, приватность, защита | Шифрованный backup и транзакционный merge реализованы; app lock впереди |
| M6 | Виджеты и уведомления | Не начат |
| M7 | Визуальная проверка и производительность | Визуальные проверки ведутся уже в M1; добавлена галерея, итоговый аудит впереди |
| M8 | Release pipeline и проверенный APK | Workflow и Obtainium подготовлены; production secrets, первый Release и установка впереди |

Архитектурную карту модулей, форматы данных и команды Android добавлять после появления соответствующего кода. Полные критерии этапов находятся в PROJECT_BRIEF, разделы 58, 60–63.

Новые файлы: domain/DayLog.kt — типы и валидация отметок; data/DayLogEntity/Dao/Repository — Room/Flow; data/BackupRepository — шифрование, preview и atomic merge; ui/DayLogEntry — общая форма; ui/Insights — реальные графики; ui/HelpCards — каталог/подбор/детали; ui/BackupActions — SAF/пароль/подтверждение. Документы [HEALTH_CONTENT.md](HEALTH_CONTENT.md), [BACKUP.md](BACKUP.md); скрипт scripts/check-health-content.ps1 и skill health-content-check. DayLogBackupTest проверяет миграцию и целостность копии.

Доработка UI 2026-10-06: [исходный промпт](prompts/2026-10-06-ui-polish.txt); domain/MeasuredSeries.kt — годовые измерения для статистики; domain/Period.kt — periodConflict для проверки диапазона до сохранения. preview-ui.ps1 -CompareBefore строит сравнение с локально сохранёнными исходными PNG.

Редактируемый дневник: `domain/JournalLayout.kt` — модель/инварианты и преобразование selections; `data/JournalRepository.kt` — Room DAO/JSON codec/поток настройки; `ui/DayLogEntry.kt` — редактор и drag; `journal_strings.xml` EN/RU. Room v3 сохраняет custom selections и иконку дня; backup payload v2 переносит layout. `JournalLayoutTest` и DayLogBackupTest проверяют изменения/миграции/перенос; HomeScreenTest — полноценное редактирование.

Иконка: [ritela.png](../ritela.png) — пользовательский исходник; drawable-nodpi/launcher_art.png — точная копия; mipmap-anydpi/ic_launcher.xml — adaptive icon для minSdk 26, drawable/ic_launcher_foreground.xml — отступ 16.6667% до центральной области 72/108, drawable/ic_launcher_background.xml — сливовый фон #4B2938. [update-launcher-icon.ps1](../scripts/update-launcher-icon.ps1) проверяет квадратный PNG и обновляет копию без перекодирования: `& ./scripts/update-launcher-icon.ps1`.

[Промпт небесного hero](prompts/2026-10-07-orbit-hero.txt) сохранён без изменений. domain/CycleScene.kt — предполагаемая фаза/прогресс представления без изменения расчёта; ui/CycleOrbitScene.kt — лёгкая рисованная орбита; ui/OrbitMotion.kt — lifecycle, системные анимации и low-RAM; CycleSceneTest — границы шаблона и недоступный прогноз.

ui/ForecastCard.kt — новый hero, компактные локализованные даты, info-dialog, ForecastDurationInsight и статичные light/dark/large-font Preview; прежний ui/CycleOrbit.kt удалён. HomeScreen подключает отдельную длительность и CTA с каплей. HomeScreenTest проверяет новый текст и info на 320dp/200%; существующие CI screenshot-тесты Home используют новый hero без новых имён PNG.

Орбита hero: ui/OrbitGeometry.kt — аналитическая эллиптическая геометрия, rotation matrix и упорядоченные OrbitPhaseMarkers; ui/CycleOrbitScene.kt — drawOval, пять вех и кольцо дня. OrbitGeometryTest проверяет принадлежность точек эллипсу, замыкание, положение 8/29 и clamping. HomeScreenTest.renderPolishedOrbitHeroInLightDarkAndLargeText создаёт три целевых PNG orbit-hero-light/dark/large-text, зарегистрированных в preview-ui.ps1. Производственный прогноз и domain/CycleScene.kt не изменены.

UI 2026-10-08: [пользовательский промпт](prompts/2026-10-08-ui-polish.txt) сохранён без изменений. ui/ScreenPreviews.kt — Home/Calendar (normal, narrow EN, dark, 200% RU) на синтетических данных. ui/Insights.kt — JournalQuickActions с реальными разделами дневника; ui/CalendarScreen.kt — один месяц и переключаемая вертикальная лента. res/values/cycle_articles.xml и values-ru/cycle_articles.xml — пять новых образовательных статей; HelpArticlesTest проверяет локализации и приоритет срочных советов. Theme.kt содержит общие RitelaColors/Typography/Shapes/Motion и Spacing. Пользовательские docs/ui остаются отдельными исходными материалами и не включаются автоматически в коммит.

Partner: `domain/Partner.kt` — контакты, grants, normalized doodles; `data/PartnerStorage.kt` — Room/DataStore; `PartnerCrypto.kt` — portable identity/Keystore; `PartnerCodec.kt` — codec; `PartnerRepository.kt` — pairing, selection, encrypted messages. Требования/протокол: [PARTNER](PARTNER.md), оригинал [промпта](prompts/2026-10-09-partner.txt).

Partner UI: `ui/PartnerScreen.kt`, `PartnerChoices.kt`, `PartnerFormatter.kt`, `PartnerReceivedData.kt`, `PartnerViewModel.kt`, `PartnerFiles.kt`; `ui/DoodleEditor.kt` — editor/read-only reveal; `ui/PartnerExchangeScene.kt` + `domain/HeartPhysics.kt` — real-state animation. `data/PartnerBluetooth.kt` — foreground RFCOMM. `res/xml/partner_file_paths.xml` exposes ciphertext cache only. Tests: PartnerTest, PartnerReliabilityTest, PartnerUiTest, PartnerVaultTest.

2026-10-11: JournalLayout/JournalRepository хранят JSON-архив определений тегов и withoutPenetration; JournalPresentation — множественный выбор/приоритет сердечка; DayLogEntry — восстановление defaults и свойства тега; Insights — исторические названия. `res/drawable/ic_heart_filled.xml`, `values[-ru]/journal_updates.xml` — календарная заливка и EN/RU копия. JournalChangesTest проверяет сохранность записей/backup/старого формата; JournalEditingUiTest — выбор и редактор.
