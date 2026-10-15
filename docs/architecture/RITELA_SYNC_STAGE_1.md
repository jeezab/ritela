# Ritela Sync — рабочий план этапа 1

2026-10-15. **Пользователь разрешил этап 1 командой «Приступай к этапу 1»; локальные модели и Room 7 реализованы.** Основание — [уточнённая архитектура](RITELA_SYNC_PLAN.md). Документ сохраняет согласованный объём и критерии; фактические результаты проверок фиксируются в [STATUS](../STATUS.md). Следующие этапы не входят в это разрешение.

Результат этапа: приложение открывает существующие профильные базы после явной миграции Room 6→7, сохраняя прежние данные и ручной обмен. Новые модели выражают согласие, single-author generation, версии записей и адресные sync-настройки, но не подключены к отправке или изменению медицинских записей.

## 1. Точная граница

Включено: чистые Kotlin-модели/инварианты и лимиты; четыре служебные Room entity/DAO; `MIGRATION_6_7`; регистрация миграции; экспорт схемы 7; тесты моделей, миграции, файловой/профильной совместимости и регрессии ручного протокола.

Журнал изменений, backfill revisions, outbox/inbox/received projection, scheduler, HTTP, activation flow, Tink/key generation, transfer/recovery execution, новый wire format и UI относятся к последующим этапам. Нет новых зависимостей/permissions, Cloudflare/Wrangler/FCM, автоматического BLE/Nearby. Stage 1 не даёт приложению сетевых возможностей. `Doodle`/`PartnerCodec` legacy limits и ручной RFCOMM остаются прежними.

Версия Room 7 реализована явной MIGRATION_6_7; новые изменения схемы используют следующую миграцию, без редактирования уже сохранённых схем/миграций.

## 2. Файлы и изменения

Пути исходников ниже относительно корня репозитория. Перечисленные новые исходники и schema 7 созданы в этом этапе.

