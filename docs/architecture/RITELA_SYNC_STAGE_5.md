# Ritela Sync — этап 5, работа продолжается

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
