# Точка продолжения

2026-10-06. Подготовлена поддержка Obtainium и выпуск подписанного APK через GitHub Releases. Изменения сохраняются локальным коммитом; push, тег и публикация не выполнялись.

## Результат

- README: HTTPS-ссылка «Добавить в Obtainium» с конфигурацией app.ritela, названием Ritela, автором jeezab и URL репозитория. Есть ссылка на Releases и инструкция первого выпуска. Формат проверен по официальной wiki Obtainium.
- version.properties: единая версия 0.2.0. Gradle читает её для debug/release; Android versionCode = major × 1000000 + minor × 1000 + patch, сейчас 2000, выше прежнего кода 1. Неоднозначные версии и компоненты за допустимыми границами отклоняются.
- release.yml: запуск по тегу v*, точное совпадение с версией, отказ от повторного/старого стабильного выпуска, постоянная подпись из пяти secrets. Полная сборка debug/release, unit/Compose tests и оба lint. Проверяются package, versionName/versionCode, отсутствие debug-флага/сетевых разрешений и SHA256 сертификата. Один универсальный APK и checksum загружаются в draft Release; публикация выполняется только после успешной загрузки. Прерванный draft можно продолжить повторным запуском.
- scripts/release.py: check/link/prepare без сторонних Python-пакетов. scripts/test_release.py: пять regression tests, включая ошибочные теги, старую версию, неверную подпись, debug APK, неправильные metadata и сетевые permissions. Обычный CI также проверяет версию/ссылку и запускает эти тесты.
- docs/RELEASES.md: настройка production keystore/secrets, повторяемые выпуски, приватный GitHub token для Obtainium и переход с прежнего debug APK через backup. Карта и решения обновлены. Production ключ не создан и в Git не добавлен.
- Предыдущая UI-доработка сохранена в d4b31f4: свободный вертикальный календарь, day sheet, блокировка занятых дат, polish главной/settings, EN/RU и исходный промпт. UI, Room и медицинский контент в этом шаге не менялись. На старте рабочее дерево было чистым; прежних незакоммиченных черновиков RU уже не было.

## Проверки

- python scripts/release.py check — PASS: v0.2.0, versionCode 2000, точная Obtainium-ссылка в README.
- python -m unittest discover -s scripts -p 'test_release.py' — 5 tests PASS.
- scripts/gradle.ps1 formatKotlin — PASS.
- scripts/gradle.ps1 checkKotlin assembleDebug testDebugUnitTest lintDebug assembleRelease lintRelease — BUILD SUCCESSFUL. 56 Android/JVM/Compose tests, 0 failures/errors. Debug и release lint: 0 ошибок/предупреждений, по 9 informational hints.
- Signing plumbing проверен с существующим **тестовым debug-ключом**, не production. keytool -exportcert → SHA256 независимого сертификата → release.py prepare — PASS. Результат только в app/build/test-distribution/ritela-0.2.0.apk и .sha256; не опубликован и не предназначен для распространения.
- scripts/verify-apk.ps1 для debug и release — PASS: app.ritela, API 26–37, без сетевых permissions. Debug: 11 796 995 байт, SHA256 a62054997499bfcc8d848b0cd941da6be9c0b3d67928785fa37bd2e8f19d1ef4. Тестовый release: 8 240 476 байт, SHA256 0acb9d1ec1433b0b3c8da25808407a5cae9209cd2b7ac3d51c47a7a95bc5d444.
- actionlint 1.7.12 для обоих workflow — PASS; shellcheck недоступен и отключён только в локальном вызове actionlint. Проверяется структура Actions и выражения, удалённый запуск не выполнялся.
- scripts/check-workspace.ps1, scripts/check-health-content.ps1, git diff --check — PASS. 24 документа, 6 зарегистрированных skills; 18 существующих EN/RU карточек.

## Ограничения / следующий шаг

Следующий шаг: настроить пять signing secrets по docs/RELEASES.md, затем по явному запросу отправить master и v0.2.0, проверить Release и установку/обновление через Obtainium на устройстве. GitHub CLI локально не установлен; наличие удалённых secrets не подтверждено. До первого опубликованного Release Obtainium не сможет скачать APK по ссылке. Для закрытого репозитория требуется token в Obtainium с Contents: Read-only.

Обновление с APK другой подписи требует предварительного экспорта и перехода на постоянный release-ключ. Последующие версии должны использовать тот же ключ. Production-подпись, реальная установка поверх прошлой версии и удалённый pipeline ещё не проверены.

Ранее запланированные device smoke tests календаря/TalkBack/SAF backup и клинический review медицинского контента остаются впереди. JVM-снимки не подтверждают поведение на устройстве.
