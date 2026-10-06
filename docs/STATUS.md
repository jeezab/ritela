# Точка продолжения

2026-10-06. Исправлена гонка в PeriodViewModelTest, соответствующая таймауту editingAndDeletingRecalculateForecastFromRoom на скриншоте упавшего CI. Проверки выполнены локально. Push и новый GitHub Actions run не выполнялись.

## Результат

- Тест ожидал только изменённые даты из Room. Invalidation Flow может обновить их до того, как coroutine edit завершится и ViewModel сбросит saving. Следующий delete тогда отклоняется защитой повторных действий, и ожидание удаления заканчивается таймаутом.
- awaitPersistedState теперь ждёт ожидаемые данные вместе с saved=true/saving=false перед следующим действием; при problem тест сразу показывает ошибку записи. Таймаут 5000 мс сохранён. Production код, прогноз, БД и UI не менялись.
- docs/TESTING.md и DECISIONS фиксируют правило двух сигналов и команду повторного запуска только тестовой задачи через --rerun без пересборки зависимостей. Отдельный stress-скрипт для разовой проверки не добавлялся.
- Obtainium и release pipeline из 3ea97bf сохранены: ссылка README, версия 0.2.0/code 2000, signed release по тегу с draft upload/checksum. Production signing secrets и первый опубликованный Release остаются впереди; инструкция в docs/RELEASES.md. Предыдущая UI-доработка в d4b31f4.

## Проверки

- scripts/gradle.ps1 formatKotlin — PASS.
- scripts/gradle.ps1 testDebugUnitTest --tests app.ritela.PeriodViewModelTest --rerun-tasks — PASS, 4 tests. Первый диагностический запуск пересобрал задачи.
- Пять дополнительных последовательных запусков testDebugUnitTest --tests app.ritela.PeriodViewModelTest --rerun — все PASS, по 4 tests; test task действительно исполнялась, не UP-TO-DATE/FROM-CACHE.
- scripts/gradle.ps1 checkKotlin assembleDebug testDebugUnitTest lintDebug — BUILD SUCCESSFUL. Полный набор: 56 tests, 0 failures/errors; lint 0 ошибок/предупреждений, 9 informational hints. В последнем полном прогоне проблемный тест прошёл за 0.048 с.
- scripts/verify-apk.ps1 — PASS: signed app.ritela, API 26–37, без сетевых permissions. Debug APK 11 796 995 байт; SHA256 969053f63bcc0de9a9a28b5cd98deed478949e886d5ead18c8c74242b0dcd97f.
- scripts/check-workspace.ps1, scripts/check-health-content.ps1, python scripts/release.py check, пять Python release tests и git diff --check — PASS.

## Следующий шаг / ограничения

Отправить исправление в master по явному запросу и подтвердить новый GitHub Actions run, включая APK artifact. Скриншот показывает название теста и таймаут, но не полный stack trace/фазу ожидания; исходный сбой на Linux локально не воспроизводился. Найденная гонка устранена условием ожидания, шесть изолированных и один полный прогон прошли на Windows. Это не подтверждение удалённого CI.

Далее — production signing secrets/первый Release и проверка Obtainium на устройстве. Device smoke tests календаря/TalkBack/SAF backup и клинический review остаются впереди; JVM-снимки не подтверждают поведение на устройстве.
