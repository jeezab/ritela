# Этап 8: надёжность и допуск к пилоту

2026-10-16. Локальные проверки пройдены; это не допуск к публичному выпуску.
Этапы 6/7 имеют незавершённые функции; production E2E и физическое обновление
на двух телефонах не проверены. Итог запуска и APK — в STATUS.

## Повторение проверок

Android: `formatKotlin`, затем `checkKotlin assembleDebug testDebugUnitTest lintDebug`
с фильтрами `app.ritela.Sync*Test`, `app.ritela.Relay*Test`,
`app.ritela.HttpRelayTransportTest`, `app.ritela.Partner*Test`,
`app.ritela.UserProfilesTest`, `app.ritela.DayLogBackupTest`.
Локально: `-Pritela.skipScreenshots=true --max-workers=1 --no-parallel`,
JVM `-Xmx1536m -Dfile.encoding=UTF-8`. UI/эмулятор не запускать.

В `relay/`: `npm ci --ignore-scripts`, `npm run check`, `npm test`, `npm run build`.
Build — только Wrangler dry-run; D1-тесты — local workerd с synthetic bindings.
В корне: `python -m unittest discover -s scripts -p test_release.py`, APK verifier,
workspace/diff checks и actionlint. Никаких cloud credentials не требуется.

Новый Relay workflow проверяет relay на Node 24; Release workflow повторяет
проверки до signing. Общий APK verifier работает с локальным Windows SDK или
ANDROID_HOME runner, требует network permissions и cleartext=false.
Release verifier также сохраняет проверки version/debug/certificate.
Статическая проверка APK не заменяет runtime consent tests.

## Матрица восстановления

- `SyncMigrationTest`: все 1…10→11, значения/BLOB/defaults off, fresh/migrated DDL,
  rollback/retry ошибки DDL. `SyncCompatibilityTest`: legacy keys/контакты/packets,
  квитанции/delayed Doodles/профили. Room остаётся 11, destructive fallback нет.
- `RelayRuntimeTest`: потерянный PUT/reopen повторяет ID/bytes; потерянный storage
  ACK не теряет apply/E2E receipt. STORED не означает DELIVERED. Это reopen БД,
  не физическое убийство Android-процесса.
- `SyncReceiveTest`: неполный/переставленный baseline переживает reopen, полный
  применяется атомарно; collision/повреждение не меняют проекцию; gaps/старый UPSERT
  не обходят tombstones. Старый автор/restore без recovery не принимаются.
- Runtime/queue tests: оба порядка manual/HTTP, первый receivedAt и replay после
  clear, oversized legacy Doodle сохраняет ручной обмен. Отзыв отменяет pending
  work, сохраняет историю; gates проверяют origin/profile/consent, HTTP завершается
  до закрытия БД. Очистка relay не гарантирует стирание ранее полученного.
- Relay tests: CAS transfer с двумя подписями/конкурентами; restore epoch закрывает
  доступ старым devices. Android transfer flow generation >1 ещё не реализован;
  реальный D1 Time Travel не запускался.
- HTTP/runtime/worker tests: Retry-After/cancellation/constraints/continuation,
  отсутствие HTTP/key creation до согласия. Физический Doze требует испытания.

## Повреждения и нагрузка

Новый RelayCryptoTest с фиксированным seed отвергает 32 bit flips и шесть усечений
ciphertext, затем принимает оригинал. Это bounded corpus, не доказательство
криптографической безопасности и не coverage-guided fuzzing.
Relay tests отвергают 24 malformed control bodies и превышение 16 KiB без изменения
relay_state; ответы содержат только error code/no-store. Смешанный ACK с чужим
маршрутом откатывает пачку и сохраняет корректный пакет.
Burst из 36 пакетов по 8 KiB проходит две cursor pages без дублей/потерь:
≤512 KiB/32 packets на страницу, 0 fetch writes, ≤512 local rows read.
После ACK payload bytes равны нулю; tombstones остаются по retention.

Snapshot хранит числовые верхние границы rows read/response bytes, statements и
rows written. Точное число байтов меняется с числом цифр AUTOINCREMENT cursor.
Локальные reads/writes/statements: empty fetch ≤4/0/3; upload 8 KiB ≤32/21/14;
duplicate ≤32/12/14; fetch 8 KiB ≤8/0/3; ACK ≤32/19/11; cancel ≤16/15/11;
revoke ≤16/15/12. Это бюджет fixtures, не гарантия production плотности/индексов.
SQLite duration/время suite не равны billed CPU p95. CPU Free, реальные rows/bytes,
egress и бюджет аккаунта требуют отдельного измерения; polling не увеличивается.

Поиск Log.*, println, printStackTrace и console logging в sync/domain/relay не
обнаружил sinks. Relay не логирует exception objects с параметрами D1. Это
ограниченная проверка исходников, не независимый review библиотек/инфраструктуры.

## Обновление и backout

Перед испытанием сохранить записи защищённым backup и identity export отдельно,
проверить чтение synthetic копии. Backup не переносит relay keys/queue/авторство.
Старый телефон не стирать до проверки сохранности. Подпись APK должна совпадать
с установленной; debug APK не обновит production с другим сертификатом.
Backout: выключить Sync, сохранить записи/историю, выпустить исправление с большим
versionCode и поддержкой Room 11. Старый APK поверх новой схемы, очистка app data
и destructive downgrade недопустимы. Пустой build origin отключает доступность
relay без удаления данных. Restore D1 — по operator ledger/epoch из relay README.

До допуска пилота отдельно проверить на двух физических устройствах: signed
install/update с сохранением профилей/контактов/ключей/ручного обмена/Doodles,
две активации и два setup roundtrip, synthetic DATA/Doodle/ACK/OPENED,
airplane mode/restart/retry, выключение/отзыв/backout. Секреты/payload не включать
в отчёт. Устройство/экран — только после нового явного запроса пользователя.
Незавершённые этапы 6/7, independent crypto review, CPU/quotas и device matrix
не считаются пройденными автоматически.
