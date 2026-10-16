# Ritela Sync: технический план

Актуализация: 2026-10-16. Основание: аудит после `9679650`, план `c7bac34`
и последующие команды пользователя. Этапы 1–3 реализованы локально; этап 4 дал
локальный Worker/D1 и Tink HPKE. По текущему запросу завершена Android-цепочка
этапа 5, включая Room 11, HTTP runtime, WorkManager и явный pilot setup.
Подключены auto records/forecast, adaptive foreground receive, статусы чата и
мастер file invitation; разрешения закреплены за выбранными ID и подтверждённым
автором. [Инструкция](../RITELA_SYNC_USING.md). Полный server deletion/last-success UX,
author transfer/recovery UI и physical pilot остаются отдельными работами.
Детали этапов — §9; точные проверки — [STATUS](../STATUS.md).
Облако не менялось. Таблицы первоначального аудита ниже описывают исходное состояние.

Пользователь уже создал Cloudflare D1: `133088c5-c0e6-40b7-b264-af6ef7d1cdd8`. Это идентификатор ресурса, не пароль и не адрес API. Доступность, имя базы, account ID, тариф, регион и её содержимое не проверялись: доступа к аккаунту Cloudflare в этом аудите не было. Повторно создавать базу не требуется; применение миграций и развёртывание Worker — отдельные будущие действия.

## 1. Вывод аудита

Ritela уже умеет безопасно обмениваться отдельными сообщениями между явно подтверждёнными устройствами. Можно сохранить идентичности, доверенные контакты, криптографические примитивы и ручные способы доставки. Однако текущий протокол переносит снимки, а не изменения записей. Добавление HTTP к `prepareData()` не обеспечит синхронизацию: поздний снимок способен заменить более новый, удаления записей не передаются, устойчивой очереди повторов нет.

Предлагается выделить общий Sync Engine и версионированный протокол изменений. Для первой версии — один автор исходных записей на одном устройстве, несколько получателей и устройств доставки; параллельное редактирование собственных записей на нескольких телефонах не включать. Получатель хранит отдельную проекцию владельца и никогда не меняет оригинал. При миграции старые сообщения сохраняются как история; новый протокол включается только после согласования возможностей двух сторон.

## 2. Что есть в коде

Все пути в таблице относительно `app/src/main/kotlin/app/ritela/`, если не указано иное. Проверены реализации, а не только прежние описания в документации.

| Область | Реализация и поведение | Вывод для Sync |
|---|---|---|
| Идентичность профиля | `data/PartnerCrypto.kt`: `LocalPartnerKeys.create()`, `identityId`, `certificate()`; RSA-3072 identity, SHA-256 от public key как ID; отдельные UUID устройства, EC-256 ключ подписи, nonce | Сохранить identity и подтверждённые device keys. Локальный UUID профиля не является identity ID |
| Хранение ключей | `PartnerKeyVault.loadOrCreate()/save()`: переносимые приватные ключи зашифрованы AES-GCM под профильным Android Keystore key; `AtomicFile`. Если wrapping key пропал при существующем файле, выдаётся ошибка | Не создавать молча новую личность при ошибке. Приватные identity/device keys не выдавать relay |
| Приглашение и доверие | `data/PartnerRepository.kt`: `inspectInvitation()`, `pair()`, `acceptKnown()`; RSA-сертификат устройства и EC-подпись приглашения. Новая привязка требует invitation v2 | Повторно использовать. Relay не может автоматически подтверждать нового партнёра/телефон |
| Проверка двух сторон | `data/PartnerPairing.kt`: `pairingContext()/pairingCode()`; полный SHA-256 контекст двух endpoints и nonce; восемь цифр только для отображения. `PartnerDevice.mutualConfirmed` блокирует обмен до подтверждения | Сохранить полный контекст. Не превращать короткий код в пароль API |
| Модель контактов | `domain/Partner.kt`: `PartnerIdentity`, `PartnerDevice`, `PartnerContact`, `PartnerDirectory.add()/merge()/remove()` | Контакт может объединять личности; сеть и права привязывать к каждой личности/устройству, не к имени или карточке группы |
| Права | `ShareScope.permits()/normalized()`, `domain/PartnerSharing.kt`: `remembered()/outgoing()`; `confirmData()` атомарно сохраняет пакет, grants и lastSelection | Сохранить явный выбор. `remembered()` фиксирует ID существующих тегов; новые теги не получают автоматического допуска. `SETTINGS` читается в legacy, но исключён из новых передач |
| Данные и схема | `data/RitelaDatabase.kt`: Room v6, явные миграции 1→2→3→4→5→6. `PeriodEntity`: UUID, createdAt/updatedAt, unique startDay. `DayLogEntity`: ключ epoch-day, без revision/updatedAt. Layout/settings — singleton-строки | UUID периода подходит; timestamp не является порядковой версией. Для дня использовать ключ `(ownerIdentity, day)`. Нужны revisions, журнал и tombstones |
| Запись и удаление | `PeriodRepository`/`PeriodDao`: add/addAll/finish/edit/delete, проверки пересечений. `DayLogRepository.save()`: пустой день физически удаляется. `JournalRepository.save()`, `BackupRepository.import()` также меняют записи | Охватить все пути изменения в одной транзакции с журналом, включая import. Наблюдение за Flow само по себе теряет удаления и промежуточные изменения |
| Формирование пакета | `PartnerRepository.preview()/prepareData()/confirmData()/prepareDoodle()` и private `prepare()` | `preview()` пригоден как проверенная отправная точка фильтрации/первого снимка. Выделить serializer/projector и crypto envelope из большого repository, сохранив ручной API |
| Шифрование | `prepare()`: случайный AES-256 key, IV 12 bytes, AES-GCM/128-bit tag, AAD `ritela.partner:1`; ключ обёрнут RSA-OAEP SHA-256/MGF1-SHA256 получателя; EC/SHA-256 подпись точной строки header | Это E2E envelope, пригодный как база после усиления протокола. Не сертификация безопасности интернет-сервиса |
| Формат | Внешний JSON `header` (строка) + `signature`; header `ritela.partner.encrypted` v1 содержит from/device/to/targetDevice/key/iv/data; зашифрованный body `ritela.partner` v1 содержит UUID, link nonce, kind, created, content | Внешние устойчивые identity/device ID видимы серверу, если загрузить envelope напрямую. Для минимизации метаданных нужен дополнительный внешний слой или явное принятие этой утечки |
| Проверка/дедупликация | `receive()`: размер, адресат/телефон, существующее доверие и enabled, подпись, GCM, совпадение адресов body/header, link nonce, тип и содержимое; `PartnerDao.message(id)`/insert ABORT в транзакции | Переиспользовать проверки. ID защищает от повтора того же сообщения, но не от старого содержимого с новым ID и не от отката локальной БД |
| Inbox/outbox | `data/PartnerStorage.kt`: `PartnerMessage`, `PartnerDao`, JSON-каталог. Таблица хранит исходящие и входящие сообщения, body открытым локально и ciphertext envelope | Это история, а не scheduler: нет attempts, nextAttempt, lease, scope epoch, per-device delivery. Не называть существующую таблицу готовой sync queue |
| Удаление сообщений | `deleteMessage()`, `hide()/erase()/clearReceived()`: ID сохраняется; `hiddenFromChat` введён MIGRATION_5_6 | Сохранять защиту от replay. Локальное удаление пузыря не означает удаление оригинальной записи у владельца или удалённой копии |
| Квитанции | `acknowledgement()`, `receive()` обрабатывают ACK/OPENED с проверкой исходного сообщения/адресата; `openDoodle()` создаёт OPENED для отдельного DOODLE | Переиспользовать смысл квитанций; добавить устойчивую очередь и per-device статусы. Сейчас OPENED для вложенного в DATA дудла протоколом не предусмотрен |
| Дудлы | `domain/Partner.kt`: `Doodle`, `unlockAt()/canOpen()`, лимиты штрихов/точек/подписи; `PartnerCodec.doodle()`, `data/SavedDoodleLibrary.kt`, `domain/SavedDoodles.kt` | Сохранять формат рисунка и локальную библиотеку. AFTER_DAYS отсчитывается от первого локального received, включая часы/минуты; повтор доставки не должен менять это время |
| Файл | `ui/PartnerFiles.kt`, `PartnerExternalFile.kt`, `PartnerViewModel.receiveFile()`, `data/PartnerInvitationFile.kt` | SAF/FileProvider и `.ritela` сохранить. Импорт файла не создаёт автоматический ACK; открытие share sheet не доказывает доставку |
| Bluetooth | `data/PartnerBluetooth.kt`: foreground RFCOMM, `exchangeInvitations()/send()/receive()/close()`, length-prefixed frames (до 4 МиБ), ACK на том же соединении | Это Bluetooth Classic, не Google Nearby Connections и не BLE auto-sync. Существующие `NearbyPhone` — название локальной модели сопряжённых телефонов |
| Режим получателя | `PartnerPreferences` (DataStore), `ui/PartnerHome.kt`/`PartnerReceivedCalendar.kt`, `data/PartnerSnapshot.kt` | Получатель отображает authenticated snapshot без пересчёта прогноза. `partnerDataMessage()` выбирает максимальный local received — для новых дельт это необходимо заменить версионированной проекцией |
| Изоляция | `data/UserProfiles.kt`: `ProfileSession`, отдельные БД, vault, preferences, close/switch/delete; `ui/ProfileApp.kt` | Фоновая задача должна открывать конкретный profile ID, а не текущий выбранный UI-профиль; удаление/переключение должны отменять/закрывать работу безопасно |
| Backup | `BackupRepository` переносит выбранные записи/layout/settings; отдельные `exportIdentity()/restoreIdentity()` в PartnerRepository сохраняют RSA identity | Экспорт identity не сохраняет device key/nonce, контакты, историю или anti-replay state. Restore требует пустого каталога, создаёт новое устройство; это не восстановление всей Sync-сессии |
| Сеть и зависимости | `app/src/main/AndroidManifest.xml`: нет INTERNET; `gradle/libs.versions.toml`: Room/DataStore/Coroutines уже есть, WorkManager/HTTP-клиента нет | Разрешения и сетевой scheduler добавляются отдельным этапом; до согласия никакие обращения к relay не выполняются |

