# Точка продолжения

Обновлено: 2026-10-05 (Europe/Moscow). Активный этап: M1. Реализован и проверен базовый сценарий записи менструального периода. MVP ещё не завершён.

## Реализовано

- M0: Android skeleton и CI workflow; коммит `8d727f6`. Первый CI упал в setup-java; формат версии исправлен на `17.0.20+101` коммитом `550784f`. Второй CI успешно установил Java, но упал в Prepare SDK: `sdkmanager: command not found`. Теперь sdkmanager вызывается по полному пути внутри ANDROID_HOME с явным SDK root и проверкой executable. Новый удалённый запуск ожидается.
- Добавление периода с началом и необязательным окончанием; продолжающийся период можно завершить позже. История показывает последние 30 записей.
- Room 2.8.5, KSP 2.3.12, Lifecycle 2.11.0; схема v1 в `app/schemas/`. UUID, calendar epoch days, createdAt/updatedAt. Нет destructive fallback.
- Будущие/обратные даты отклоняются. Проверка пересечения и вставка выполняются в транзакции; конкурентные дубликаты не создают второй записи.
- ViewModel/StateFlow, сохранение состояния формы, индикация загрузки/сохранения и понятные ошибки без логирования данных.
- FLAG_SECURE, backup/transfer exclusions и отсутствие сетевых разрешений сохранены. База в приватной директории приложения, пока без шифрования.
- Новый project-local skill `ritela-android-check` фиксирует проверенный цикл format → build/test/lint → UI review → APK checks. Реестр/lock обновлены; всего 3 локальных скилла.

## Проверки

- Исправление Prepare SDK: YAML parsing и Bash syntax всех run-блоков — PASS. Выполнен фактический блок Prepare SDK в Git Bash с временным макетом SDK вне PATH: путь с пробелами, точные аргументы SDK root/platform/build-tools, остановка при отсутствии ANDROID_HOME/инструмента и сохранение exit code 42 установщика — PASS. Это проверка shell-вызова, не реальная установка Linux SDK. `scripts/check-workspace.ps1` и `git diff --check` — PASS. Код приложения не менялся.
- Исправление CI: workflow YAML — PASS; SemVer 7.8.4 из lock закреплённого setup-java отклоняет старый input и принимает `17.0.20+101`. Adoptium API подтвердил точное соответствие релизу `jdk-17.0.20.1+1`, Linux x64 JDK. Integrity npm-архива проверена по upstream lock. `scripts/check-workspace.ps1` и `git diff --check` — PASS. Код приложения не менялся; следующие проверки приложения относятся к предыдущему шагу M1.
- `scripts/gradle.ps1 formatKotlin`: PASS.
- `scripts/gradle.ps1 checkKotlin assembleDebug testDebugUnitTest lintDebug`: BUILD SUCCESSFUL, configuration cache reused.
- 11 тестов, 0 failures/errors: 3 UI, 3 domain, 3 Room/repository, 2 ViewModel. Покрыты DST/leap-day, ошибочные даты, overlap/concurrent insertion, закрытие/открытие базы и add → Activity recreation → finish.
- `scripts/verify-apk.ps1`: подпись, package/min/target SDK и отсутствие сети — PASS. APK: `app/build/outputs/apk/debug/app-debug.apk`, 11 941 066 байт. SHA-256: `a7500e9676414622d965ad68a61d9fbf23fc47cd55d31abb677712c0d1b021d1`; файл рядом в `.sha256`.
- UI PNG просмотрены: home-light/home-dark/home-recorded/period-entry/period-finish в `app/build/reports/screenshots/`. Для диалогов применяется захват decorView: Robolectric PixelCopy иначе снимал фон Activity.
- `scripts/check-workspace.ps1`: PASS, 15 документов, 3 скилла/их hash. Новый skill прошёл skill-creator validator. Workflow YAML и `git diff --check`: PASS.
- Production source audit: прямые Log/println вызовы отсутствуют. Синтетические тестовые записи не входят в APK или Git.

## Следующее действие

После отправки исправления проверить новый запуск GitHub Actions: SDK, build/tests/lint и APK artifacts. Во втором запуске Java установлена успешно, следующие шаги после Prepare SDK пока не выполнялись.

Продолжить M1: редактирование и удаление периодов с проверкой пересечений, сохранением UUID/createdAt и подтверждением удаления. Добавить regression/UI tests для коррекции и удаления. Затем M2: pure Kotlin prediction engine, календарь и главная сводка. Читай релевантные разделы brief через `scripts/read-brief.ps1`, а не полный промпт.

## Ограничения

Нет прогноза, календаря, коррекции/удаления, интенсивности/дневных событий, экспорта/импорта, app lock и зашифрованного backup. История на главном экране ограничена 30 записями. Миграций пока нет (начальная schema v1); дальнейшие изменения требуют migration tests. Устройств/emulator нет, device smoke test не выполнялся. Успешного полного удалённого CI пока нет; второй запуск упал в Prepare SDK до сборки. Debug APK не является release/MVP.
