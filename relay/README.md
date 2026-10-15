# Ritela Relay: локальный закрытый пилот

Этап 4 не развёрнут. `wrangler.jsonc` содержит только фиктивный local D1 ID,
`workers_dev=false`, выключенные observability/metrics. Рабочая D1 пользователя
не подключена. В репозитории нет production secrets и скрипта deploy.

Локальные проверки (Node 24, PowerShell):

```powershell
cd relay
npm.cmd ci --ignore-scripts
npm.cmd run check
npm.cmd test
npm.cmd run build
npm.cmd audit
```

`build` — только `wrangler deploy --dry-run`. Vitest поднимает изолированную D1
с синтетическими ключами; `test/measure.ts` измеряет локальные D1 meta.
Не запускать команды remote migration/deploy как часть этих проверок.

Перед будущим отдельным разрешённым развёртыванием оператору необходимо:

1. Проверить назначение D1, создать backup и применить `migrations/0001_relay.sql`.
2. Установить независимые случайные `CAPABILITY_PEPPER` (не менее 32 символов)
   и `RECOVERY_EPOCH` (не менее 16), через secret storage, без логов/коммитов.
   Установить ту же epoch в единственной строке `relay_state`. До этого API закрыт.
3. Создать криптографически случайные 256-bit activation tokens вне репозитория.
   В D1 записывать только SHA-256 lowercase hex, срок действия; выдавать token
   пользователю защищённым каналом. Публичного mint/registration UI нет.
4. Настроить отдельный защищённый журнал revocations/deletions для восстановления.
   После Time Travel закрыть доступ, сменить внешнюю epoch, повторно применить
   журнал и обновить D1 epoch. Старые devices не должны ожить из snapshot.
5. Проверить реальные CPU p95, D1 meta и бюджет аккаунта. Локальные тесты не
   доказывают соблюдение Free 10 ms CPU. Начать с нескольких устройств;
   ориентир до 50 участников, хотя технический лимит регистраций — 100 за всё время.

API v1 использует подпись P-256/SHA-256 IEEE P1363 каждого запроса.
Точные canonical bytes — `src/auth.ts` и Android `RelayRequestAuth.kt`;
совместные публичные vectors — `test-vectors/api-signatures.json`.
Capabilities — отдельные случайные 256-bit значения; в D1 хранится их HMAC.
Body hash и capability hash включены в подпись. Окно часов ±5 минут,
mutation nonce 10 минут; fetch не пишет nonce. Новый timestamp требует нового
request UUID; delivery ID остаётся неизменным для идемпотентного retry.

Endpoints (все authenticated, кроме регистрации с proof of possession новой key):

- `POST /v1/devices`: `{handle, publicKey, activation}`; handle UUID генерирует клиент.
- `DELETE /v1/devices/{handle}`: только свой; повторное удаление допустимо.
- `POST /v1/routes`: receiver создаёт route для конкретного sender.
- `DELETE /v1/routes/{route}`: receiver или назначенный sender закрывает route.
- `POST /v1/routes/{route}/reserve`: receiver резервирует полный baseline budget.
- `PUT /v1/routes/{route}/packets/{id}`: immutable ciphertext, capability sender;
  query `class,expires,generation,grant[,reservation]` в этом порядке.
- `GET /v1/devices/{handle}/packets?cursor=0&limit=32`: только receiver,
  максимум 512 КиБ JSON; cursor — последний включённый relay sequence.
- `POST /v1/devices/{handle}/ack`: до 128 `{route,id}`; удаляет relay bytes,
  не является E2E-квитанцией доставки.
- `DELETE /v1/routes/{route}/packets/{id}`: receiver/sender; cancel-before-upload
  создаёт tombstone и запрещает последующее появление того же ID.
- `POST /v1/authorities/{owner}/initialize`: только owner, generation 1.
- `POST /v1/authorities/{owner}/transfer`: текущий author, две подписи старого
  и нового API key, CAS generation/digest; одной гонке разрешён один победитель.

Поля control bodies, точные guards и error codes определены соответствующими
`src/routes.ts`, `packets.ts`, `authorities.ts`. Все SQL values параметризованы.
Response/ошибки — `Cache-Control: no-store`, без исходных exceptions в логах.
429 возвращает `Retry-After: 60`; 401/403/409 требуют проверки состояния,
503 — паузы и повторной попытки. Нельзя менять delivery ID после timeout.

Лимиты: packet 256 КиБ, route 8 МиБ/256 packets, device 16 МиБ, вся очередь
300 МиБ/32768 packets. Baseline reservation до 6 МиБ/64 parts на 10 минут;
не обходит общие лимиты и резервирует также число rows. Обычные новые uploads
до 1 МиБ/device/day, mutations до 4096/day. Route и payload TTL до 7 дней,
tombstones 8 дней; истёкший route нельзя продлить или переиспользовать.
Продление связи требует нового route/capability. Nonces ≤32768, tombstones
≤65536: переполнение закрывает запись с backoff до bounded cleanup.
Rate limiter 60/min по edge IP и device, per-location/eventually consistent;
это защита нагрузки, а не глобальный финансовый счётчик. Общий NAT разделяет IP budget.

Отзыв немедленно закрывает чтение/запись route в транзакции. До 32 ciphertext
удаляются inline, остальные — bounded hourly cleanup (128 за проход), поэтому
срок физического удаления при большой очереди длиннее одного часа. Backup
retention и уже скачанные локальные копии не стираются этим endpoint.
Закрытые строки device/author fence сохраняются как deny ledger.

Модель угроз, криптографические ограничения и результаты —
[`RITELA_SYNC_STAGE_4.md`](../docs/architecture/RITELA_SYNC_STAGE_4.md).
