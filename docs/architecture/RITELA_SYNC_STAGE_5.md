# Ritela Sync — этап 5: Android delivery runtime

Дополнение: [упрощённое подключение и auto updates](../RITELA_SYNC_USING.md)
включают service setup в приглашение, immediate upload/foreground receive,
отдельный auto consent и author approval. Room 11 и relay HTTP API прежние.
Изложенные ниже два ручных setup roundtrip остаются совместимым fallback.

2026-10-16: подключена цепочка consent → pilot registration → authenticated manual
setup → durable HPKE → HTTP upload → receive/apply → storage ACK → E2E receipt.
Оператор сообщил о развёртывании https://ritela-relay.zabvenen.workers.dev;
публичный origin задан в gradle.properties. Неавторизованный HTTPS GET `/`
проверен отдельно: HTTP 401/UNAUTHORIZED. Активация/доставка через production ещё
не проверены. `-Pritela.relayOrigin=` делает подключение недоступным в сборке;
наличие адреса не включает согласие и не вызывает relay из приложения.

Room 11 / `MIGRATION_10_11` добавляют пять пустых таблиц: `relay_config`
(регистрация/cursor/backoff), `relay_routes` (направленные pairing bindings),
`relay_packets` (immutable HPKE), `relay_received` (canonical semantic replay
digest и отдельные manual/HPKE wire hashes), `relay_peers` (public certificates).
Схемы 1…10 и все 23 прежние entities
сохраняются; upgrade не активирует сеть и не backfill-ит историю. Activation token
и route capabilities хранятся в защищённых BLOB под отдельным profile Keystore
ключом. WorkManager.Data содержит только UUID профиля. Setup удаляется из
plaintext PartnerMessage.body; прежний зашифрованный manual envelope сохраняется.

`RelayRuntime` сериализует pump профиля. Подтверждённые intents/record outbox
материализуются в сохранённый HPKE до HTTP. Потерянный ответ/перезапуск повторяют
те же ID/bytes. STORED — хранение сервером; delivered требует authenticated E2E
квитанции. Вся страница импортируется одной Room transaction: подписи/address/
pairing, projection, replay digest и ACK. Затем storage ACK, затем cursor. Сбой
между ними безопасно повторяет импорт; manual/HTTP дубликат сохраняет первый
receivedAt и tombstone. 408/429/5xx/IO дают durable backoff/Retry-After; ошибки
доступа/конфликта/валидации требуют явного возобновления, не создают hot loop.

Для уже подготовленной record outbox есть отдельный явный API:
`registerAuthorFence()` публикует только существующий ACTIVE generation-1 grant,
не назначает автора. После отдельного receiver author approval создаётся
`createReceiveRoute(authority=senderHandle, baselineBytes, baselineParts)` с
reservation; затем обычные queue encoder/HPKE/apply/E2E ACK. Такой route требует
того же sender handle, successor generation остаётся заблокированным. Этот путь
не является автоматическим UI baseline setup этапа 7 и не запускает подготовку
изменений при каждой правке.
Разовая reservation передаётся только с BASELINE; DELTA использует обычный
дневной budget и не блокируется истёкшим резервом первоначальной передачи.

Локальные ограничения: ciphertext 16 МиБ, 512 pending intents, 65 536 packet/replay
IDs, TTL до 7 суток; прежние wire limits adapter сохраняются. Большие legacy
Doodles остаются доступны вручную. Новая author generation >1 блокируется до
явного server transfer, согласие на сеть не даёт automatic medical grants.

`SyncWorker`/`SyncScheduler`: unique one-time work и hourly periodic fetch,
15-minute flex, jitter до 10 минут, CONNECTED, exponential retry для сетевых ошибок.
Непустая (в том числе ограниченная по bytes) страница и готовый остаток очереди
создают durable immediate continuation; отсутствие маршрута не образует hot loop.
Инициализация
WorkManager отложена; opt-out не создаёт sync jobs. Room invalidations восстанавливают
работу и замечают новые задания/routes, cursor-only updates не образуют цикл.
Каждый запрос проверяет active profile/consent; upload дополнительно rechecks
peer/device/grants/epoch/pairing/source digest. Switch/delete/close отменяет и
дожидается HTTP перед закрытием Room. INTERNET/ACCESS_NETWORK_STATE добавлены,
cleartext запрещён; до согласия DNS/HTTP/регистрации/relay key creation нет.
Каждый control/delivery запрос также требует совпадения saved и build origin:
обновление сборки с другим адресом не переносит прежнее согласие на новый сервер.

Пилот: оператор отдельно развёртывает Worker и выпускает tokens; приложение
собирается с точным HTTPS origin. На каждом телефоне: настройки синхронизации →
согласие и код. Затем оба дважды обмениваются setup через прежний файл/Bluetooth:
сначала публичными transport certificates, потом receiver-owned routes. После
этого подтверждённые послания/Doodles и receipts идут без ручного файла. Кнопка
подготовки setup открывает системную передачу зашифрованного файла; обычный Send
при действующем маршруте не требует повторного выбора file/Bluetooth. Форма кода
использует общий PartnerModal с прокруткой/IME insets и закреплёнными действиями.
Маршрут действует до семи суток; в этом пилоте его обновляют новым явным setup.
После expiry ручной обмен остаётся доступен; старые packet IDs не оживляются
сменой маршрута, требуется новая подтверждённая отправка.