Существующая проверочная база: `PartnerTest`, `PartnerPairingTest`, `PartnerInvitationsTest`, `PartnerVaultTest`, `PartnerReliabilityTest`, `PartnerShareSelectionTest`, `PartnerMessageDeletionTest`, `PartnerExperienceTest`, `UserProfilesTest`, `DayLogBackupTest` в `app/src/test/kotlin/app/ritela/`. Их наличие не означает, что новый Sync уже протестирован; в этом документационном шаге Android-тесты не запускались.

## 3. Границы первой версии

Согласие на ручную передачу не включает интернет и не включает автоматическую отправку будущих записей. Для каждого профиля отдельно сохраняются global Internet Sync enabled, а для каждого адресата — auto-send enabled и точный утверждённый scope. Отправка в группу разворачивается в независимые получатели с независимым шифрованием и правами.

Один профиль-владелец редактирует свои медицинские записи на одном авторском устройстве. Другие сертифицированные устройства могут быть получателями, но не получают автоматически право записи. Модель единственного автора подтверждена пользователем; передача авторства описана в §4.5. Одновременное редактирование оригинала на нескольких телефонах не входит в Sync v1.

Local-first сохраняется: ошибка сети, истечение TTL и исчерпание облачного лимита не мешают сохранять собственные записи. Bluetooth и файлы работают без включения Internet Sync. Для автоматического обмена рядом предусматривается только интерфейс транспорта; обнаружение BLE/Nearby и фоновая работа рядом сейчас не проектируются.

## 4. Поток данных и границы слоёв

```mermaid
flowchart LR
    DB[Local Room DB] --> CH[Change Tracking]
    CH --> P[Permissions and Projection]
    P --> Q[Durable Sync Queue]
    Q --> E[Encryption]
    E --> T{Transport}
    T --> F[Manual file]
    T --> B[Manual Bluetooth]
    T --> R[HTTPS Worker and D1]
    F --> V[Verification and Deduplication]
    B --> V
    R --> V
    V --> A[Atomic Apply to received projection]
    A --> ACK[Encrypted receipt queue]
    ACK --> Q
```

`domain/sync/` содержит чистые Kotlin-модели операций, версии, permission decisions, правила повторов и состояния. `data/sync/` содержит Room entities/DAO, транзакции, криптографию, HTTP и WorkManager adapter. UI подписывается на состояние и вызывает действия; сеть и расчёты не помещаются в composable. Для начала остаётся один Gradle app module: отдельный модуль/DI framework не требуется.

Предлагаемые компоненты: `SyncEngine` координирует durable work; `ChangeRecorder` пишет изменения; `ShareProjector` выдаёт только разрешённое представление; `SyncEnvelopeCodec` кодирует v2; `PartnerEnvelopeCrypto` выделяет существующие crypto operations; `SyncTransport` предоставляет send/fetch/acknowledgeStorage, а ручные адаптеры поддерживают только доступные операции. Отсутствие fetch у file transport — явная capability, а не фиктивный успех.

Пакет и его UUID создаются один раз на адресное устройство. Повтор по другому транспорту передаёт те же bytes, а не заново вызывает `prepareData()` с новым UUID. Общая E2E дедупликация не зависит от HTTP ID, имени файла или Bluetooth-сеанса. Несколько устройств получают отдельные delivery rows для одной логической операции.

### 4.1. Локальные таблицы (проект)

Таблицы ниже описывают целевой движок целиком. Этап 1 ввёл четыре служебные таблицы миграцией 6→7; [этап 2](RITELA_SYNC_STAGE_2.md) добавил пять локальных таблиц миграцией 7→8. Его source sequence не является авторским поколением: финальная authenticated version mapping/apply появится на этапе 3. Старые миграции и сообщения сохраняются. Миграции не читают vault, не генерируют identity и не создают автоматические grants.

| Таблица | Ключ и назначение |
|---|---|
| `sync_owner_state` | owner identity → author device, generation, следующий sequence. Generation меняется только подтверждённым переходом авторства, не произвольной новой установкой |
| `sync_record_versions` | `(owner, entityType, entityKey)` → revision, deletion flag, digest. Период = UUID; день = epoch-day; layout definitions = section/tag ID; forecasts = отдельная производная сущность |
| `sync_changes` | `(generation, sequence)` → op UUID, entity key, revision, type UPSERT/DELETE, минимальные данные для воспроизведения. Записывается атомарно с исходным изменением |
| `sync_links` | `(peerIdentity, peerDevice)` → protocol version, exact scope, scope epoch, consent flags, route reference, baseline revision, last applied/acknowledged position |
| `sync_outbox` | packet UUID + target device → immutable encrypted bytes, change range, scope epoch, state, attempts, nextAttempt, leaseUntil, error code; unique packet/target |
| `sync_inbox` | `(senderIdentity, senderDevice, generation, packetId)` → digest, receivedAt, result; хранит tombstones после очистки содержимого |
| `sync_received_records` | `(owner, generation, type, key)` → revision, projected value или tombstone; данные получателя отдельно от собственных periods/day_logs |
| `sync_conflicts` | конкурирующие/неожиданные версии и повреждённые разрывы, без удаления прежней применённой записи |

Журнал доступен только локально и очищается после checkpoint/подтверждений всех активных получателей. Не копировать туда полный дневник для каждого партнёра. При compact хранить record revisions/tombstones и floor допустимого cursor: клиент ниже floor обязан получить новый baseline, а не принимать старые пакеты.

### 4.2. Атомарная запись и первоначальный снимок

1. Мутация periods/day_logs/layout/import и соответствующий `sync_changes` выполняются в одной Room transaction. Изменения, не разрешённые адресату, остаются локальными. Outbox может материализоваться позже из сохранённого журнала, без зависимости от живого Flow collector.
2. При явном включении создаётся scope epoch и в согласованном snapshot БД фиксируется watermark W. Формируется только утверждённая проекция по состоянию W; изменения после W накапливаются отдельно.
3. Baseline состоит из manifest, ID частей и digest; применяется атомарно после проверки всех частей. Повреждение/пропажа части не удаляет старую проекцию. Передавать большие снимки порциями, не рассчитывать на одну строку D1.
4. После baseline идут ordered deltas; при отсутствии разрешённой проекции изменения для данного адресата можно пропускать. Поэтому номер глобального журнала не означает, что получатель должен получить все номера. Envelope несёт согласованный per-link sequence и base cursor, не требующий раскрытия запрещённых событий.
5. События смены даты/алгоритма могут менять разрешённый forecast без правки исходной записи: запланировать обновление производной сущности на устройстве владельца. Получатель не рассчитывает прогноз заново. `asOf`, `calculatedAt`, `algorithm` остаются явными.

### 4.3. Дельты, конфликты и удаления

V2 body включает protocol/capability version, logical operation ID, packet ID, identities/devices, полный контекст связи, owner generation, link sequence/base cursor, scope epoch, creation time и список операций с entity key/revision. Все эти поля аутентифицированы. Временная метка используется для отображения, но не выбирает победившую версию.

Для одного автора новая revision побеждает старую; равная revision с иным digest — конфликт/ошибка протокола. Пакет, пришедший раньше зависимостей, сохраняется в ограниченный буфер; запрашивается недостающее или baseline. DELETE — версионированный tombstone. Старый UPSERT не воскрешает удалённую запись. Удаление у получателя не отправляет DELETE владельцу. Для неизвестного авторского устройства/поколения — карантин и подтверждение, без last-write-wins по часам.

Разрешения применяются к проекции, включая названия разделов/тегов и вложения. Для разрешённого дня передаётся полная **разрешённая** версия его полей: снятый тег удаляется из проекции, запрещённые значения не используются даже в hash. Отсутствие категории в дельте не означает её удаление. Сужение scope передаётся отдельным REPLACE_SCOPE/RESET_VIEW и новым baseline, чтобы ранее разрешённое поле не оставалось случайно в актуальном представлении.

