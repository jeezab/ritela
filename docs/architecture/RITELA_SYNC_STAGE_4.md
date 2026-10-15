# Ritela Sync — результат локального этапа 4

Реализованы Worker/D1 и Android transport crypto primitives. Production relay
не развёрнут, рабочая D1 не открывалась. Android пока без INTERNET permissions;
HTTP, scheduler и подключение primitives к очереди — этап 5.
Room остаётся v9; старые ключи, профили, контакты, manual v1/RFCOMM и Doodles
не менялись. При удалении профиля дополнительно удаляется только его relay vault.

## Реализация и границы

`relay/src/{index,auth,routes,packets,authorities,cleanup}.ts`, миграция
`relay/migrations/0001_relay.sql`: pilot activation с proof of possession,
раздельные receiver/sender/capability права, immutable delivery IDs, replay
nonce, byte/row quotas, reservations, TTL, cancel-before-upload и receiver ACK.
Все проверки допуска mutation повторяются внутри atomic D1 batch; CHECK guard
откатывает весь batch. GET не пишет nonce. Fence CAS и две подписи исключают
двух текущих relay authors; это не заменяет E2E author approval на Android.
После transfer старые authority routes закрыты, reservations освобождены.
Без старого ключа self-service recovery намеренно отсутствует.

`PartnerEnvelopeCrypto.kt`: Google Tink 1.23.0 HPKE base
X25519/HKDF-SHA256/AES256GCM, один RAW key. Подпись существующего device key
внутри обязательна: HPKE base сам по себе sender не аутентифицирует.
Контекст включает suite, route, delivery ID, hash точных recipient public bytes;
inner signature связывает identity/device/target/pairing nonce. Не изобретены
ECDH/KDF/AEAD composition и ratchet. Compromise статического recipient private
key раскрывает ранее записанные ciphertext: forward secrecy не обещается.
Вращение ключей/новый route ограничивает будущую экспозицию, не стирает историю.
96 КиБ ограничивают signed inner body, 256 КиБ — ciphertext; JSON escaping
проверен отдельно, без ошибочного приравнивания размера оболочки к inner body.

`RelayKeyVault.kt`: отдельные API P-256 и HPKE keys per profile, AtomicFile
под Android Keystore AES-GCM с profile-bound AAD. Без явного consent create
запрещён; load отсутствующего файла не генерирует keys. Повреждение/missing
wrapping key приводит к отказу без перезаписи. Identity/device keys не ротируются.
`RelayTransportCertificate.kt` подписывает API/HPKE public binding существующим
device key и проверяет текущий mutual pairing nonce; сертификат не даёт author rights.
`RelayRequestAuth.kt`: canonical ASCII и строгое DER↔P1363 преобразование только
формата ECDSA signature, без реализации криптографических операций.

## Проверки безопасности

Локальный code/threat review: malicious registered sender/receiver, угадывание
route, forged capability/signature, replay, altered body/path, двойной transfer,
upload/revoke/cancel race, quota race, rollback и восстановление snapshot.
16 Worker tests на реальной локальной D1 runtime; TypeScript check, dry-run
bundle и npm audit. Межъязыковые публичные JCA/WebCrypto vectors проверяются
обоими runtime; Android Tink roundtrip, wrong recipient/context/link/tamper,
vault isolation/corruption и transport certificate forgery проверяются Robolectric.
Это не независимый аудит, physical Android interop или измерение production CPU.
До release нужен отдельный security review и двухтелефонный пилот.

Relay видит API handles, route metadata, размеры/время и IP на edge;
медицинский plaintext не передаётся Worker. Worker не пишет exception objects,
headers, URLs, bindings или bodies в логи; observability выключена. Метрики тестов
содержат только synthetic aggregate rows/bytes. Злоупотребление выданным token,
traffic analysis, DoS в пределах квот и компрометация телефона не устранены.

Отзыв будущего доступа, удаление активных relay bytes и удаление локальной
копии получателя различны. Route закрывается сразу; большие очереди чистятся
порциями, backups подчиняются Cloudflare retention. Уже расшифрованную или
экспортированную информацию гарантированно отозвать нельзя. Restore требует
внешней смены RECOVERY_EPOCH и повторного применения отдельного operator ledger:
автоматически обнаружить откат самой D1 по данным этой же D1 невозможно.

## Измерение стоимости и ограничения пилота

Локальный D1 meta snapshot в `relay/test/__snapshots__/relay.test.ts.snap`:
upload 8 КиБ — 21 rows written, receiver ACK — 19, duplicate upload — 12,
cancel/revoke — по 15. Empty fetch — 0 writes/3 statements; fetch 8 КиБ —
0 writes/3 statements, response 11100 bytes. Read counts зависят от index state;
snapshot использует next-power-of-two upper buckets, тест ограничивает каждый
измеренный запрос 64 reads. Snapshot не является тарифным измерением production.

Полный upload+ACK уже стоит 40 writes до последующего GC nonces/tombstones.
При 20 envelopes/day ×100 пользователей это ≥80000 writes/day до cleanup и
control traffic: прежние 12–20 writes/envelope были занижены. Ориентир пилота
снижен до 50 участников до production meta/CPU и проверки 30% запаса бюджета.
Технический предел 100 registrations — защита, а не обещанная Free ёмкость.
Bounded cron, row caps и rate limit не гарантируют доступность при исчерпании
account-wide D1/Workers quota; клиент обязан сохранять очередь и ждать.

Реальный CPU p95 (Free 10 ms), deployed rate limiting, операторский ledger,
recovery rehearsal и нагрузка на staging не проверены. Это обязательные gates
перед публикацией, не причины выполнять remote deploy без разрешения.

## Следующий шаг

Этап 5: consent-gated HTTPS transport, bounded/cancellable IO, WorkManager,
durable retry/fetch checkpoints и проверки switch/delete/offline/429/5xx.
Новых UI, автоматического Bluetooth/BLE и публичной регистрации нет.

Источники API: [Tink setup](https://developers.google.com/tink/setup/java),
[Tink hybrid](https://developers.google.com/tink/hybrid),
[D1 atomic batch](https://developers.cloudflare.com/d1/worker-api/d1-database/),
[Workers rate limit binding](https://developers.cloudflare.com/workers/runtime-apis/bindings/rate-limit/),
[D1 pricing](https://developers.cloudflare.com/d1/platform/pricing/),
[Workers limits](https://developers.cloudflare.com/workers/platform/limits/).