| Файл | Конкретная работа |
|---|---|
| `app/src/main/kotlin/app/ritela/domain/sync/SyncModels.kt` | Value objects `OwnerGeneration`, `RecordRevision`, `AuthorAuthority`, `SyncLinkPolicy`, `SyncConsent`, `SyncRecordKey`; enum entity types/authority states. Чистый Kotlin без Android/Room/JSON |
| `app/src/main/kotlin/app/ritela/domain/sync/SyncInvariants.kt` | Проверки согласованности состояния, монотонности поколения/revision, scope/transport consent; формат generation/sequence и overflow guard. Только функции/значения, без реального переключения автора |
| `app/src/main/kotlin/app/ritela/domain/sync/SyncLimits.kt` | Константы структурных/byte limits из основного плана. Новый internet Doodle budget проверяет числовые параметры/byte count, не меняет конструктор старого `Doodle` и не сериализует его |
| `app/src/main/kotlin/app/ritela/data/sync/SyncEntities.kt` | Четыре entity, точно по §4, с SQL default values через `@ColumnInfo`; без секретов/medical payload. Persisted flags представлены Int 0/1 с проверкой mapper, domain использует Boolean; так corrupt raw 2 не превращается незаметно в true |
| `app/src/main/kotlin/app/ritela/data/sync/SyncStateDao.kt` | Чтение singleton state/links/versions и транзакционный `ensureDefaults()` с INSERT IGNORE. Условные операции handover и изменение consent намеренно оставлены последующим этапам, чтобы не создавать непроверенный activation API |
| `app/src/main/kotlin/app/ritela/data/sync/SyncStateStore.kt` | Локальный read/initialize defaults в транзакции; raw → validated domain metadata. Нет методов opt-in/activate/transfer и вызовов из существующих edit/send paths. Ошибки metadata, неизвестный protocol/scope или непроверенный authority дают типизированный отказ; SQL/storage ошибки не подавляются |
| `app/src/main/kotlin/app/ritela/data/RitelaDatabase.kt` | version 6→7, новые entities, accessor `syncState()`, явная `MIGRATION_6_7`. Миграции 1→6 сохранить текстуально |
| `app/src/main/kotlin/app/ritela/data/UserProfiles.kt` | В `ProfileSession` добавить `MIGRATION_6_7` к имеющемуся `.addMigrations(...)`. Имена `ritela.db`/`ritela-<UUID>.db`, vault/preferences paths и жизненный цикл не менять; не создавать network/keys при миграции |
| `app/schemas/app.ritela.data.RitelaDatabase/7.json` | Экспортировать KSP/Room после изменения entities. Не писать вручную и не менять схемы 1…6. Сверить fresh/migrated structures, defaults и индексы |
| `app/src/test/kotlin/app/ritela/SyncModelsTest.kt` | Доменные инварианты/границы без Robolectric |
| `app/src/test/kotlin/app/ritela/SyncMigrationTest.kt` | File-backed SQLite версии 1…6 → Room 7, сравнение содержимого/схемы, failure rollback и повторное открытие |
| `app/src/test/kotlin/app/ritela/SyncCompatibilityTest.kt` | Изолированные профили/файлы/vault, legacy pairing/data/doodles/receipts после upgrade; реальные synthetic encrypted packets |
| `app/src/test/kotlin/app/ritela/SyncStateStoreTest.kt` | Fresh defaults, транзакционность, rejects invalid raw state и восстановление после reopening; отсутствие activation из сохранённого legacy grants |
| `app/src/test/kotlin/app/ritela/DayLogBackupTest.kt` | Дополнить обе migration chains `MIGRATION_6_7` (сейчас кончаются 5→6); сохранить прежние assertions для backup и старых схем |
| `app/src/test/kotlin/app/ritela/PartnerReliabilityTest.kt` | Дополнить миграционный builder 4→5→6 до 7; сохранить проверки остальных таблиц/дудлов/restore |
| `app/src/test/kotlin/app/ritela/PartnerMessageDeletionTest.kt` | Дополнить file DB helper chain 5→6 до 7; сохранить assertions hidden/tombstone/late receipts; название migration-теста уточнить как 5→current, если он теперь проверяет эту цепь |
| `docs/DATA_FORMAT.md`, `docs/TESTING.md` | Описать четыре таблицы и отсутствие wire/backup changes; команды целевых тестов и ограничения |
| `docs/STATUS.md`, `docs/PROJECT_MAP.md`, `docs/DECISIONS.md` | По завершении реализации записать фактические schema/checks, неизменность legacy, оставшиеся риски/следующее действие |

Перед редактированием повторить `rg -n 'MIGRATION_[1-9]_|Room.databaseBuilder' app/src`, чтобы не пропустить новый builder. In-memory test DB уже создаётся в текущей версии; ему не нужна искусственная миграция, но нужны disabled defaults при первом обращении через store.

## 3. Инварианты