Disable отзывает будущий доступ и отменяет локальную работу, не обещая удалить
уже отправленные bytes. При peer revoke runtime пытается удалить relay packets;
это не стирает прочитанные/экспортированные копии. При global off HTTP удаления
нет. Medical backup не переносит relay keys/config/checkpoints.

Отдельно остаются adaptive foreground receive, auto record preparation/forecast
refresh (этап 6), baseline/scope/deletion UX (этап 7), author-transfer UI,
security review и физические устройства. Public registration и automatic BLE
не добавляются, ручной RFCOMM сохраняется. Точные проверки — `docs/STATUS.md`.

## История первой изолированной части (до runtime)

Первая самостоятельная часть — `data/sync/HttpRelayTransport.kt` и
`HttpRelayTransportTest.kt`. Это adapter существующего `SyncTransport`,
подписанные control requests, fetch и явный storage ACK. Клиент не подключён
к runtime очереди или UI. INTERNET permission пока не добавлен; обычное приложение
не может делать сетевые запросы. Конструкторы не создают keys и не выполняют I/O.

Endpoint требует явного точного HTTPS host allowlist, без userinfo, нестандартного
port, query/fragment в origin. Нет default relay, discovery, HTTP fallback или
redirects. Платформенные TLS/hostname checks не переопределяются. Контроль
согласия — обязательный suspend callback, включая свежую проверку lease/peer,
profile session и global flag. Он вызывается до подготовки, непосредственно перед
connect и после ответа. При off/switch cancellation отключает HttpsURLConnection;
уже ушедшие bytes отозвать нельзя.

Timeouts: connect 10 секунд, read 15, общий deadline 30. Успешный ответ ограничен
512 КиБ, error body 16 КиБ, request control body 16 КиБ, upload 256 КиБ.
Compression не используется. Ошибки имеют только технические коды без URL,
payload, headers или exception causes. API failures сохраняют status и bounded
Retry-After delta-seconds; 408/429/5xx считаются retryable, 401/403/409/413
требуют решения верхнего уровня. Adapter сам не делает retry и не обходит budget.

Upload использует уже сохранённый ciphertext; не перешифровывает и не меняет ID
при retry. Route/capability/expiry/generation/grant/reservation подписаны в canonical
request. CANCELLED response не считается успешным storage. Смена HTTP request UUID
при повторе допустима: идентичность packet сохраняется delivery ID и digest.
Сохранение bytes на relay не является E2E delivery.

Fetch сначала целиком валидирует page: число записей, строго возрастающий safe
integer sequence, UUID/route, canonical Base64 и byte limits, уникальность и cursor
последней записи. Ошибка в конце страницы не возвращает частичный cursor.
Adapter не продвигает checkpoint и не отправляет ACK автоматически: следующий
шаг обязан связать durable authenticated apply, ACK и сохранение cursor в таком
порядке, чтобы process death давал безопасный повтор без потери данных.

Следующая часть этого же этапа (ещё не реализована):

1. Определить durable relay configuration/route bindings и fetch checkpoint,
   исключить capabilities из plaintext settings/WorkManager.Data. При расширении
   Room — явная migration 9→10 и проверка всех populated старых схем.
2. Связать HPKE encoder/decoder с SyncQueueRepository/SyncApplyRepository;
   исключить передачу manual suite через internet и не смешивать transport ACK/E2E ACK.
3. Добавить SyncWorker/SyncScheduler с unique profile work, network constraint,
   hourly periodic fetch/jitter, durable retry и Retry-After; отключённый профиль
   не создаёт work. Начальное восстановление scheduler не включает consent.
4. Связать ProfileManager switch/delete/close с отменой HTTP/work и ожиданием
   завершения перед закрытием старой Room session. Проверять профиль при каждом запросе.
5. Только при подключении runtime добавить INTERNET/ACCESS_NETWORK_STATE,
   запрет cleartext, обновить verify-apk/privacy и прежний permission test.
6. Проверить restart/process death, partial apply/ACK, Doze/constraints через
   WorkManager TestDriver, retry 429/5xx, global/peer off и switch/delete в полёте.

Этап 5 в целом не объявлен готовым; текущая часть не даёт работающей интернет-
синхронизации. UI/setup/автоматические пользовательские send actions относятся
к этапам 6–7. Ручной Bluetooth/BLE, Room 9 и существующие данные не меняются.

Проверки первой части: format/checkKotlin/assembleDebug/lintDebug — PASS,
71 тест (HttpRelayTransportTest, RelayCryptoTest, Sync*Test, UserProfilesTest),
0 failures/errors/skipped; lint 0 errors/0 warnings/9 прежних hints.
9 HTTP tests используют только синтетические exchange/connection, без DNS/HTTP.
APK проверен без network permissions; workspace/diff/staged checks — PASS.
UI/PNG/эмулятор/production relay не запускались.
