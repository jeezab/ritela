# Ritela Sync — этап 2: локальный журнал и очередь

2026-10-15. Пользователь разрешил этап 2 и последним уточнением попросил продолжить именно его. Локальный этап завершён, без перехода к этапу 3. Итог — 116 успешных целевых тестов, APK/lint и проверки сохранности схем; подробности и коммиты — [STATUS](../STATUS.md), общий дизайн — [RITELA_SYNC_PLAN](RITELA_SYNC_PLAN.md).

## Реализованный поток

`PeriodDao`, `DayLogDao` и `JournalDao` наследуют `ChangeRecorder` из `data/sync/SyncJournal.kt`. Их методы записи выполняют изменение и запись журнала в одной Room-транзакции. Внешние API repository сохраняются. Этот путь охватывает add/addAll/finish/edit/delete, очистку дня, сохранение layout и `BackupRepository.import()`, который уже вызывает эти DAO внутри общей транзакции. При ошибке журнала медицинская запись не считается успешно сохранённой; транзакция откатывается. Неуспешные пересечения и no-op не создают фиктивных изменений.

Журнал записывает собственные periods, разрешённое для будущей проекции представление дня (секции/теги/заметка) и layout. SETTINGS исключён из новых передач по существующим правилам, поэтому тема/язык не журналируются. Изменение только calendarIcon, не входящего в обмен, также не создаёт лишний payload. Дудлы/послания и квитанции остаются в прежнем ручном протоколе; автоматическое их enqueue относится к этапу 6.

`sync_changes.sequence` — локальная монотонная позиция, а не авторское поколение и не глобальная wire revision. Она не требует загрузки identity/vault. На стадии 2 `sync_authority` и `sync_record_versions` не активируются автоматически: криптографическое авторство/поколение будет связано с этими данными последующими этапами. Старые UUID/epoch-day и схемы 1…7 сохраняются.

## Room 8

Явная `MIGRATION_7_8` добавляет пять таблиц и индексы, не переписывает существующие десять entity:

| Таблица | Назначение |
|---|---|
| `sync_changes` | Auto-increment sequence, уникальный changeId, entityType/entityKey, deleted и локальный payload. Индексы по changeId и type/key/sequence |
| `sync_journal_state` | Singleton id=1 и floor допустимого cursor |
| `sync_link_progress` | PK peer identity/device/scope epoch; локальный cursor, per-link sequence, policyDigest и requiresBaseline |
| `sync_projection_state` | PK peer identity/device/epoch/type/key; digest только разрешённого представления, чтобы отличать снятие ранее переданного поля от никогда не разрешённой записи |
| `sync_outbox` | Immutable packet ID/bytes, адресат/epoch/policy digest, kind, диапазон локального cursor, per-link sequence, state/attempts/nextAttempt/lease/token/errorCode |

Схема `app/schemas/app.ritela.data.RitelaDatabase/8.json` экспортируется Room/KSP. Миграция зарегистрирована в ProfileSession и существующих migration builders. Она не делает backfill, не включает интернет, не читает ключи и не меняет contacts/grants/lastSelection или файлы профиля. Все прежние миграции остаются.

## Снимок, фильтрация и версии

`SyncQueueRepository.prepare()` в одной транзакции проверяет независимый global opt-in, link auto-send/protocol/epoch, действующие grants и enabled/mutualConfirmed устройства. Никаких UI-методов включения или автопривязки нет. Legacy grants сами по себе не являются согласием. Whole-section выбор допускается только после фиксации tag ID; section-wide остаётся лишь note. Scope не может незаметно измениться без нового epoch.

Для первого baseline в этой же транзакции backfill добавляет отсутствующие текущие записи в журнал, фиксируется watermark W и готовится согласованная разрешённая проекция. Записи после W попадут в следующий проход; конкурентная мутация не может вклиниться в snapshot transaction. После enqueue прогресс, projection digests и bytes фиксируются атомарно. Ошибка encoder/превышение лимита не продвигают cursor и не теряют сохранённые исходные записи.

`ShareProjector` отбрасывает запрещённые категории/значения/имена тегов и заметки, не включает будущие новые теги. Несколько ещё не поставленных в outbox изменений одной записи coalesce до последнего состояния; уже созданные bytes не переписываются. Если ранее переданное поле снято, отправляется удаление проекции. Если запись никогда не содержала разрешённых значений, не отправляются даже её дата/ID. Изменение только запрещённых значений не создаёт пакет.

Глобальные source watermark/sequence хранятся только локально. В draft payload используются per-link sequence/revisions; это не раскрывает адресату количество чужих или запрещённых изменений. JSON канонизируется перед сравнением digest. Контекстный digest относится к конкретному разрешённому представлению, не является hash полного запрещённого дневника.

FORECASTS проецируется только через явно переданный owner snapshot provider, который должен использовать существующий FORECASTS-only preview владельца в том же profile snapshot. По умолчанию provider отсутствует — подготовка такого пакета блокируется, а не выдумывает прогноз. Поля ограничены allowlist, контекст periods — максимум одна запись. Алгоритм прогнозов и UI не изменены; автоматическое обновление по смене даты/алгоритма относится к этапу 6.