1. Profile UUID, identity ID и device UUID — разные идентификаторы. Имена контактов/профилей не являются ключами и не используются в файловых путях или authority. Все sync-таблицы физически находятся в БД конкретного профиля.
2. Нет строки или `internetEnabled=0` → согласие отсутствует. `automaticEnabled` отдельного link не может перекрыть global off. Старые `grants`, `lastSelection`, `exchangeEnabled` и `mutualConfirmed` не являются consent на internet. Миграция не копирует их в автоматический scope.
3. UNINITIALIZED: generation=0, lastSequence=0, owner/author/grant/transition отсутствуют. ACTIVE: generation>0, owner identity и author device/key binding заданы, присутствует подтверждённая цепочка grant. TRANSFER_PENDING замораживает публикацию; RETIRED не возвращается к ACTIVE того же поколения; RECOVERY_REQUIRED не публикует. На этапе 1 production может создать только UNINITIALIZED, остальные состояния пока лишь типы/валидируемые synthetic fixtures.
4. Generation — положительный Long для активного автора. Разрешённый переход строго g→g+1 с expected old generation/device/grant digest; `Long.MAX_VALUE` вызывает явную ошибку, не wraparound. Два grants одного поколения с разными author/digest — конфликт, не выбор по timestamp. Это контракт для будущего исполнения; stage 1 не подтверждает безопасность distributed fencing.
5. Существующая запись не получает фиктивную revision из `updatedAt`. Отсутствие `sync_record_versions` означает unversioned. После будущей инициализации row имеет generation>0, revision>0; DELETE — та же версия с isDeleted=true. UUID периода/epoch-day не меняются. Received projection никогда не размещается в собственных `periods/day_logs`.
6. Link имеет конкретные peer identity/device; объединённый `PartnerContact` не объединяет permissions или author grants. Epoch=0, protocolVersion=0, scopeJson=NULL и automaticEnabled=0 означают отсутствие Sync-согласования. Неизвестные protocol/state/несогласованные NULL отвергаются fail-closed; raw enum text не превращается в разрешение по умолчанию.
7. Scope нельзя расширить при миграции/появлении нового тега. Пока stage 1 не обрабатывает scope JSON, nullable значение не декодируется в `ShareScope` с полным доступом. Проверки принятого `ShareScope` используют существующий `PartnerSharing.remembered()/outgoing()`, не второй несовместимый нормализатор.
8. В metadata нет medical values/plaintext body и private keys. Digests будущих projected records не вычисляются миграцией, не логируются и не выгружаются наружу. Authority grant — подписанный публичный материал, не экспорт приватного ключа; конкретная криптопроверка относится к последующим этапам.
9. Физическое удаление старых medical rows/сообщений, изменение hidden flags, рассылка дудлов и file re-encoding не являются действиями миграции. Проверка stale legacy packet не приписывает ему полномочия v2 автора.

## 4. Room 7: точное изменение схемы

Добавлены **только** следующие таблицы. Имена camelCase совпадают с entity properties. Типы и DEFAULT в entity/миграции/fresh schema совпадают; schema 7 экспортирована Room/KSP. SQL ниже соответствует MIGRATION_6_7; на устройстве она применяется при открытии соответствующей профильной базы.

```sql
CREATE TABLE IF NOT EXISTS sync_profile_state (
    id INTEGER NOT NULL,
    internetEnabled INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY(id)
);

CREATE TABLE IF NOT EXISTS sync_authority (
    id INTEGER NOT NULL,
    ownerIdentity TEXT,
    authorDevice TEXT,
    generation INTEGER NOT NULL DEFAULT 0,
    lastSequence INTEGER NOT NULL DEFAULT 0,
    state TEXT NOT NULL DEFAULT 'UNINITIALIZED',
    transitionId TEXT,
    previousGrantDigest TEXT,
    grantJson TEXT,
    grantDigest TEXT,
    PRIMARY KEY(id)
);

CREATE TABLE IF NOT EXISTS sync_record_versions (
    ownerIdentity TEXT NOT NULL,
    entityType TEXT NOT NULL,
    entityKey TEXT NOT NULL,
    generation INTEGER NOT NULL,
    revision INTEGER NOT NULL,
    isDeleted INTEGER NOT NULL DEFAULT 0,
    recordDigest TEXT,
    PRIMARY KEY(ownerIdentity, entityType, entityKey)
);

CREATE TABLE IF NOT EXISTS sync_links (
    peerIdentity TEXT NOT NULL,
    peerDevice TEXT NOT NULL,
    automaticEnabled INTEGER NOT NULL DEFAULT 0,
    scopeEpoch INTEGER NOT NULL DEFAULT 0,
    scopeJson TEXT,
    protocolVersion INTEGER NOT NULL DEFAULT 0,
    acceptedOwnerGeneration INTEGER NOT NULL DEFAULT 0,
    acceptedAuthorDevice TEXT,
    acceptedGrantDigest TEXT,
    PRIMARY KEY(peerIdentity, peerDevice)
);

INSERT OR IGNORE INTO sync_profile_state (id, internetEnabled) VALUES (1, 0);
INSERT OR IGNORE INTO sync_authority
    (id, generation, lastSequence, state)
    VALUES (1, 0, 0, 'UNINITIALIZED');
```

