# Ritela Sync — этап 6, локальные подтверждённые отправки

Дополнение 2026-10-16: automatic record preparation, owner forecast refresh,
адаптивный foreground receive и frozen auto scope подключены. Получатель один раз
подтверждает автора/selection, дальнейшие journal/deltas/DELETE приходят в чат.
Send немедленно загружает пакет; foreground fetch имеет сохраняемый budget и
Retry-After. [Текущая инструкция](../RITELA_SYNC_USING.md), точные проверки — STATUS.
Ниже история первой реализации; прежние «не подключено» к текущей версии не относятся.

2026-10-16: runtime зависимости этапа 5 подключены: HPKE routes/config, immutable
dispatch, transactional receive/ACK/cursor и WorkManager. Явные DATA/DOODLE и
ACK/OPENED intents доставляются после consent/пилотной активации/manual setup.
Остаются automatic record preparation/forecast refresh, adaptive foreground
receive и общий UX автоматических scopes/baseline. Ниже история первой части.

## Реализованный путь

`PartnerRepository.confirmData` и явный `prepareDoodle` сохраняют PartnerMessage
и намерение отправки одной Room-транзакцией. Preview `prepareData`, редактор и
библиотека сохранённых Doodles сами по себе намерений не создают. Без global
opt-in/действующего protocol-2 link/текущего mutual pairing намерение отсутствует.
Включение интернета не превращает старую историю в исходящую очередь.

Room 10/MIGRATION_9_10 добавляет только `sync_message_intents` и index state:
PK messageId; identity/device/kind; epoch; pairing context digest; grants digest;
digest точного исходного сообщения; state/errorCode. Все 22 прежние entities
идентичны Room 9, схемы/миграции 1…9 остаются. Таблица пуста при upgrade;
миграция не включает sync, не читает vault и не создаёт ключей.

Намерение ссылается на существующий PartnerMessage, не копирует медицинский
plaintext или manual envelope в новую transport queue. `PENDING_WRAP` означает
ожидание будущего HPKE encoder; отправлять эти bytes через HttpRelayTransport
нельзя. Отдельная таблица исключает столкновение message IDs и author sequence
автоматических BASELINE/DELTA, которые продолжают использовать sync_outbox.
Послание не требует включать автоматическую публикацию медицинских изменений
или назначать устройство автором; разрешение и scope для auto records отдельны.

`SyncMessageQueue.offer` идемпотентен по ID, `pending` повторно проверяет global,
peer/device, оба pairing nonce/key/context, epoch, grants и неизменность исходного
сообщения. Отзыв/смена контекста/удаление дают CANCELLED, E2E ACK — ACKED;
ни повторное включение, ни поздняя квитанция не оживляют CANCELLED ID.
Изменение directory и отмена намерений выполняются одной транзакцией.
При direct SQL выключении global отмена обнаруживается следующим pending;
будущий dispatcher обязан повторять проверку перед каждым запросом.

Заведомо большие сообщения и Doodles отмечаются BLOCKED/MESSAGE_TOO_LARGE:
96 КиБ минус 2 КиБ резерва wrapper, 128 strokes/4000 points/240 caption.
Окончательный HPKE ciphertext всё равно требует отдельной проверки 256 КиБ.
Блокировка не усекает старый рисунок и не ломает manual v1. До transport binding
дневной relay budget и финальный wrapper size не считаются проверенными.
Локальная таблица ограничена 65536 IDs и 512 pending intents; при заполнении
offer возвращает BLOCKED без потери самого локального сообщения. UI обработки
этого результата/GC и общего byte budget с HTTP outbox ещё нет.

Квитанции создаются существующим authenticated pipeline: один и тот же pending
ACK с тем же body/current context переиспользует ID, включая повтор после restart.
Открытие Doodle по-прежнему отсчитывается от первого receivedAt; до заданной
даты OPENED не создаётся. ACK/OPENED ставятся в намерения лишь при разрешённом
internet link. Флаг delivered меняет только проверенная E2E квитанция; наличие
намерения или будущее relay store не означает доставки.

## Проверки и продолжение

`SyncMessageQueueTest` покрывает opt-out/no backfill/preview, reopen/ID idempotence,
независимость от auto author, SQL-trigger rollback сообщения/выбора/намерения,
revoke/global off/epoch/grants/device disable, большой legacy Doodle и ручной
roundtrip, E2E ACK/replay/удаление/late ACK, delayed OPENED и reuse ACK.
`SyncMigrationTest` теперь проверяет все 1…9→10, заполненные v8/v9 служебные
таблицы и BLOB, fresh/migrated DDL/index, rollback/retry из 6/7/8/9.

Следующее действие: завершить зависимости этапа 5 — durable HPKE binding,
freeze ciphertext/route/expiry, persistent HTTP dispatch/fetch/apply и scheduler.
После этого объединить message intents и record outbox в dispatcher с повторной
проверкой разрешений, общим byte budget, TTL/retry/resync; добавить автоматическую
подготовку записей/forecast refresh и восстановление из журнала после process death.
Этап 6 в целом не завершён. Новый UI, автоматический Bluetooth/BLE и физический
пилот не входят в текущую часть; разрешённый manual обмен сохранён.

Финальная проверка части: format/checkKotlin/assembleDebug/lintDebug — PASS,
164 selected tests / 0 failures/errors/skipped (8 SyncMessageQueueTest).
Lint 0 errors/0 warnings/9 прежних hints; signed APK API 26–37 проверен без
network permissions. Workspace/diff/staged checks — PASS. Старое ожидание v9
в compatibility test обновлено до v10; после этого выбранный прогон повторён
целиком. Новых зависимостей нет, UI/PNG/эмулятор/внешняя сеть не запускались.