## Очередь и восстановление

`SyncPacketEncoder` обязателен и не имеет production implementation/fallback на plaintext. `SyncDraft` — внутренний DTO, не финальный wire v2; его строковое представление скрывает содержимое. Тестовый encoder намеренно использует synthetic plaintext bytes только в tests. Ни UI, ни ProfileSession не запускают prepare/engine и не создают fake packet в пользовательской БД.

`domain/sync/SyncEngine.kt` зависит от чистых портов `SyncQueuePort`/`SyncTransport`. Реального сетевого/file/Bluetooth адаптера нового протокола нет. Engine делает один явный шаг, без scheduler, фоновых таймеров, WorkManager и сетевых разрешений.

Состояния локального outbox: READY → IN_FLIGHT → STORED либо RETRY_WAIT/CANCELLED. STORED означает лишь успех transport.store и не меняет `PartnerMessage.delivered/opened`. E2E ACK/приём/подтверждение применения — этап 3. Другой пакет того же peer/epoch не обгоняет незавершённый предыдущий.

Lease действует 120 секунд, имеет случайный token, attempts и время. Рестарт восстанавливает очередь из Room; истёкший lease можно забрать с новым token. Старый исполнитель не подтверждает работу нового; проверяются token/state/срок и неизменность bytes/адресата. Повтор использует те же ID и bytes. IO-ошибка даёт сохранённый exponential backoff с jitter от порядка 30 секунд до 6 часов; больший Retry-After учитывается, переполнение времени не оборачивается. Coroutine cancellation/программные ошибки не маскируются как успешная доставка или обычный сетевой retry.

Политика перепроверяется при claim и непосредственно перед transport handoff. При отзыве pending bytes стираются, записи становятся CANCELLED, requiresBaseline=1. Обрабатываются и отложенные retry, а не только уже наступившие attempts. После повторного включения нужен свежий baseline с новым ID и дальнейшим linkSequence. Новый scope требует нового epoch. Уже переданные transport bytes отозвать назад нельзя; это ограничение сохраняется для будущей интернет-интеграции.

## Лимиты и compaction

Один draft ограничен 128 операциями и 96 КиБ plaintext; encoded envelope — 256 КиБ. Retained payload queue ограничена 16 МиБ, 512 пакетами на профиль и 256 пакетами на адресное устройство. STORED bytes также учитываются: без E2E подтверждения не предполагается, что их можно безопасно потерять. При заполнении очередь блокирует новую материализацию; собственные записи и журнал продолжают сохраняться. В v2 нужны ACK/checkpoint cleanup и multipart: oversized baseline сейчас возвращает BLOCKED без усечения истории или фиктивного успеха. Chunking — этап 3, не скрытый неограниченный обход лимита.

`compact()` не проходит дальше самого раннего ещё нужного cursor/pending range. Она всегда сохраняет последнюю исходную версию каждого ключа и tombstones, повышает floor, а клиент ниже floor обязан получить baseline. До появления хоть одного recipient checkpoint/outbox каждые 256 локальных изменений DAO безопасно убирают superseded history, оставляя latest/tombstones и floor; opt-out профиль не хранит неограниченную историю старых заметок. После появления recipient state применяется явная compaction с учётом всех курсоров. Автоматический периодический dispatcher/очистка E2E-acknowledged outbox будут подключены позже.

Журнал и digests находятся только в приватной локальной Room БД, как существующие медицинские данные; это не новая защита at rest. Логирования payload, ключей, capabilities или медицинских значений не добавлено. Ручные `.ritela`, existing encryption/v1 pairing и foreground RFCOMM прежние.

## Проверки и дальнейшая граница

Новые тесты: `SyncEngineTest`, `SyncJournalTest`, `SyncQueueTest`. Они проверяют атомарность DAO/import, ошибки журнала/encoder, bulk/no-op/delete, bounded opt-out history, фильтрацию/новые теги/скрытые даты и labels, per-link revisions, scopes/epoch/revoke, snapshot во время конкурентной записи, queue limits, restart/lease/backoff/stale executor, compaction/tombstones, два профиля и отсутствие default network/forecast adapters.

`SyncMigrationTest` проверяет цепочки 1…7→8 и rollback после фактически выполненных DDL из 6 и 7 (есть отдельное подтверждение, что failure migration действительно вызвана). Старые compatibility/regression tests актуализированы для Room 8. Полный целевой набор, APK/lint и schema comparison фиксируются в STATUS. UI/PNG/эмулятор не запускаются; физическая связь и remote Cloudflare не проверяются.

Следующий этап 3 — согласование возможностей/wire v2, проверенный encrypted receive/apply, baseline chunks, конфликты и удаление/квитанции/дедупликация между транспортами. Этот документ и этап 2 не означают, что эти механизмы уже реализованы.
