# Ritela Sync: этап 3

Локальный протокол и приём реализованы без сетевых обращений, нового UI, зависимостей и генерации ключей при миграции. Автоматический запуск не подключён: UI consent/setup — этап 7, relay/HPKE/HTTP — этапы 4–5, автоматическая публикация посланий/Doodles — этап 6. Ручные DATA/DOODLE v1, существующая библиотека, SAF и foreground RFCOMM сохранены.

## Формат и согласование

`SyncEnvelopeCodec.kt` описывает application protocol 2. Для явного ручного обмена используются существующие `ritela.partner.encrypted`/`ritela.partner` v1 с новыми подписанными типами `SYNC_CAPS` и `SYNC`. Это явно заявленный suite `manual-rsa-oaep-aesgcm-ecdsa-v1`, а не HPKE и не скрытый downgrade. Старый клиент отвергнет неизвестный kind; старые DATA/Doodles продолжают читать оба клиента. RSA-OAEP/AES-GCM/ECDSA остаются прежними, с прежним ограничением forward secrecy. Интернет-encoder обязан использовать будущий Tink wrapper; ручной encoder нельзя автоматически считать интернет-совместимым.

`PartnerRepository.prepareSyncCapabilities` — явная подготовка зашифрованного offer. Получение offer и локальное предложение хранятся отдельно для каждого устройства и привязаны к обоим nonce текущего pairing. До двустороннего обмена encode/receive SYNC запрещены. Offer не включает интернет и не назначает автора. Разрешения и состояние доверия проверяются в той же Room-транзакции, что подготовка/приём. Подписываются адреса, device, link, ID, created и всё содержимое; повторно используется сохранённый ciphertext, не новая рандомизированная упаковка того же ID.

`encodeSyncDraft` — готовый ручной encoder для существующего `SyncPacketEncoder`; `receive`, `retry` и `acknowledgement` общие для файлов и Bluetooth. Нет нового фонового транспорта. RFCOMM принимает новые kinds и возвращает прежний подписанный ACK на том же соединении. Файловый импорт по-прежнему не генерирует обязательный ответный файл. Квитанцию можно подготовить явным API; физическое соединение в JVM не проверено.

`SyncManualTransport.kt` соединяет SyncTransport с явно переданной foreground request/reply операцией: перед передачей проверяет сохранённый packet/current trust/author/scope, затем принимает и проверяет зашифрованный ACK. Нет реализации сокета/сети по умолчанию. Один только экспорт файла не объявляет remote storage. Если E2E ACK успел освободить outbox до возврата transport.store, queue.stored узнаёт точное подтверждённое сообщение и возвращает успех без восстановления ciphertext/lease.

## Автор и перенос телефона

`SyncAuthorGrants.initialize` вызывается явно: первое поколение привязано к текущему EC device key; повтор возвращает ровно сохранённый grant. Наличие RSA identity/certificate не активирует восстановленное устройство. `approveSyncAuthor` — отдельное явное подтверждение получателем: проверяет текущий доверенный device key и подпись grant. Приём SYNC требует точного owner/device/generation/grant digest. Макcимальный принятый generation не сбрасывается при очистке данных.

Identity import в свежий профиль сначала фиксирует RECOVERY_REQUIRED в Room, затем меняет vault. Сбой между действиями оставляет публикацию заблокированной; импорт не создаёт ACTIVE/generation 1 и не заменяет существующую author authority. Восстановленный RSA по-прежнему пригоден для явного ручного v1 pairing/обмена; новая author activation требует отдельного recovery/fence.

Преемник требует generation +1, previous digest, новый подтверждённый device, его подпись и countersignature прежнего автора. Конкурирующий grant того же поколения и откат отвергаются. `retire` проверяет подписанное предложение нового телефона и атомарно сохраняет RETIRED, точный handover result и отмену активной очереди до выдачи подписи. Повтор того же предложения возвращает те же bytes; другое предложение не проходит. Retired/восстановленный телефон не может повторно initialize/encode/retry SYNC.

При подтверждении преемника pending payload старого поколения очищается, а ID/digest остаются. Inbox различает pending/applied/discarded (0/1/2): отбрасывание и очистка неполного baseline не создают ложный application ACK и не занимают pending budget навсегда.

Активации нового автора по локальному handover здесь нет: необходим relay fence и idempotent transition commit этапа 4. Нет обхода для потерянного телефона: recovery требует отдельной операторской процедуры/подтверждения, а не одного identity export. Офлайн-получатель, ещё не увидевший новый grant, может принимать старое поколение; глобальное мгновенное запрещение не обещается. Откат всей приватной БД вместе с ключами вне поддерживаемого backup также требует внешнего fence. Обычный backup не содержит sync authority/checkpoints и их не откатывает. Старый fail-closed `SyncStateStore` по-прежнему не объявляет непроверенный ACTIVE metadata готовым без специализированного verifier.

## Очередь, сборка и проекция