Текущий `partnerDataMessage()` с выбором latest received остаётся только legacy-путём. Новый UI читает материализованную Sync-проекцию, а история сообщений остаётся историей. Старый DATA, доставленный после новой дельты, не заменяет Sync-проекцию. Обратный переход в legacy возможен только явным действием с предупреждением об актуальности.

При восстановлении устаревшей локальной резервной копии обычный sequence может откатиться. Из непроверенного старого backup нельзя продолжать прежнее авторское поколение: нужна сверка checkpoint с доверенным устройством либо подтверждённый переход к новому generation и baseline. Relay не является доверенным источником медицинских версий. Полностью обнаружить откат всех локальных копий при отсутствии внешнего доверенного checkpoint невозможно; это ограничение фиксируется в документации восстановления.

### 4.4. Очередь и реальные статусы

Состояния: LOCAL_PENDING → READY → IN_FLIGHT → RELAY_STORED → RECIPIENT_APPLIED → OPENED; отдельно RETRY_WAIT, PAUSED, EXPIRED, CANCELLED, NEEDS_REVIEW. RELAY_STORED означает только приём зашифрованных bytes сервером. «Доставлено» появляется только по проверенному E2E ACK после durable commit у получателя. «Открыто» — только для контента, где есть явное действие просмотра; чтение DATA автоматически его не создаёт.

Получатель одной транзакцией сохраняет inbox ID/digest, применяет операции и создаёт устойчивую исходящую квитанцию. Лишь после commit разрешено удалять серверный пакет. Сбой после commit до HTTP DELETE приводит к повтору без повторного применения; потерянный ACK повторно ставится/переотправляется. ACK/OPENED не создают ответных E2E ACK, иначе получится бесконечная цепочка. Их серверные копии удаляются после durable import.

Для standalone и вложенного дудла заранее определить отдельный logical doodle ID и OPENED target; старые клиенты получают прежнее поведение. `receivedAt` фиксируется первым импортом и сохраняется при replay/рестарте. Отложенное открытие — UX-ограничение на устройстве: ciphertext уже расшифровывается получателем, а перевод часов/модифицированный клиент может обойти задержку. Не обещать криптографический time lock.

Повторы: exponential backoff с jitter, например от 30 секунд до 6 часов, сохранённые attempts/nextAttempt; учитывать Retry-After и сетевое состояние. 401/403 останавливают link до восстановления допуска, 413 требует переразбиения на уровне протокола, 429/5xx повторяются. Lease освобождается по timeout после смерти процесса. Истечение relay TTL не удаляет локальный оригинал: по восстановлении связи отправляется актуальный baseline/дельта с корректной версией. Очередь ограничена по bytes; coalesce допускается для ещё не отправленных superseded record updates, но не для пользовательских посланий и дудлов.

### 4.5. Смена телефона и запрет двух авторов

Device certificate подтверждает принадлежность личности, но не право быть автором. Авторство задаётся отдельным grant: `(ownerIdentity, generation, authorDeviceId, authorPublicKey, transitionId, previousGrantDigest, checkpoint)`. Generation — монотонный целый fencing token, а не timestamp/UUID. Каждый v2 пакет, изменяющий проекцию исходных записей владельца, подписан именно author device key, содержит generation и ссылку на grant; общего identity RSA private key недостаточно для продолжения прежнего авторского поколения. ACK/OPENED и самостоятельные послания/дудлы подписывает фактическое доверенное устройство отправителя: они не получают права менять медицинские записи и не требуют author key адресата.

Планируемая передача при доступном старом телефоне:

1. Старый автор переходит в TRANSFER_PENDING, прекращает создание авторских операций и отправку старой очереди; несохранённое/неподтверждённое фиксируется в checkpoint/черновиках. Передача должна явно охватить последние локальные изменения, а не только доставленные relay.
2. Старое и новое устройства подтверждают общий transition ID, checkpoint и новый device key. Grant поколения g+1 подписывают старый author key и новый device key; identity подпись связывает с прежней личностью. Новое устройство остаётся неактивным до commit перехода.
3. Для internet link Worker атомарно выполняет compare-and-set `(g, oldAuthorHandle) → (g+1, newAuthorHandle)` в записи authority со случайным owner handle. Два параллельных transfer из одного g не могут завершиться одновременно. Worker проверяет подписи прежнего и нового pseudonymous authority control/API keys, чей binding к author device keys проверяют партнёры внутри E2E grant; глобальные device keys, identity и medical checkpoint серверу не нужны. Все связанные маршруты используют один authority fence, а не независимые счётчики по партнёрам. Первоначальная привязка authority handle также входит в подтверждённый E2E bootstrap, не принимается по заявлению неизвестного устройства.
4. Проверка fence и резервирование upload происходят одной транзакцией. Уже накопленные пакеты g закрываются/удаляются; g+1 отправляет checkpoint baseline. Старое устройство становится RETIRED и не может вернуть себе ACTIVE из backup/identity export. После commit потеря ответа разрешается запросом результата transition ID, без второго увеличения поколения.
5. Получатели сохраняют максимальный подтверждённый generation/author key и digest цепочки, после перехода отвергают g и конкурирующие grants даже через файл/Bluetooth. Relay fence не заменяет E2E проверку полномочий. Получатель может принять новый baseline до его данных только после проверки/подтверждения grant.

Старый телефон может физически редактировать отключённую от сети копию; запретить это удалённо невозможно. Такие записи не становятся изменениями действующего автора: при возвращении старой установки они помещаются в локальное восстановление/черновик, без автослияния и отправки. Выбранная политика сохраняет данные для ручного разбора вместо silent loss. После transfer старые обычные v1 пакеты по-прежнему читаются как ручная история, но не изменяют активную v2 проекцию; v2-authority не приписывается старому формату.

Полностью офлайн-получатель, ещё не узнавший о смене автора, не может знать, что старый grant отозван. До получения перехода он может видеть устаревшую копию; строгая глобальная гарантия мгновенного отзыва потребовала бы онлайн-проверки или отказа от офлайн-приёма. Для ручного-only переноса нужна координация со старым устройством и передача grant каждому получателю. Нельзя заявлять, что локальная Room-миграция решает distributed fencing.

Если старый телефон потерян, восстановление RSA identity не активирует нового автора. RECOVERY_REQUIRED: закрыть старый relay-допуск через отдельно сохранённую recovery capability/операторскую процедуру пилота; новый grant с g+1 и новым device key явно подтверждают существующие партнёры. Не принимать восстановленный identity key как автоматическое доказательство, что старое устройство уничтожено. До подтверждения новым телефоном можно пользоваться локально, но он не публикует изменения прежнего автора. Подробный transfer/recovery протокол и его тесты относятся к этапам 3–5; этап 1 сохраняет только необходимые состояния/ссылки.

## 5. Криптография и модель угроз

Существующие RSA-OAEP/AES-GCM/EC-подпись обеспечивают основу конфиденциальности и аутентичности при сохранности ключей. Подпись header связывает IV, wrapped key, ciphertext и внешние адреса; body/header дополнительно сверяются. Но протокол v1 не имеет forward secrecy, sequence, expiry, permission epoch и полноценной защиты от rollback. Компрометация RSA identity private key позволяет расшифровать ранее сохранённые пакеты, даже если устройства различаются: `targetDevice` ограничивается проверкой приложения, а AES key обёрнут общим identity RSA.

Предлагаемый переход без перепривязки существующих контактов:

- Оставить identity ID/RSA и подтверждённые EC device keys корнями доверия. Новые возможности и transport keys передавать в подписанном E2E capability exchange через существующий файл/Bluetooth. Несовместимый клиент остаётся в ручном v1; не делать скрытый downgrade v2→v1.
- Для внешнего шифрования выбрать **HPKE через Tink Android**, а не собственную композицию ECDH/HKDF/GCM. Предпочтительная suite — `DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM`; подтвердить её работу выбранной версии на minSdk 26 до внедрения. Отдельные HPKE recipient keys сертифицируются прежним подтверждённым device key; API signing key остаётся отдельным P-256 ключом. В сертификате связать purpose, public key, device, link context, key version и expiry. Не переиспользовать signing key как DH key.
- Чтобы Worker не видел v1 `from/to/device`, Tink `HybridEncrypt` шифрует весь внутренний подписанный пакет для recipient device key. Формат ciphertext/prefix и context_info фиксируются по документации Tink и vectors; ключ/nonce/KDF библиотека формирует сама. Адреса и изменения остаются внутри; внешне — random route ID, delivery ID, transport key version и opaque ciphertext. Private keyset шифруется локальным Keystore wrapping key, без KMS/облачных аккаунтов. Сервер HPKE не расшифровывает и библиотека ему не нужна.
- Внутренний v2 header/body связывают полный контекст, protocol version, generation, scope epoch, sequence, target device, размеры/части manifest. Кодировать подписываемые bytes однозначно; не подписывать один JSON, а проверять его пересобранную версию. Strict UTF-8, ограничения длины/вложенности/числа операций до выделения больших буферов, allowlist алгоритмов/версий; без произвольных algorithm из входа.
- Проверять API-допуск до дорогостоящего приёма, E2E подпись до применения. Неизвестные ключи, несовпадение device/context, конфликт digest и устаревший generation не обновляют данные. Inbox ID/tombstones и watermark сохраняются после локальной очистки истории.

