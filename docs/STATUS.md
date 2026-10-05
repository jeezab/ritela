# Точка продолжения

Обновлено: 2026-10-05 (Europe/Moscow). M0 завершён, активный этап M1. После первого APK пользователь поставил качество интерфейса и текста в приоритет. MVP ещё не завершён.

## Реализовано

- Пункт 2 / M1: редактирование дат и снятие окончания через общую форму; удаление с подтверждением/отменой. DAO редактирует в транзакции, исключая себя из overlap; сохраняются UUID/createdAt. SQL finish защищён от смены начала на дату после окончания. Схема v1 не менялась. Полный format/build/test/lint PASS, 17 tests (6 UI, 6 repository, 3 domain, 2 ViewModel). Проверены сохранение metadata, конкурентные правки, отсутствие изменений при ошибке и UI edit → recreation → reopen → delete/cancel/confirm. Просмотрены новые period-edit/period-delete и обновлённые home PNG; галерея содержит 11 снимков.
- Доработка пункта 1: форма прокручивается, кнопки дат на всю ширину с min height 56dp; окончание можно снять кнопкой «Ещё идут». Выбор дат ограничен сегодняшним днём и началом записи. Смена поля убирает старую ошибку. В summary/history показывается весь диапазон. Проверено тем же полным Android набором: 13 tests, build/lint/format PASS; просмотрены обновлённые PNG, галерея обновлена.
- Пользователь подтвердил успешный CI после cdd675b и скачал APK. История исправлений Java/SDK в DECISIONS. Удалённый запуск нового UI/галереи ещё не подтверждён.
- Добавление начала/необязательного окончания менструации, завершение открытой записи, история последних 30 записей. Room 2.8.5, KSP 2.3.12, Lifecycle 2.11.0; schema v1, UUID/epoch days/createdAt/updatedAt. Нет destructive fallback.
- Future/reverse/overlap validation; проверка пересечений и вставка в транзакции, защита от конкурентных дубликатов. ViewModel/StateFlow, сохранение формы, loading/saving/errors без медицинских логов.
- Первый проход по дизайну: «Сегодня» и дата, выделенная текущая/последняя запись, основное действие, компактная история. Слоганы и повторяющийся privacy block удалены. Светлая/тёмная палитры, общие shapes/typography; системные dynamic colors сохранены.
- Форма «Месячные», понятные подписи дат, пояснение необязательного окончания, короткие ошибки. Исследование Clue/Flo/drip по официальным материалам, дефекты прежнего текста и план: docs/UI_DESIGN.md. Установленные версии конкурентов не проверялись.
- scripts/preview-ui.ps1: обновление Compose-снимков и HTML-галерея на компьютере без APK/телефона. 9 PNG, синтетические данные, timestamps, полноразмерные изображения. CI формирует ту же галерею внутри verification-reports. Добавлены IDE previews.
- FLAG_SECURE, backup/transfer exclusions и отсутствие сетевых разрешений сохранены. Room в приватной директории, пока без шифрования. 3 project-local skills; routine preview реализован скриптом.

## Проверки текущего шага

- Применён ritela-android-check. scripts/gradle.ps1 formatKotlin: PASS.
- scripts/gradle.ps1 checkKotlin assembleDebug testDebugUnitTest lintDebug: BUILD SUCCESSFUL, configuration cache reused.
- 13 тестов, 0 failures/errors: 5 UI, 3 domain, 3 Room/repository, 2 ViewModel. UI: privacy flags, add → Activity recreation → finish, empty light/dark, recorded/ongoing dark, 320dp/fontScale 2.0 и видимость главного действия.
- Просмотрены все 9 PNG в app/build/reports/screenshots/. На шрифте 200% экран прокручивается; вторичное действие под заголовком истории, чтобы не теснить его.
- scripts/preview-ui.ps1 -SkipRender: PASS. HTML UTF-8, 9 существующих local image links. Скрипт с UTF-8 BOM для совместимости Windows PowerShell 5.1/pwsh. Обычный режим принудительного HomeScreenTest проверен отдельно.
- scripts/verify-apk.ps1: PASS, signed app.ritela, API 26..37, no network permissions. APK: app/build/outputs/apk/debug/app-debug.apk, 12 429 250 байт, SHA-256 d18b02b8b05426f10c4c2f8600b009849b58c6aeb7ebe4b167aee707c597305b; рядом .sha256.
- Workflow YAML/gallery step: PASS. scripts/check-workspace.ps1: PASS, 16 документов, 3 skills/hash. git diff --check: PASS.

## Следующее действие

Пункты 1 и 2 проверены; продолжить M2: отдельный domain prediction engine, календарь и запись выбранного дня. Прогноз — по завершённым интервалам между началами, с явно описанными эвристиками, достаточностью данных и растущей неопределённостью до 12 циклов. Смотреть states/themes/sizes/fontScale и фиксировать результаты в доках/коммитах.

После push подтвердить CI нового UI и наличие screenshots/index.html в распакованном verification-reports. Старый успешный CI не доказывает проверку текущего workflow.

## Ограничения

Это первый проход, не завершённый дизайн всего продукта. Нет прогноза, календаря, дневной интенсивности/симптомов, экспорта/импорта, app lock и шифрованного backup. Главная показывает последние 30 записей. Миграций пока нет; изменение schema v1 требует migration/tests. Эмулятор/Android Studio не настроены в workspace; device smoke test, TalkBack и fontScale 1.3 пока не проверены. Галерея статическая; для нажатий нужен эмулятор или устройство. Debug APK не является release/MVP.