`recordDigest` — будущий локальный digest исходной версии, stage 1 его не вычисляет. Он никогда не отправляется вместо digest разрешённой проекции: разные scope требуют per-link digests в последующих таблицах. При желании не хранить digest исходного содержимого его можно оставить NULL; correctness держится на revisions/операциях и проверке конкретного пакета, а не на раскрытии hash запрещённых полей.

Singleton id=1, boolean range, enums, связи state/NULL и монотонность проверяются в domain/store с транзакцией. Новые CHECK/TRIGGER, которые Room annotations не воспроизводят при fresh install, не добавлять только в миграцию. Raw corrupt rows должны отвергаться store; это проверяется отдельно. У четырёх таблиц нет вторичных индексов: доступ stage 1 только по PK. Не дублировать JSON partner directory в реляционные FK и не привязывать row lifecycle к медленной/ошибочной дешифрации vault.

Ни один столбец существующих `periods`, `day_logs`, `journal_layout`, `profile_settings`, `partner_directory`, `partner_messages` не меняется. Существующие индексы/PK сохраняются. Room schema validation после migration обязательна; `PRAGMA foreign_key_check` и `integrity_check` — PASS на synthetic fixtures. Destructive fallback, database copy-over и downgrade не допускаются.

### 4.1. Fresh install и migration

`MIGRATION_6_7` выполняет только SQL выше. Никаких key loaders, UUID identity generation, расшифровки/пересборки JSON, часов, network и чтения файлов. Room вызывает миграцию транзакционно; тестировать failure rollback, не ловить ошибку с частичным успехом.

Fresh Room 7 создаёт те же таблицы через entities. `SyncStateStore.ensureDefaults()` идемпотентно вставляет id=1 defaults одной транзакцией при первом локальном чтении; отсутствие строки до этого эквивалентно off/uninitialized. Такой же store корректно читает уже вставленные миграцией defaults. Не вводить новую зависимость/глобальный onOpen callback только ради вставки двух строк. Если legacy code вообще не обращается к Sync, отсутствие singleton rows в fresh install безопасно и не мешает ручному обмену.

После миграции `sync_links` и `sync_record_versions` пусты. Не обходить старые записи/контакты для backfill. Это специально сохраняет исходные данные byte-for-byte и не создаёт активность при обновлении. Первоначальная версия/author grant/baseline появятся позже в одной контролируемой процедуре; записи, сделанные между upgrade и opt-in, войдут в исходный baseline, не потеряются из-за отсутствия журнала на этапе 1.

## 5. Миграционные и совместимые сценарии

Все фикстуры синтетические. Для schema fixtures использовать существующий подход проекта: JSON `schemas/.../<v>.json` → file-backed SQLite с tables/indices и user_version → закрыть → открыть актуальной Room с полной migration chain. Не обновлять старый файл schema 6 ради совпадения ожиданий. Общий test helper выделять только если он действительно устраняет повторение создания fixtures.