Так можно сохранить доверенные идентичности без обязательной новой привязки **существующих устройств**: они подписывают новые capabilities прежними ключами. Новому/восстановленному телефону всё равно необходимо отдельное одобрение. HPKE с постоянным recipient key не даёт forward secrecy против компрометации этого ключа: сохранённый ciphertext можно расшифровать позже. Ротация и удаление старых private keys ограничивают окно лишь при отсутствии их backup/копий; это не Double Ratchet. Защиту от отката, replay и подмены автора обеспечивает протокол поверх HPKE, не сама библиотека.

### 5.1. Сравнение вариантов и проверка безопасности

| Вариант | Применимость и решение |
|---|---|
| Собственный P-256 ECDH + HKDF + AES-GCM | Примитивы доступны, но собственными становятся KEM/KDF binding, формат, ошибки и nonce discipline. У первоначального предложения нет преимущества, оправдывающего этот риск; не выбирать |
| Tink Android / HPKE | Выбранная основа wrapper: стандартный механизм, Android API и меньше собственного crypto-кода. Поддержка Android начинается с API 24; minSdk проекта 26. Не предоставляет sender authentication/ratchet сам по себе; сохранить device signature и всю проверку trust/версий. Зависимость добавляется только на этапе 4, версия/size/R8 и suite проверяются заранее |
| libsodium sealed boxes | Компактная готовая схема anonymous public-key encryption; sender authenticity всё равно требует подписи. Для Android понадобятся оценка конкретного binding/JNI/ABI и сопровождения; переход не даёт полной FS против компрометации recipient key. Дополнительный native stack сейчас не обоснован |
| libsignal / Double Ratchet | Решает более широкую задачу защищённых сессий с ratchet. Потребует prekeys/session storage, адаптации trust/recovery и лицензии AGPLv3; upstream прямо не поддерживает использование вне Signal. Сейчас не выбирать ради одной обёртки. Рассматривать отдельно, если FS станет обязательным условием запуска |