`SyncQueueRepository` разбивает baseline на части по 128 операций и byte budget с резервом под metadata; до 64 частей/3 МиБ. Большая дельта делится на последовательные DELTA packets. Baseline имеет общий batch UUID, part/count и общий record revision, равный первой per-link sequence; каждая часть имеет собственные immutable packet ID и sequence. У дельт revision соответствует sequence соответствующего пакета. Global source watermark в wire не попадает. Scope передаётся явно и остаётся frozen; слишком большой scope/record не обрезается, подготовка BLOCKED. Все части, source/projection cursors и bytes сохраняются одной транзакцией; ошибка любой части откатывает весь enqueue.

`SyncApplyRepository` работает только после проверки подписи/адреса/nonce/current trust. Inbox хранит SHA-256 точных envelope bytes, включая после очистки payload. Тот же ID с другим digest или новая ID с занятой sequence — отказ. Pending ограничен 256 пакетами на owner и 16 МиБ на профиль; digest ledger ограничен 65 536 ID, после заполнения приём останавливается, tombstones не удаляются автоматически. Политика последующего безопасного checkpoint/GC ledger остаётся задачей перед длительным публичным использованием.

Полный baseline публикуется одной транзакцией. Перестановка/повтор/перезапуск между частями допустимы; отсутствующая часть не меняет видимую проекцию. Одинаковый record key в разных частях запрещён. Все части должны иметь одинаковый scope/epoch/grant. UPSERT/DELETE применяются к отдельным received records, собственные DAO не вызываются. Дельты с gap ждут в inbox; очередной полный baseline может закрыть gap. Новый scope epoch требует baseline и полностью заменяет прежнюю проекцию. Scope внутри одного epoch неизменяем. Прежние epoch/sequence не откатывают состояние; tombstones защищены checkpoint и immutable ID ledger. В wire разрешены PERIOD/DAY/FORECAST и целиком отфильтрованный LAYOUT/current; последний хранит только разрешённые названия тегов, без индивидуальной синхронизации пользовательского редактора layout.

`PartnerRepository.messages` добавляет служебную скрытую `SYNC_PROJECTION`; `partnerDataMessage` предпочитает её v1-снимкам даже после локальной очистки. Старый DATA остаётся в истории, но не перехватывает актуальную v2-проекцию. Snapshot использует существующий валидатор и только переданный owner forecast; расчёт цикла не менялся. Содержимое хранилища приватно для профиля, как существующая Room БД; новое шифрование at rest не заявляется.

## Квитанции и удаление

ACK для SYNC содержит не более 128 обработанных ID данного отправляющего устройства. Неполный baseline получает пустой список: durable receipt отдельной части не делает её E2E delivered. ACK после полной сборки покрывает части; для большого gap можно повторить явные квитанции. Sender проверяет identity/device/direction/kind каждого ID в одной транзакции, затем ставит delivered и освобождает ciphertext соответствующего outbox. Это не OPENED. Существующее per-device направление PartnerMessage сохраняется; квитанция второго доверенного устройства не подтверждает пакет первого.

OPENED поддерживает самостоятельный Doodle и Doodle внутри DATA. Для задержки используется первое persisted receivedAt; duplicate его не обновляет. Для очищенного исходящего DATA отдельный небольшой marker подтверждает, что там действительно был Doodle. Поздняя квитанция не восстанавливает скрытое/стёртое сообщение. Подготовка OPENED и отметка opened сохраняют существующий явный flow; автоматическое создание/публикация посланий относится к этапу 6.

`clearReceived` очищает local projection/records/pending payload и v1 copies, сохраняя ID/digest/checkpoint/grant. Повтор не возвращает содержимое; последующее новое разрешённое изменение может быть получено. Отзыв будущего доступа не стирает эти копии автоматически; удаления relay здесь нет. Получатель может сделать экспорт/скриншот или модифицировать клиент: гарантированное стирание переданной информации не обещается.

## Room и проверки

Room 9, явная `MIGRATION_8_9` создаёт только семь таблиц: `sync_capabilities`, `sync_received_author`, `sync_received_state`, `sync_received_records`, `sync_inbox`, `sync_doodle_receipts`, `sync_handover`. Прежние пятнадцать entities и схемы/миграции 1…8 сохранены. Нет backfill старой переписки, смены ключей, изменения контактов или opt-in. ProfileSession и прежние migration builders регистрируют новый переход.

`SyncReceiveTest` проверяет negotiation/author gate, reversed/missing/duplicate chunks, restart, immutable digest/sequence collision, atomic reject последней части, gaps/DELETE/scope reset/v1 precedence/local clear, два устройства/receipt binding, author successor/retirement/restore и embedded Doodle delay/OPENED. `SyncQueueTest` проверяет 260 записей baseline/delta и rollback при ошибке второй части. `SyncMigrationTest` проверяет все 1…8→9, fresh/migrated DDL и фактический rollback/retry из 6/7/8; compatibility tests сохраняют реальные синтетические encrypted v1 envelopes, профили, vault, DataStore и legacy Doodles.

Точные результаты проверок и оставшиеся ограничения — `docs/STATUS.md`. Ни relay/D1, ни реальные HTTP/HPKE, ни UI/PNG/эмулятор не запускались.
