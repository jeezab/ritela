# Ritela Sync — этап 7, доступная локальная часть настроек

2026-10-16: этап 5 подключил настоящее opt-in/activation и manual setup. При
заданном HTTPS origin кнопка открывает отдельное согласие с password-полем кода
пилота; до подтверждения сеть не запускается. Без origin кнопка недоступна.
STORED runtime отображается как ожидание E2E receipt, без ложного delivered.
Baseline/scope/server deletion UI и last-success UX остаются отдельной работой.
Дальше сохранено описание первоначальной локальной части.

Добавлен вход «Синхронизация» в SettingsScreen и полный прокручиваемый диалог
`ui/SyncSettings.kt`. Сохранены HomeTheme/SettingsGroup, тёплая палитра и общий
sans-serif, кнопки не ниже 48 dp, EN/RU copy. Просмотр/генерация UI-снимков и
эмулятор не запускались по действующему запросу пользователя.

## Что доступно

По умолчанию показан режим «Только вручную». Файлы и ручной Bluetooth остаются
доступны. Internet и automatic nearby явно обозначены как недоступные; рабочего
переключателя, публичной регистрации и activation token entry нет. Конструктор
настроек и чтение очереди не создают consent rows, keys, jobs или HTTP requests.
Открытие настроек не заменяет согласия и не включает сеть.

`SyncSettingsRepository` привязан к конкретной ProfileSession. Новый DAO читает
до 100 строк metadata из message intents/record outbox с текущим именем именно
identity-получателя, без замены именем общей группы. Ожидающие строки выводятся
раньше отменённых/подтверждённых. В списке нет payload, подписанных URLs, IDs,
capabilities или сырых ошибок. Состав раскрывает frozen категории и наличие
текста/рисунка; сами значения, текст послания и изображение не выводятся.

Статусы различают ожидание, блокировку размера, передачу без E2E подтверждения,
подтверждённую доставку и отмену. READY/IN_FLIGHT в этой незавершённой версии не
показываются как реальная интернет-отправка. STORED не считается delivered;
флаг delivered/ACKED приходит из существующего authenticated receipt path.
Неизвестное состояние показывается как недоступный статус.

Отмена требует подтверждения. Message intent становится CANCELLED, исходный
PartnerMessage сохраняется для ручного обмена. Для record outbox действие
приостанавливает auto-send конкретному peer/device и отменяет только его
непереданные записи; checkpoint требует нового baseline при последующем setup.
Другие адресаты и независимые послания не затрагиваются. STORED строки/bytes
остаются: локальная операция не подтверждает удаление уже переданных копий.

Если в профиле ранее сохранён internet flag, доступен явный отзыв этого согласия.
В одной Room-транзакции flag становится 0, ожидающие message intents отменяются,
непереданные record leases/bytes отменяются и progress инвалидируется. Scope,
ручные grants, собственные записи и контакты не стираются. Ошибка откатывает
flag и обе очереди вместе. Состояние сохраняется после reopen. Никаких скрытых
cleanup requests после выключения не выполняется.

Три разных действия объяснены отдельно: прекращение будущего доступа; удаление
ожидающих пакетов с relay (требует будущего отдельного authenticated запроса);
локальные копии получателя. Нет обещания стереть уже скачанное/экспортированное
или серверные резервные копии. Кнопки server deletion сейчас нет: транспорт и
подтверждение очистки не подключены.

## Границы и следующий шаг

Room остаётся v10: entity/schema/migration не менялись, добавлены только DAO
queries и profile-scoped repository. Identity/vault/legacy manual protocol,
контакты и saved Doodles не изменены. INTERNET permission по-прежнему отсутствует.

Этап 7 целиком не завершён. После runtime этапа 5 и автоматической части этапа 6
нужны реальный независимый consent flow, pilot activation/setup, per-peer auto
toggles/frozen scopes, точный baseline/tag preview, времена server check/E2E
update, pending deletion/retry, queue paging/GC и UX общего byte budget.
Пока этих компонентов нет, интерфейс не создаёт впечатления работающего Sync.
Для enable consent нужен отдельный подтверждённый пользовательский выбор;
наличие контактов/старой очереди или token не является согласием.

Проверки кода: `SyncSettingsTest` — no implicit consent, metadata-only snapshot,
атомарный disable/rollback/reopen, раздельные cancel/pause, profile/peer isolation,
неизвестные/подтверждённые состояния. `SyncSettingsUiTest` — EN/RU, вход/выход,
disabled internet, отмена с отказом/подтверждением, privacy copy, состав без
plaintext и scroll reachability 320 dp/fontScale 2.0. Тесты не создают PNG и не
подменяют визуальную/устройственную/TalkBack проверку.

Финальные проверки части: formatKotlin, checkKotlin, assembleDebug, lintDebug и
20 выбранных tests (7 settings/storage, 4 Sync UI, 8 message queue, 1 profile UI)
прошли; 0 failures/errors/skipped. Повтор после scroll confirmation — также 20 PASS.
Lint 0 errors/0 warnings/9 прежних hints, signed APK без network permissions.
Проверены parity всех 34 EN/RU keys, workspace/diff/staged checks. UI/PNG/эмулятор
не просматривались; реальная сеть и publish/push не выполнялись.

Room invalidation Flow: [официальный API](https://developer.android.com/reference/androidx/room/InvalidationTracker).