| Сценарий | Что проверять |
|---|---|
| v6: полный профиль | Периоды с UUID/created/updated/open end; день с null/enum/custom/note/icon/sex; layout с архивом/unknown-compatible JSON; settings EN/RU/DARK; exact values до/после, row counts, индексы |
| Контакты/права | Реалистичный `PartnerDirectory` с несколькими identities/devices, merged/group contacts, grants и lastSelection, mutualConfirmed false/true, disabled devices/exchangeEnabled false, legacy отсутствующими optional fields; raw JSON строка строго совпадает, имена/права не нормализуются заново |
| Сообщения | Incoming/outgoing DATA/DOODLE/ACK/OPENED; delivered/opened комбинации, hiddenFromChat, стёртые body/envelope и IDs-tombstones; exact body/envelope/created/received/statuses сохраняются |
| Старые версии | Отдельно 1→7, 2→7, 3→7, 4→7, 5→7 и 6→7. Проверить только существовавшие в источнике поля; defaults старых миграций 1→6 не подменять defaults новой миграции |
| Пустая/fresh БД | Empty v6→7 и fresh v7: структуры совпадают, default enabled=false/uninitialized, повтор ensureDefaults не меняет строки; version floor/identity hash валидны |
| Restart/идемпотентность | Повторное открытие Room 7 не вызывает новую миграцию, defaults не сбрасывают synthetic явно сохранённые настройки, отсутствие key generation; table/row snapshots неизменны |
| Ошибка миграции | Через test-only wrapper намеренно бросить исключение после DDL/вставки; user_version остаётся 6 и старые строки целы, новые DDL rollback; повтор штатной миграции проходит. Никакой test hook в production |
| Два/три профиля | Legacy `ritela.db` и `ritela-<id>.db` с разными одинаково названными контактами, settings и одинаковыми day/message IDs. Открытие/upgrade/switch одного не меняет другой. Неактивная база мигрируется только при открытии; закрытие handle корректно |
| Файлы рядом с БД | SHA-256/profile bytes у `partner-<id>.keys`, `.doodles`, профильных DataStore prefs, registry и имеющегося ciphertext cache совпадают до/после pure migration. Android Keystore alias остаётся; existing key можно использовать. Для legacy ProfileSession учитывать уже существующую eager key initialization, не выдавать её за действие migration |
| Реальные ключи/trust | Создать пару synthetic identities/сертифицированных устройств, подписанные invitations/контекст, encrypted DATA и timed DOODLE на v6; после upgrade расшифровать/проверить через прежние receive/retry APIs. identityId/deviceId/nonce/public keys/verificationContext не изменились |
| Replay/квитанции | Replay стёртого/hidden message не восстанавливает его; поздний ACK/OPENED не возвращает скрытый контент; delivered/opened не ставятся от факта migration. Проверить повтор файла и packet/frame round-trip без радио/эмулятора |
| Дудлы | IMMEDIATE/AFTER_DATE/AFTER_DAYS с днями/часами/минутами; первый receivedAt и unlockAt не сдвигаются после reopen. Standalone и DATA-вложение/послание; рисунок legacy > новых internet limits остаётся читаемым. Библиотека 15 элементов, UUID/savedAt/uses/order и file bytes сохранены |
| Backup/restore | Старые payload versions по существующим DayLogBackupTest читаются; medical backup формат не получает ключи/Sync consent, identity export по-прежнему отдельно. RestoreIdentity создаёт прежним способом новый device и не активирует автора; Sync remains uninitialized/off |
| Невалидные metadata | generation/revision overflow/negative, unknown enum, boolean не 0/1, ACTIVE без grant/author, одинаковое поколение с другим digest, NULL scope при auto=true. Store отказывает без включения sync; legacy own records доступны |
| Нет утечек/побочных эффектов | Migration/Store не вызывают injected key loader/transport (store не имеет таких зависимостей). Manifest permissions/dependency graph до/после не меняются. Synthetic note/token/key sentinel отсутствует в captured logs/exception messages |

Тесты модели перехода доказывают запрет недопустимых состояний/двух несовместимых ACTIVE grants в одном локальном authority state, а не невозможность работы двух физических телефонов. End-to-end CAS/transfer/recovery, сетевые гонки и proof-of-author signature будут проверены на этапах 3–5 до включения Sync. Регрессию RFCOMM frame/protocol можно проверить JVM-тестами; реальную связь двух телефонов этот этап не подтверждает.

## 6. Последовательность будущей работы

