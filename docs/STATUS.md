# Точка продолжения

Обновлено: 2026-10-05 (Europe/Moscow). Активный этап: M1. Реализован и проверен базовый сценарий записи менструального периода. MVP ещё не завершён.

## Реализовано

- M0: Android skeleton и CI workflow; коммит `8d727f6`. CI подготовлен, удалённого запуска/push не было.
- Добавление периода с началом и необязательным окончанием; продолжающийся период можно завершить позже. История показывает последние 30 записей.
- Room 2.8.5, KSP 2.3.12, Lifecycle 2.11.0; схема v1 в `app/schemas/`. UUID, calendar epoch days, createdAt/updatedAt. Нет destructive fallback.
- Будущие/обратные даты отклоняются. Проверка пересечения и вставка выполняются в транзакции; конкурентные дубликаты не создают второй записи.
- ViewModel/StateFlow, сохранение состояния формы, индикация загрузки/сохранения и понятные ошибки без логирования данных.
- FLAG_SECURE, backup/transfer exclusions и отсутствие сетевых разрешений сохранены. База в приватной директории приложения, пока без шифрования.
- Новый project-local skill `ritela-android-check` фиксирует проверенный цикл format → build/test/lint → UI review → APK checks. Реестр/lock обновлены; всего 3 локальных скилла.

## Проверки

- `scripts/gradle.ps1 formatKotlin`: PASS.
- `scripts/gradle.ps1 checkKotlin assembleDebug testDebugUnitTest lintDebug`: BUILD SUCCESSFUL, configuration cache reused.
- 11 тестов, 0 failures/errors: 3 UI, 3 domain, 3 Room/repository, 2 ViewModel. Покрыты DST/leap-day, ошибочные даты, overlap/concurrent insertion, закрытие/открытие базы и add → Activity recreation → finish.
- `scripts/verify-apk.ps1`: подпись, package/min/target SDK и отсутствие сети — PASS. APK: `app/build/outputs/apk/debug/app-debug.apk`, 11 941 066 байт. SHA-256: `a7500e9676414622d965ad68a61d9fbf23fc47cd55d31abb677712c0d1b021d1`; файл рядом в `.sha256`.
- UI PNG просмотрены: home-light/home-dark/home-recorded/period-entry/period-finish в `app/build/reports/screenshots/`. Для диалогов применяется захват decorView: Robolectric PixelCopy иначе снимал фон Activity.
- `scripts/check-workspace.ps1`: PASS, 15 документов, 3 скилла/их hash. Новый skill прошёл skill-creator validator. Workflow YAML и `git diff --check`: PASS.
- Production source audit: прямые Log/println вызовы отсутствуют. Синтетические тестовые записи не входят в APK или Git.

## Следующее действие

Продолжить M1: редактирование и удаление периодов с проверкой пересечений, сохранением UUID/createdAt и подтверждением удаления. Добавить regression/UI tests для коррекции и удаления. Затем M2: pure Kotlin prediction engine, календарь и главная сводка. Читай релевантные разделы brief через `scripts/read-brief.ps1`, а не полный промпт.

## Ограничения

Нет прогноза, календаря, коррекции/удаления, интенсивности/дневных событий, экспорта/импорта, app lock и зашифрованного backup. История на главном экране ограничена 30 записями. Миграций пока нет (начальная schema v1); дальнейшие изменения требуют migration tests. Устройств/emulator нет, device smoke test не выполнялся. CI не проверен удалённо. Debug APK не является release/MVP.
