# Точка продолжения

Обновлено: 2026-10-05 (Europe/Moscow). M0 локально проверен; CI подготовлен, удалённый запуск ещё не выполнялся. Следующий этап: M1, Room и запись менструации.

## Сделано

- Android app.ritela: Kotlin/Compose/Material 3, нейтральная светлая/тёмная тема, пустая история, edge-to-edge.
- FLAG_SECURE, отключённые backup/cloud/device transfer; INTERNET и аналитика отсутствуют.
- Gradle wrapper с SHA-256, version catalog, cache, ktlint, скрипты SDK/Gradle/APK verification.
- SDK 37.0, build-tools 36.0.0, platform-tools 37.0.1. AGP 9.4.1, built-in Kotlin/Compose Compiler 2.2.10, BOM 2026.09.00, Activity 1.13.0, Robolectric 4.17. minSdk 26, compile/targetSdk 37.
- GitHub Actions с закреплёнными SHA: build, unit tests, lint, format check, debug APK/checksum/reports artifacts. Push не выполнялся.

## Проверки

- `scripts/bootstrap-toolchain.ps1 -VerifyOnly`: PASS.
- `scripts/setup-sdk.ps1 -AcceptLicenses`: PASS; `adb devices`: устройств нет.
- `scripts/gradle.ps1 formatKotlin`: PASS.
- `scripts/gradle.ps1 checkKotlin assembleDebug testDebugUnitTest lintDebug`: BUILD SUCCESSFUL, 2 теста, 0 failures/errors. Lint сохраняет уведомления об обновлениях версий как informational; ошибки/предупреждения кода остаются обязательными.
- `scripts/verify-apk.ps1`: подпись, package/min/target SDK, отсутствие сети — PASS. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`, 11 585 606 байт; SHA-256 рядом в `.sha256`.
- `scripts/check-workspace.ps1`: PASS, 13 документов и 2 скилла. Workflow YAML разбирается без ошибок. `git diff --check`: PASS.
- Светлый/тёмный UI отрендерен Robolectric и просмотрен: `app/build/reports/screenshots/home-light.png`, `home-dark.png`.

## Следующее действие

M1: добавить Room с exportSchema/KSP, модель периода с LocalDate и стабильным UUID, проверку дат, запись начала/окончания и отображение истории. Проверить хранение и поведение UI автоматическими тестами, повторить build/lint/format/APK checks, обновить документы и коммитить законченный шаг.

## Ограничения

Сейчас APK — стартовый экран: пользовательские записи и прогноз ещё не реализованы. Устройство/emulator не подключены; JVM render не считается device smoke test. CI до push не проверен удалённо. Debug APK не является готовым release/MVP.