Свойства Tink и рекомендация HPKE подтверждены [документацией hybrid encryption](https://developers.google.com/tink/hybrid), Android support — [setup](https://developers.google.com/tink/setup/java), особенности prefix/encapsulation — [wire format](https://developers.google.com/tink/wire-format). Альтернативы проверены по [libsodium sealed boxes](https://libsodium.gitbook.io/doc/public-key_cryptography/sealed_boxes) и [upstream libsignal](https://github.com/signalapp/libsignal). Это сравнение пригодности к Ritela, а не утверждение об аудите всей библиотеки.

До выпуска: pin артефакта и зависимостей, проверить security advisories/лицензию и размер APK, minSdk/R8; reference vectors библиотеки/HPKE и round-trip двух Android endpoints; изменённые ciphertext/context/key, неизвестная версия, key rotation и утрата Keystore; fuzz bounded parsers и неправильные подписи; сверка отсутствия identity/medical plaintext во внешнем envelope и логах. Kotlin↔Worker vectors нужны для API ECDSA signing/canonical request, **не для HPKE**, которое Worker не выполняет. Независимый review проверяет интеграцию, certificates, replay/fence/recovery; использование Tink не заменяет review протокола. При провале suite/compatibility gate вернуться к выбору готовой библиотеки, без автоматического fallback на самодельную криптографию.

Identity key rotation — отдельное подписанное старым и новым ключом событие с подтверждением пользователем и контактом; при потере/компрометации старого ключа — повторная проверка личности. Device/transport-key revocation закрывает очереди, отключает отправку, отменяет старые outbox и требует новой версии ключей. Запрещено молча восстанавливать отозванное устройство из старого backup.

Cloudflare всё равно видит IP и сетевые/TLS-метаданные, время, частоту и размер запросов, случайные route/device handles и API public keys, факт чтения/удаления, связь отправителя с очередями. Даже wrapper не скрывает граф активности и не исключает корреляцию. Размеры можно округлять до небольших buckets внутри лимита. Имена, email, телефон, категории данных, рисунки, identity keys и medical plaintext не нужны серверу. D1 encryption at rest не заменяет E2E; локальные Room bodies сейчас не зашифрованы этим wire-протоколом.

## 6. Cloudflare relay

### 6.1. Регистрация и API

Worker принимает HTTPS и хранит opaque bytes через D1 binding; Android не обращается к D1 REST API и не получает Cloudflare API token. Сервер не рассчитывает прогноз, не строит дневник и не ищет пользователей по имени.

Для закрытого пилота утверждены одноразовые activation tokens, создаваемые оператором; не встраивать общий регистрационный секрет в APK. После регистрации device handle принадлежит отдельному API signing public key. Identity/device certificate проверяется **партнёром внутри E2E**, серверу достаточно proof of possession API key; право авторства проверяется отдельно по §4.5. **Публичная самостоятельная регистрация не реализуется.** Отдельная будущая задача `SYNC-PUBLIC-REGISTRATION`: Sybil/abuse model, rate/byte quotas, доступный UX без аккаунта, расходы/операторское восстановление, тесты массовых регистраций и отдельное разрешение на запуск. Случайные UUID не решают эту задачу.

Очереди направленные, изолированы по паре устройств. Получатель создаёт route, выдаёт конкретному доверенному отправителю write capability и принимает его API public key через E2E bootstrap; отправитель подтверждает привязку. До этого отправка запрещена. Read/control доступны только API key владельца; write — назначенному sender key плюс capability, cancel собственного upload — тому же sender. Повторный bootstrap для новой route — допустимый небольшой ручной обмен, без повторного подтверждения уже доверенной личности.

| API (проект `/v1`) | Проверка и эффект |
|---|---|
| `POST /devices` | Одноразовая активация + proof of possession нового API key; выдаёт случайный handle. Погашение token атомарно |
| `POST /authorities/{handle}/transfer` | Проверка expected generation/grant digest и подписей старого/нового authority control keys, CAS поколения и сохранение результата transition ID. E2E author-device signatures проверяет получатель. Recovery без старого ключа — отдельная операторская процедура пилота, не обход этой проверки |
| `POST /routes` | Подпись получателя; owner API key, разрешённый sender API key, hash write capability, expiry, quotas. Ответ не раскрывает чужие маршруты |
| `PUT /routes/{route}/packets/{deliveryId}` | Sender signature + capability; immutable bounded bytes, digest, expiresAt. Повтор того же ID/digest возвращает прежний результат; другой digest → 409 |
| `GET /devices/{handle}/packets?cursor=…&limit=…` | Подпись owner; только его активные очереди, `expiresAt > now`, keyset pagination, общий byte limit. Cursor не даёт доступа к другому handle и не доказывает E2E порядок |
| `POST /devices/{handle}/ack` | Owner signature, bounded список delivery IDs; удаляет только durable-imported ciphertext, сохраняет короткую idempotency tombstone. Это storage ACK, не статус «Доставлено» |
| `DELETE /routes/{route}/packets/{deliveryId}` | Owner или назначенный sender для своих пакетов; idempotent cancel, включая запрос, обогнавший upload |
| `DELETE /routes/{route}` | Owner немедленно закрывает чтение/запись route; sender может закрыть своё направление. Bytes очищаются ограниченными batch |
| `DELETE /devices/{handle}` | Owner signature; закрывает все свои route и удаляет накопленные bytes/credentials; результат операции проверяется повторным запросом |

Подписывать domain/version, method, canonical path/query, body SHA-256, device handle, timestamp и случайный request ID. Проверять допустимое расхождение часов; явная коррекция часов не меняет медицинские revision. Для мутаций записывать request ID/result атомарно с эффектом и сохранять nonce tombstone до окончания replay window. Идемпотентный повтор получает прежний результат. GET не меняет очередь; replay валидного GET в коротком окне может повторно вернуть только тот же доступный ciphertext и не влияет на E2E состояние. Не писать D1 nonce на каждый пустой poll; для требования полного запрета replay чтений понадобится отдельный session/challenge протокол с иной оценкой writes.

Write capabilities — 256-bit random, передаются внутри E2E, сервер хранит только digest/HMAC с server pepper. В URL capability не помещать; bearer без sender signature недостаточен. Все DELETE/ACK ограничены владельцем/отправителем и конкретной route. Закрытие route и upload сериализуются условными SQL-операциями/транзакционным batch: upload не проходит после revoke, а уже прошедший помечается на очистку. Отмена ID оставляет tombstone, чтобы запоздалый upload не воскресил отменённый пакет.

### 6.2. Схема D1 (проект, не миграция)

| Таблица | Поля/ограничения |
|---|---|
| `activation_tokens` | token_hash PK, expires_at, redeemed_by; краткий TTL, cleanup |
| `relay_devices` | random handle PK, api_public_key, key_version, state, created_at; никаких имён/телефонов |
| `routes` | random route_id PK, owner_handle FK, sender_handle FK, capability_hash, state, expires_at, queued_bytes, queued_count, quota_bytes; индекс `(owner_handle, state)` |
| `packets` | monotonic relay_seq INTEGER PK, route_id FK, delivery_id, ciphertext BLOB, digest, bytes, created_at, expires_at; UNIQUE(route_id, delivery_id), индекс `(route_id, relay_seq)` и `(expires_at)` |
| `packet_tombstones` | `(route_id, delivery_id)` PK, digest/result, expires_at; отмены/удаления не хранят payload; срок не меньше срока допустимого upload/retry данного ID |
| `request_nonces` | `(device_handle, request_id)` PK, body_digest, result_code, expires_at; только мутации, индекс expiry |
| `author_fences` | random owner_handle PK, generation, active_author_api_key, grant_digest, transition_id; CAS-переход по §4.5 и проверка каждой авторской загрузки; без medical checkpoint/identity plaintext |

Использовать bound parameters, FK, CHECK для bytes/count/TTL и атомарное резервирование quota. `D1Database.batch()` выполняет statements транзакционно, но это не API интерактивных Room-транзакций: все conditional updates/insert и проверки числа изменённых строк следует спроектировать и проверить на гонках. Нельзя сначала отдельно проверить лимит, потом отдельно вставить пакет. [D1 batch API](https://developers.cloudflare.com/d1/worker-api/d1-database/).

Принять следующие product limits для пилота; это пределы приложения, а не Cloudflare:

| Объект | Ограничение и поведение |
|---|---|
| HTTP envelope | До 256 КиБ на фактические bytes тела после encoding/padding/wrapper; ciphertext BLOB тоже не более 256 КиБ. Content-Length недостаточно: проверять stream, decoded size и собранный payload |
| Обычная дельта | До 128 операций и до 96 КиБ projected plaintext, что наступит раньше; не более 64 КиБ на одну операцию, иначе ограниченный multipart |
| Новый internet Doodle | До 128 штрихов, 4000 точек всего, 4096 точек/штрих как legacy ceiling, caption до 240 символов, canonical doodle JSON UTF-8 до 96 КиБ. Послание сохраняет лимит 500 символов. Проверка одновременно по структуре и bytes, без молчаливого обрезания |
| Legacy рисунки | Существующие до 256 штрихов/20 000 точек, файлы/библиотека и задержки сохраняются без перекодирования миграцией. Для internet разрешить legacy рисунок до 1 МиБ logical plaintext через multipart; больший остаётся доступным локально/вручную, UI объясняет лимит |
| Multipart | Один logical объект до 1 МиБ, не более 32 частей; до 96 КиБ plaintext в части с повторной проверкой конечных 256 КиБ. Один незавершённый объект/route, до 2 МиБ временного буфера/peer, до 10 минут reassembly после получения первой части; затем очистка и повтор с тем же manifest. Parts не применяются до manifest/digest проверки. Размер до decrypt/после decrypt проверять отдельно |
| Baseline истории | Не более 3 МиБ projected plaintext на один атомарный baseline, до 64 частей; резервировать до 6 МиБ reassembly на receiver, один активный baseline/profile, timeout 24 часа. Если больше — явная ошибка/выбор меньшего диапазона после согласия, без скрытого усечения истории; local/manual данные не теряются |
| Fetch/ACK | До 512 КиБ на response, до 32 envelopes/страницу, до 128 ACK IDs; total bytes важнее количества |
| Relay retention/queue | TTL до 7 суток; route до 256 envelopes/8 МиБ, receiving device до 16 МиБ, весь pilot D1 до 300 МБ как soft admission cap с запасом к 500 МБ. Baseline заранее резервирует quota; недостаток → retry/pause |

Текущий `.ritela` до 3 МиБ продолжает работать вручную. Новые internet limits не меняют `Doodle`/`PartnerCodec` legacy validation и не инвалидируют сохранённые рисунки. Новые ограничения проверяет отдельный Sync serializer до enqueue; превышение предлагает упрощение копии/ручную передачу, не перезаписывает оригинал. Stage 1 фиксирует только константы/инварианты, без нового UI/кодека. Числа проходят нагрузочный gate перед пилотом; форматы не допускают decompression bombs (компрессия в MVP не нужна).

Просроченные пакеты исключаются из GET сразу по времени. Cron удаляет их небольшими indexed batches, обновляет quotas и чистит expired nonce/tombstone; после сбоя повторяет. D1 не имеет автоматического TTL для этих строк. Контролировать возраст backlog, bytes, rows_read/written, p95 CPU и 429/5xx без route IDs и содержимого в логах. Защита API: глобальный cap registrations, per-device/route/IP rate limits, byte quotas, reject oversized stream, bounded parsing, Retry-After. Память одного Worker isolate не годится для глобального надёжного rate limit: persistent quota решается в D1, а edge rate limiter/WAF — дополнительный слой с отдельно проверенными возможностями тарифа. Подделка IP не должна обходить device/route quota.

Отдельные dev/staging resources или локальный Wrangler D1; существующую пользовательскую базу не использовать как тестовую без проверки её назначения. Не переносить реальные медицинские пакеты в тестовые фикстуры. Не добавлять R2/KV/Durable Objects/FCM в MVP без измеренной необходимости.

### 6.3. Отзыв, удаление и резервные копии сервера

Три независимые операции должны иметь независимые результаты в UI и протоколе:

| Действие | Что гарантирует корректный клиент | Чего не гарантирует |
|---|---|---|
| Отозвать будущий доступ | Новые операции по отозванным категориям не создаются/не загружаются, auto scope уменьшается, epoch повышается, несогласованные queued bytes отменяются | Не удаляет уже переданные сообщения и не возвращает bytes уже отправленного запроса |
| Удалить из relay | После подтверждённого cancel/route revoke API больше не выдаёт соответствующие pending bytes; локально хранится результат или pending deletion при offline. Охватывает также части незавершённого объекта | Не удаляет уже скачанные копии и немедленно не стирает Time Travel history |
| Удалить локальную копию получателя | Сам получатель может удалить содержимое/актуальную проекцию, сохранив минимальные replay tombstones. По запросу владельца cooperating client может убрать категорию из текущего представления | Нельзя доказать удаление из модифицированного клиента, скриншота, экспорта, стороннего backup или ранее расшифрованных bytes |

У первых двух действий также нет гарантии отзыва уже скачанной информации. Для relay отдельно остаётся ограничение резервных копий ниже. Сужение scope очищает актуальную Sync-проекцию cooperating client после получения control event, но не переписывает историю старых DATA незаметно; очистка истории — отдельная явная операция получателя. Локальное удаление у получателя создаёт suppression marker до выбранного watermark/объекта: replay/resync не восстанавливает явно удалённое без его согласия. Новые разрешённые записи после watermark обрабатываются по отдельно показанному режиму, а не считаются восстановлением старого содержимого. Отзыв не удаляет оригинальные записи владельца.

При сужении scope или отзыве в локальной транзакции повышается epoch, запрещаются новые пакеты и отменяются неотправленные; dispatcher перепроверяет согласие непосредственно перед каждым upload. Для уже загруженных запрашивается cancel/закрытие route. При сохранении связи новый разрешённый baseline и REPLACE_SCOPE очищают текущую проекцию получателя. Если upload уже в сети, полностью предотвратить его доставку невозможно: закрытие route, отмена ID и новый epoch уменьшают окно, но не отзывают скачанные bytes.

Отключение всего Internet Sync немедленно прекращает сетевую работу. Если для очистки relay необходим запрос, UI предлагает явное действие «Удалить ожидающие данные с сервера и отключить»; при недоступной сети показывает pending deletion и срок TTL, без ложного сообщения об успешной очистке. Фоновая передача после выключения не продолжается ради скрытого cleanup. Локальное удаление профиля также заранее предлагает удаление relay-очередей; потеря credentials после удаления профиля оставляет серверу TTL cleanup.

Данные, уже расшифрованные/экспортированные получателем, отозвать технически нельзя. Удаление из активных D1-таблиц не обещает немедленного исчезновения из резервных копий: Time Travel хранит историю 7 суток на Free и 30 на Paid. После административного восстановления БД Worker должен оставаться закрытым до повторного применения revocations/deletions из отдельного защищённого operator recovery ledger; иначе восстановление оживит старые маршруты. Сроки хранения ledger и доступ оператора согласовать перед production. [D1 Time Travel](https://developers.cloudflare.com/d1/reference/time-travel/).

## 7. Android и UX

WorkManager добавляется для persistent one-time upload и периодического fetch с network constraint; Room — источник истины о заданиях. При создании записи/doodle сохраняется outbox и ставится unique work по profile ID; приложение при старте восстанавливает scheduler из БД. Название work не содержит medical values/имён. Для профиля off задания не создаются. Для первой версии синхронизируется выбранный профиль; при switch прежняя работа отменяется/дожидается завершения, старый database handle не используется новой сессией. Поддержку фоновой работы всех профилей можно добавить отдельно через registry, без использования active UI repository.

Периодический WorkManager имеет минимум 15 минут и не гарантирует точное время выполнения; Doze, ограничения OEM и force-stop могут задерживать доставку. В фоне — проверка раз в час с jitter и one-time upload после локального действия, без foreground service, wake lock и постоянного polling. [Android: work requests и constraints](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work).

### 7.1. Быстрая доставка при открытых приложениях

Для пилота выбирается **адаптивный foreground fetch**, без дополнительного облачного сервиса. После согласия — немедленный fetch при RESUMED/возврате сети и немедленная постановка upload после подтверждённой отправки; одна receive loop на выбранный профиль, объединяющая запросы всех партнёров. Открытая активная переписка: интервал 5 секунд первые 2 минуты, затем 15 секунд при действиях пользователя; после 2 минут без действий — 60 секунд. Остальные видимые экраны — 60 секунд. Burst сбрасывается не чаще одного раза за 5 минут; возврат на вкладку не обходит общий бюджет.

Token bucket на профиль: capacity 24 foreground fetch, refill 1 token/15 секунд; hard ceiling 264 запросов за скользящий час, включая initial/reconnect fetch. Для ручного «Проверить» при исчерпании показать ближайшее время вместо обхода бюджета. Максимум один запрос in flight; пропускать тики при медленном ответе. Empty fetch не создаёт D1 writes/nonces. Все действия проверяют global opt-in/peer state; STOPPED, switch/delete profile и выключение sync отменяют loop/HTTP, а не оставляют таймер. При 429/5xx применяется backoff и Retry-After без foreground override.

Ориентир при нормальной сети и обоих активных окнах — обнаружение послания/дудла за 5–15 секунд плюс upload/fetch/apply; на неактивной вкладке до минуты. Это целевое поведение для проверки пилота, не гарантия при перегрузке/экономии энергии. Таймер получателя необходим: факт отправки у одного телефона сам по себе не будит второй. Разрешённые фоновые задержки сохраняются.

WebSocket/SSE с серверным D1 polling лишь перемещает лишние чтения на сервер, поэтому не выбирать. Альтернатива при подтверждённой потребности в меньшей задержке/большем foreground usage — authenticated WebSocket **только пока приложение видно**, небольшой Durable Object с Hibernation для wake-up signal после commit, затем обычный fetch из D1. Это требует дополнительного сервиса, reconnect/backpressure и отдельного расчёта, потому сейчас не добавляется. [Cloudflare: WebSocket Hibernation](https://developers.cloudflare.com/durable-objects/best-practices/websockets/). FCM также остаётся отдельной будущей задачей.

### 7.2. Согласие и журналирование

Global internet flag отсутствует/false → никаких relay DNS/HTTP, регистрации, проверки endpoint, token redemption, foreground loop или periodic network work. Наличие activation token, старых grants, контакта или подготовленной Room-схемы согласие не заменяет. Worker/scheduler и каждый send/fetch повторно проверяют профильный flag; disable отменяет in-flight best effort, без обещания вернуть уже отправленный запрос. После восстановления backup или миграции интернет не включается автоматически.

В production логах только allowlisted технические коды/счётчики: без plaintext, рисунков/дат/тегов, ключей, capabilities, activation tokens, подписанных тел/URL query и полных исключений HTTP-клиента. Route/device/identity IDs не нужны логам; логи тела запроса/ответа, SQL bindings и debug network interceptors отключены. Проверки с synthetic sentinel data должны доказывать отсутствие значений в логах. `WorkManager.Data` содержит только технический profile/job reference; medical payload остаётся в локальной БД.

Будущая FCM интеграция допустима только как необязательный сигнал «есть новые пакеты», без sender name, текста, категории, дат цикла или рисунка. Signal не считается доказательством доставки и не заменяет authenticated fetch. Это дополнительный обработчик/метаданные и отдельная зависимость, поэтому сейчас Firebase не добавляется.

В настройках — «Синхронизация»: «Только вручную» по умолчанию; «Через интернет» включается отдельным согласием с объяснением relay/метаданных/задержек; «Автоматически рядом» только как будущий режим, без рабочего переключателя. В карточке личности остаётся «Чем поделиться», рядом отдельный выключенный по умолчанию auto-send, последнее подтверждённое обновление, очередь и история состава передач. Новый тег/раздел требует нового подтверждения; расширение ручного выбора не расширяет auto scope.

Включение показывает получателя и точные категории baseline. Doodles и послания после нажатия «Отправить» автоматически попадают в очередь при разрешённом internet link; черновик редактора не отправляется. Состояния пользователя: «Ожидает отправки», «Отправляется», «Ожидает подтверждения», «Доставлено», «Открыто», «Ошибка»/«Приостановлено». Технические IDs/nonce не показывать в обычном потоке. Последняя проверка сервера и последнее подтверждённое обновление различаются.

UI использует текущие `Theme.kt` tokens и существующие partner screens; все новые строки сразу EN/RU. При реализации применить `ui-quality-audit`, а при изменении отображения forecast/classification — `cycle-prediction-validation`. Текущий запрет пользователя на просмотр UI/PNG/эмулятора действует до нового явного запроса; не генерировать снимки локально ради визуальной проверки.

## 8. Лимиты и расчёт ёмкости

Проверено по первичным страницам Cloudflare 2026-10-15. Free: Workers 100 000 requests/day, 10 ms CPU/request, 128 МБ памяти; D1 5 млн rows read/day, 100 000 rows written/day, 5 ГБ всего, но **500 МБ на одну базу**, до 10 баз, строка/BLOB не более 2 000 000 bytes, до 50 D1 queries за invocation. Индексы и DELETE также увеличивают writes. Это общие лимиты аккаунта, их расходуют и другие приложения. [Workers limits](https://developers.cloudflare.com/workers/platform/limits/), [D1 pricing](https://developers.cloudflare.com/d1/platform/pricing/), [D1 limits](https://developers.cloudflare.com/d1/platform/limits/).

Следующий расчёт — модель нагрузки, а не обещание бесплатной эксплуатации. N пользователей, по одному устройству и одному адресату у каждого; 20 исходящих envelopes/user/day **включая E2E квитанции**, средний итоговый ciphertext 8 КиБ; 24 poll/day; для каждого envelope один upload и один storage ACK/DELETE request (без batching), плюс 4 control requests/user/day. Получается примерно `68N + 24` requests/day с hourly cleanup. Write budget оценён как 12–20 billable row writes на envelope за полный цикл с nonce/tombstone/index maintenance, то есть `240–400N/day`; это предварительный диапазон, который необходимо заменить замером D1 meta на реальной схеме. Reads ориентировочно `200–500N/day` при индексах, без scan всех packets.

| Пользователи | Worker requests/day | D1 writes/day (оценка) | D1 reads/day (оценка) | Ciphertext за 7 дней без получения | Оценка Free |
|---:|---:|---:|---:|---:|---|
| 2 | 160 | 480–800 | 400–1 000 | 2,29 МБ | Подходит для закрытого пилота, CPU надо измерить |
| 100 | 6 824 | 24 000–40 000 | 20 000–50 000 | 114,69 МБ | Вероятно помещается, с запасом под metadata и CPU |
| 1 000 | 68 024 | 240 000–400 000 | 200 000–500 000 | 1,147 ГБ | Не помещается по writes и одной Free D1 |
| 10 000 | 680 024 | 2,4–4 млн | 2–5 млн | 11,469 ГБ | Не помещается по requests/writes/storage |

МБ/ГБ в таблице десятичные; payload `N × 20 × 8192 × 7`, без индексов, tombstones, устройств и SQLite overhead. Семь дней — offline backlog stress, не обычное steady-state: при удалении за сутки payload в семь раз меньше. Первичная передача истории и сложные дудлы могут быть гораздо больше 8 КиБ. При двух устройствах/нескольких партнёрах fan-out умножает uploads, bytes и квитанции. Poll каждые 15 минут даёт уже `140N + 24` requests/day; при N=1000 превышает Free даже без foreground activity. Minute polling одного устройства — 1440 пустых запросов/day, поэтому его нельзя включать постоянно.

100 пользователей — разумный пилот, а не верхняя гарантированная ёмкость: 10 ms CPU могут оказаться первым ограничением при подписи, хешировании и больших envelopes. Cron на Free также ограничен CPU; cleanup должен быть мелким. Перед публичным запуском снять p95 CPU и rows_read/written на пустом poll, insert, import ACK, retry, cancel и expiry sweep. Держать запас минимум 30% и останавливать приём до hard quota с понятной ошибкой и сохранённой локальной очередью.

Foreground добавляется к этой модели отдельно. При 30 минутах активной переписки/user/day: 24 polls за первые 2 минуты и 112 за оставшиеся 28 минут с интервалом 15 секунд, всего 136 дополнительных запросов. При N=2/100/1000/10000 суммарно около 432 / 20 424 / 204 024 / 2 040 024 Worker requests/day. В верхнем бюджете 264 дополнительных fetch/user/hour — ещё выше. Это консервативно добавляет foreground к фоновым polls, даже если часть hourly ticks будет объединена. При indexed auth/route lookup примерно 2–6 row reads на пустой foreground fetch: ещё 272–816N reads/day в сценарии 30 минут. Точное число зависит от количества routes и измеряется; никаких writes на пустой fetch. Сценарий 1000 пользователей уже превышает Free requests и без роста message traffic; редкие фоновые проверки не отменяют цену активного чата.

По storage при 100 пользователях один ежедневный doodle по 96 КиБ вместо среднего 8 КиБ добавляет за семь суток около 63,1 МБ; один legacy doodle 1 МиБ/user/day добавил бы около 728,3 МБ и превысил бы Free-базу. Структурный max не является ежедневной квотой: пилот ограничивает новые uploads, например 10 doodles и 1 МиБ суммарных envelope bytes/device/day, плюс отдельная операторская квота первого baseline. ACK/control имеют отдельную малую квоту, чтобы заполненная исходящая очередь не блокировала очистку. Общие route/device/database caps обязательны независимо от per-day quota. Число частей, индексы, nonce/tombstone cleanup и author fence входят в измеряемые расходы; 256-КиБ лимит пакета не означает разрешение заполнить все очереди до максимума бесплатно.

Paid D1 допускает 10 ГБ на базу; сценарий 10 000 пользователей с семидневным backlog уже превышает и этот предел до overhead. Это повод уменьшать retention/объём или проектировать несколько tenant shards, а не обещать бесконечный рост одной базы. Переход на Paid и стоимость согласовать по фактическим метрикам и действующим тарифам на дату внедрения; сейчас платные ресурсы не включаются. [D1 limits](https://developers.cloudflare.com/d1/platform/limits/).

## 9. Этапы реализации после согласования

Пути ниже — предлагаемые новые файлы, если компонент отсутствует сейчас. Каждый этап заканчивается отдельными небольшими проверенными коммитами, документацией и сохранением старого ручного обмена. Точные версии новых зависимостей проверяются перед добавлением; текущие версии не меняются ради плана.

### Этап 1. Модели, инварианты и миграция

Конкретный объём определяет [RITELA_SYNC_STAGE_1.md](RITELA_SYNC_STAGE_1.md): чистые модели/инварианты, четыре неактивные служебные таблицы, Room 6→7, регистрация миграции и тесты сохранности. Без journal/outbox/inbox, key generation/backfill, transport и нового wire format. Зависимости остаются прежними. Миграция сохраняет старые таблицы/JSON/файлы/ключи и создаёт только disabled/uninitialized state. Журналирование существующих DAO и backfill revisions откладываются до этапа 2 с transactional baseline.

### Этап 2. Change Tracking и устойчивый Sync Engine

Реализован по последнему указанию пользователя; детали — [RITELA_SYNC_STAGE_2.md](RITELA_SYNC_STAGE_2.md). Файлы: `domain/sync/SyncEngine.kt`, `data/sync/SyncJournal.kt` (ChangeRecorder/DAO), `SyncQueueStorage.kt`, `SyncQueueRepository.kt`, `ShareProjector.kt`; атомарные wrappers в PeriodDao/DayLogDao/JournalDao охватывают и BackupRepository.import. Room 8, snapshot watermark, per-link cursors/projection digests, очередь, lease/backoff, coalescing и compaction. Только обязательные encoder/transport порты с fake реализациями в тестах, без production adapter/сети. FORECASTS требует явного owner snapshot provider, крупный baseline блокируется до chunking этапа 3. Зависимости прежние Room/Coroutines, внедряемые time/jitter для тестов.

Тесты: kill/reopen между каждым commit/dispatch, запись во время baseline, bulk add, снятый тег/пустой день/DELETE, import, новое разрешение/новый тег, ошибки сериализации, очередь двух профилей. Готовность: ни одно подтверждённое изменение не теряется, запрещённые поля не попадают в payload, restart восстанавливает работу. Риск: рост журнала — compaction только с checkpoint/floor. Старые ручные API работают.

### Этап 3. V2, приём и актуальная проекция

Локальная часть реализована: [RITELA_SYNC_STAGE_3.md](RITELA_SYNC_STAGE_3.md). Room 9, отдельная received projection/inbox, manual capabilities/encoder с существующим JCA suite, author approval/retirement, chunks/gaps/immutable IDs/ACK и embedded Doodle OPENED. Интернет-HPKE/relay fence и активация преемника остаются этапам 4–5; UI/setup и автоматическая публикация — следующим этапам. Старые v1 файлы/RFCOMM и данные сохранены.

Файлы: `data/sync/SyncEnvelopeCodec.kt`, `SyncApplyRepository.kt`, `domain/sync/SyncConflict.kt`, `data/PartnerRepository.kt`, `PartnerSnapshot.kt`, `PartnerStorage.kt`, `domain/Partner.kt`, `docs/PARTNER.md`. Реализовать capability negotiation, baseline chunks, immutable packet IDs, per-link sequence, UPSERT/DELETE, scoped reset, ACK/OPENED и per-device status. Подготовить `SyncTransport` и ручные адаптеры. Полученные записи хранятся отдельно. Зависимости: текущие JCA/Room; отдельная библиотека сериализации только при доказанной необходимости.

Тесты: одинаковый пакет file+Bluetooth, reordered/duplicate/missing parts, duplicate ID с иным digest, старый snapshot после delta, tombstone replay, scope narrowing, два устройства, restore старого backup, ACK до/после удаления, вложенный doodle OPENED и delayed receivedAt. Готовность: порядок доставки не откатывает проекцию, собственные записи не меняются, v1 остаётся читаемым и не перехватывает активную v2 проекцию. Риск: старый клиент не понимает v2 — явная несовместимость и ручной v1 без скрытого downgrade.

### Этап 4. Защищённый relay и протокол доступа

Локальная реализация завершена: [RITELA_SYNC_STAGE_4.md](RITELA_SYNC_STAGE_4.md), операторские ограничения — [relay README](../../relay/README.md). Рабочая D1 и deploy не затронуты. Измеренный локальный upload+ACK уже даёт 40 writes/envelope до GC; приведённый выше диапазон 12–20 занижен. Для 100 участников/20 envelopes в сутки это ≥80 000 writes до cleanup/control, без требуемого запаса. Ориентир пилота теперь до 50 участников, до проверки deployed meta/CPU; технический cap 100 регистраций не обещает Free ёмкость. Таблица выше сохраняется как исходная модель, её write-оценки не использовать для допуска нагрузки. Production CPU/security review/recovery rehearsal остаются gates перед публикацией.

Файлы: будущий `relay/src/index.ts`, `auth.ts`, `routes.ts`, `packets.ts`, `cleanup.ts`, `relay/migrations/0001_relay.sql`, `relay/wrangler.jsonc`, `relay/test/`; Android `data/sync/PartnerEnvelopeCrypto.kt`, `RelayKeyVault.kt`. Уточнить canonical request bytes, ECDSA DER↔WebCrypto representation, Tink HPKE vectors, transport certificates и внешний wrapper. Реализовать local Worker, author fence, activation tokens, auth/quotas, идемпотентность, TTL/revoke/delete; привязать существующий D1 ID только после проверки назначения базы. Зависимости: Tink Android на клиенте; dev-only TypeScript/Wrangler и Workers test runner с локальной D1; pin lockfile. Серверный runtime — WebCrypto/D1, без framework по умолчанию.

Тесты: Kotlin↔Worker API-signature vectors и Android↔Android HPKE vectors, forged sender/read/control, guess route, replay mutations, concurrent quota uploads/revoke/cancel-before-upload, simultaneous author transfers и old-generation upload, expiry, 413/429, SQL parameterization, rollback batch, восстановление D1 с revoke ledger, отсутствие payload/capability в логах. Готовность: security review threat model и crypto encoding, измеренные CPU/rows/bytes; отдельный staging smoke test разрешается перед облачным выполнением. Риски: 10 ms CPU, Sybil, race cleanup, transport key loss. Production deploy не входит в одобрение локальной реализации автоматически.

### Этап 5. Android HTTP и фоновое выполнение

Android runtime подключён локально: [RITELA_SYNC_STAGE_5.md](RITELA_SYNC_STAGE_5.md).
Room 11 с protected config/routes, immutable HPKE, registration, dispatch,
transactional apply/storage ACK/cursor, E2E receipts и WorkManager. Есть явное
согласие/пилотная активация/manual setup. INTERNET/ACCESS_NETWORK_STATE добавлены,
cleartext запрещён; до consent relay не вызывается. Origin задаётся оператором
при сборке; в gradle.properties задан предоставленный оператором
https://ritela-relay.zabvenen.workers.dev, empty override отключает подключение.
Deployment выполнен оператором отдельно; мы проверили только unauthenticated
HTTPS 401 без регистрации и отправки данных. Полная
двухсторонняя цепочка проверяется synthetic exchange без внешней сети; реальный
staging smoke test остаётся отдельным действием.

Файлы: `data/sync/HttpRelayTransport.kt`, `SyncWorker.kt`, `SyncScheduler.kt`, `UserProfiles.kt`, `RitelaApplication.kt`, `AndroidManifest.xml`, `gradle/libs.versions.toml`, `app/build.gradle.kts`; `scripts/verify-apk.ps1` и документы приватности, если они проверяют прежнее отсутствие INTERNET. Добавить INTERNET/ACCESS_NETWORK_STATE по необходимости, WorkManager, HTTPS allowlist endpoint и запрет cleartext; при достаточности использовать платформенный `HttpsURLConnection` в IO с timeout/cancellation, иначе обосновать один HTTP client. Не добавлять общий сетевой слой ради единственного endpoint.

Тесты: process death, Doze/constraints через scheduler test driver, отсутствие сети, timeout/429/5xx, повреждённый ответ, partial fetch, смена/удаление профиля во время работы, выключение global/peer до upload, отсутствие любого запроса до opt-in. Готовность: запись офлайн работает, queue переживает restart, HTTPS пакет подтверждён только после E2E apply. Риск: background delays и отмена blocking IO — обрабатывать закрытием соединения. Старый manual mode без scheduler продолжает работать.

### Этап 6. Автоматическая доставка записей, посланий и дудлов

Подтверждённые send intents и квитанции [этапа 6](RITELA_SYNC_STAGE_6.md)
подключены к runtime этапа 5: DATA/DOODLE/ACK/OPENED доставляются после явного setup.
Следующим шагом подключены automatic journal preparation/forecast refresh,
adaptive foreground receive с сохраняемым budget, авторское подтверждение и
обновления в чат. Реальный pilot/physical Doze и независимый review ещё нужны.

Файлы: `PartnerViewModel.kt`, `PartnerRepository.kt`, `domain/SavedDoodles.kt`, `data/sync/SyncQueueRepository.kt`, `SyncApplyRepository.kt`. Подключить подтверждённое send action к очереди, автопроекцию разрешённых изменений, forecast refresh, E2E receipts и delayed-open. Draft и saved doodle library не отправляются автоматически. Зависимости: уже добавленные, без FCM.

Тесты: серия правок офлайн/удаление до отправки, действие «Отправить» ровно один раз после restart, повторы по двум транспортам, delayed-open с часами/минутами, дудл в DATA, потерянный ACK, expiry и resync. Готовность: нет ручного создания файлов при включённом internet link, получатель не видит запрещённого, статус не опережает подтверждение. Риск: дублирование старого ручного send flow; один общий pipeline и явное направление каждого сообщения.

### Этап 7. Настройки и прозрачность

В [этапе 7](RITELA_SYNC_STAGE_7.md) доступны manual mode/metadata queue/composition/
cancel/pause/disable/privacy и явный Internet consent/activation с manual setup.
Без build origin включение недоступно. Мастер приглашения включает setup,
добавлены auto scope opt-in и recipient author approval, нормализованные причины
ошибок/повтор и queued/STORED/E2E статусы. Last-success/server deletion UX и
полный transfer/recovery остаются открыты; этап целиком не завершён.

Файлы: `ui/SettingsScreen.kt`, `PartnerScreen.kt`, `PartnerSharing.kt`, `PartnerSecuritySettings.kt`, `PartnerConversation.kt`, `PartnerFormatter.kt`, будущие `SyncSettings.kt`/`SyncQueueScreen.kt`, `res/values/` и `values-ru/`. Реализовать независимые consent/scope/auto toggles, baseline preview, состав/очередь/последний успех, ошибки и удаление relay-данных. Применить `ui-quality-audit`; новых UI библиотек не требуется.

Тесты взаимодействий: opt-in/cancel, смена личности в группе, расширение ручного scope без расширения auto scope, revoke offline, выключение с pending deletion, narrow/large text EN/RU по semantics/layout проверкам. Готовность: пользователь различает создание файла, хранение сервером и E2E доставку; отсутствуют обещания удалённого стирания уже прочитанного. Визуальная проверка только после отдельного разрешения пользователя; до неё limitation отмечается явно.

### Этап 8. Надёжность, нагрузка и подготовка выпуска

Начата [локальная проверка этапа 8](RITELA_SYNC_STAGE_8.md): recovery matrix,
bounded tamper/malformed corpus, atomic ACK и cursor burst, локальные D1 budgets,
relay CI и исправление устаревшего запрета network permissions в artifact checks.
Это не завершение всего этапа: physical install/update и production E2E, CPU/quotas,
independent review и незавершённые функции этапов 6/7 остаются открыты.

Файлы: `app/src/test/kotlin/app/ritela/*Sync*Test.kt`, будущие `relay/test/`, CI workflows, `docs/TESTING.md`, `docs/PARTNER.md`, документы backup/privacy, `docs/STATUS.md`. Выполнить recovery matrix, protocol fuzz/property tests с bounded payloads, профилирование CPU/bytes/row metrics и пилот на двух устройствах после отдельного запроса на устройство. Тестовые данные синтетические, production содержимое не собирается.

Критерии: migration/storage tests, tamper/replay/out-of-order/revoke и cross-transport tests проходят; форматирование, debug build и lint без ухудшений; отсутствие plaintext/secret logging; проверена install/update и backout стратегия (отключить Sync, сохранить данные и историю; не destructive downgrade Room). Android-изменения проверяются через `ritela-android-check` с действующими ограничениями пользователя. UI/эмулятор не запускаются до нового запроса. Релиз допускается после отдельного решения по нерешённым security/UX ограничениям; включение платного тарифа и публикация отдельно.

## 10. Что требуется от владельца проекта

Архитектура принята за основу: single-author с fencing/явным recovery, быстрая
foreground доставка, редкая фоновая проверка, TTL 7 суток, закрытый пилот с
activation tokens, Tink HPKE и отсутствие полной forward secrecy. Последний запрос
возвращает к этапу 5 и завершает Android delivery runtime. Cloud deployment,
платный тариф и передача реальных медицинских данных этим не разрешаются.

Перед будущим staging: сообщить имя и назначение уже созданной D1, account ID и тариф, выбрать workers.dev/свой домен и разрешённое размещение данных, выделить тестовую среду, утвердить retention и предел затрат. Доступ настраивается локально через Wrangler/OAuth либо scoped deployment token с минимальными правами Workers/D1; секреты не присылать в чат и не коммитить. ID базы уже известен. Android не получает операторские Cloudflare credentials.

Перед production: получить результаты независимого security review, определить поддержку потерянных ключей/телефона, сохранить шифрованный identity export отдельно от медицинского backup, утвердить operator recovery ledger и действия при quota outage. Включение Sync добровольно у каждого участника; разрешение владельца проекта на deploy не является согласием пользователей на передачу их данных.

## 11. Альтернативы и открытые вопросы

| Вариант | Оценка |
|---|---|
| Загрузить нынешний `.ritela` в HTTP без нового движка | Подходит только для ручного relay-прототипа; не решает порядок, удаления, queue и согласия. Не выбирать как реализацию Sync |
| Полные снимки при каждой правке | Проще дельт, но увеличивает утечки/объём, размер пакетов и риск отката. Использовать только baseline/resync |
| Дельты с одним автором | Рекомендуется для первой версии: однозначные revisions и управляемое восстановление |
| Multi-writer/CRDT сейчас | Значительно усложняет медицинские конфликты и импорт. Отложить до явной потребности; версии первой схемы должны уметь обнаруживать конкуренцию |
| Один RSA envelope без внешнего wrapper | Меньше кода, сохранение существующего trust; сервер видит stable IDs и нет device-key cryptographic isolation. Только после явного принятия ограничений |
| Готовая E2E библиотека | Выбран Tink HPKE для wrapper; ratchet — отдельная задача, если FS станет обязательной. Подробное сравнение в §5.1 |
| FCM/WebSocket/частый polling | В пилоте adaptive foreground polling с бюджетом; фон раз в час. FCM/foreground-only WebSocket+DO — последующие варианты по метрикам |

Открыто перед соответствующими этапами: независимая экспертиза HPKE-интеграции и transfer/recovery/rollback; фактический CPU/rows/bytes профиля пилота; validation размеров на legacy рисунках; доступность Cloudflare в целевых сетях; требования к региону и резервным копиям. Политика обработки данных требует отдельного review. Публичная регистрация и автоматический Bluetooth/BLE не входят в текущую реализацию; существующий ручной RFCOMM остаётся.

Первоначальный аудит проверил Room v6, ProfileSession, migration tests, Doodle/library constraints и первичные источники Tink/libsodium/libsignal/Cloudflare. По разрешённым этапам добавлены Room 7/8/9, локальные journal/queue/receive и compatibility tests; точный итог Android/storage checks фиксирует STATUS. Аккаунт Cloudflare, рабочая D1 и будущий сетевой wrapper не проверены, UI/PNG/эмулятор не запускались. Следующая отдельная граница после локального этапа 3 — этап 4: relay, Tink wrapper и server fencing, без автоматического deploy в рабочую D1.