1. Повторно сверить чистоту дерева/Room 6 и миграционные builders. Зафиксировать fixture inventory и baseline checksums, подтвердить scope этого документа перед изменениями.
2. Добавить доменные модели/invariants/limits и meaningful unit tests. Никаких вызовов из существующих UI/repositories. Отдельный небольшой коммит после релевантных проверок.
3. Добавить четыре entities/DAO/store и Room migration 6→7, зарегистрировать в ProfileSession/test builders; экспортировать `7.json` и проверить только аддитивный schema diff.
4. Выполнить file-backed migration/compatibility matrix; исправлять реальные проблемы без переписывания старых schema JSON и отключения проверок. Запустить релевантные существующие регрессии; проверить отсутствие изменения vault/library bytes.
5. Обновить документацию фактическим результатом; выполнить Android checks соразмерно schema change и сохранить следующий проверенный коммит. Этап заканчивается здесь. Изменение Period/DayLog DAO ради журнала/фоновая очередь — отдельное согласованное продолжение.

## 7. Команды проверки и критерии готовности

Команды проверки этапа; конкретный выполненный прогон и результаты — в STATUS. Применяется `ritela-android-check` с действующим запретом на UI/PNG/эмулятор. Нужны format, checkKotlin, APK, lint, APK inspection и storage/regression tests; screenshot suite не запускать. Пример PowerShell с аргументами в строковых кавычках:

```powershell
& ./scripts/gradle.ps1 formatKotlin
& ./scripts/gradle.ps1 checkKotlin assembleDebug lintDebug
& ./scripts/gradle.ps1 testDebugUnitTest `
  '--tests' 'app.ritela.SyncModelsTest' `
  '--tests' 'app.ritela.SyncStateStoreTest' `
  '--tests' 'app.ritela.SyncMigrationTest' `
  '--tests' 'app.ritela.SyncCompatibilityTest' `
  '--tests' 'app.ritela.UserProfilesTest' `
  '--tests' 'app.ritela.DayLogBackupTest' `
  '--tests' 'app.ritela.PartnerTest' `
  '--tests' 'app.ritela.PartnerVaultTest' `
  '--tests' 'app.ritela.PartnerPairingTest' `
  '--tests' 'app.ritela.PartnerInvitationsTest' `
  '--tests' 'app.ritela.PartnerReliabilityTest' `
  '--tests' 'app.ritela.PartnerShareSelectionTest' `
  '--tests' 'app.ritela.PartnerMessageDeletionTest' `
  '-Pritela.skipScreenshots=true'
powershell -NoProfile -File scripts/verify-apk.ps1
powershell -NoProfile -File scripts/check-workspace.ps1
git diff --check
```

Тестов достаточно для затронутого хранения и legacy протокола; UI/Compose screenshot tests не нужны, пока UI не меняется. `verify-apk.ps1` должен по-прежнему подтверждать отсутствие network permissions. После успешных проверок не повторять их без новой причины. CI остаётся действующим, ничего не отключается.

Этап готов только когда:

- Room 7 экспортирована, fresh и migrated schema валидны; цепочки 1…6→7 проходят без destructive fallback и без изменения старых schema/migration files.
- Во всех compatibility fixtures старые таблицы/JSON/ключи/файлы и данные дудлов сохранены, профильная изоляция и replay tombstones не нарушены; legacy cryptographic packets продолжают проходить реальные проверки.
- Sync остаётся off/uninitialized и не создаёт очередь/сетевые задачи/новые ключи. Включение старого Bluetooth/partner режима не меняет это состояние.
- Model/store tests проходят, форматирование/APK/lint/inspection и релевантные storage regressions успешны; точные counts/results записаны в STATUS. Не утверждается проверка физического Bluetooth или новой распределённой авторизации.
- Код не меняет медицинские расчёты, wire/backup formats, grants/lastSelection, текущие Doodle limits и UI. Документация соответствует реализованному subset; собственные изменения сохранены локальными коммитами, без push/облака.

Граница после этапа 1 сохранена в описании выше; затем пользователь разрешил [этап 2](RITELA_SYNC_STAGE_2.md), который добавляет Room 8 и локальный journal/queue. Production transport и активная синхронизация по-прежнему не подключены. Следующая граница — этап 3.
