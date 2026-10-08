# Выпуски и Obtainium

Источник: `https://github.com/jeezab/ritela`, package ID `app.ritela`. Ссылка в README передаёт ID, название, автора и URL через официальный HTTPS redirect. Токен в ссылке не хранится.

Obtainium читает GitHub Releases, а не Actions artifacts. Каждый стабильный выпуск содержит один универсальный `ritela-<version>.apk` и checksum рядом. Не нужно выбирать архитектуру, включать отслеживание по дате или отключать распознавание версии. Тег `v0.2.1` соответствует Android versionName `0.2.1`; префикс `v` Obtainium распознаёт.

## Однократная настройка подписи

Используй постоянный production keystore, сохрани резервную копию и пароли отдельно от репозитория. Если ключа ещё нет, создай локально интерактивно; пароль не передаётся в командной строке:

```powershell
& ./.toolchain/jdk-17.0.20.1+1/bin/keytool.exe -genkeypair -keystore .toolchain/ritela-release.jks -alias ritela -keyalg RSA -keysize 4096 -validity 10000
& ./.toolchain/jdk-17.0.20.1+1/bin/keytool.exe -list -v -keystore .toolchain/ritela-release.jks -alias ritela
```

В GitHub → Settings → Secrets and variables → Actions добавь пять repository secrets:

| Secret | Значение |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Base64 содержимого постоянного keystore |
| `ANDROID_KEYSTORE_PASSWORD` | Пароль keystore |
| `ANDROID_KEY_ALIAS` | Alias, например `ritela` |
| `ANDROID_KEY_PASSWORD` | Пароль ключа; для PKCS12 обычно совпадает с паролем keystore |
| `ANDROID_SIGNING_CERT_SHA256` | SHA256 сертификата из `keytool -list -v`, допускаются двоеточия |

Получить base64 в буфер обмена, не выводя секрет в консоль:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Join-Path (Get-Location) '.toolchain/ritela-release.jks'))) | Set-Clipboard
```

После сохранения secret очисти буфер: `Set-Clipboard -Value ''`. Keystore, пароли и base64 не добавляются в Git. Локальная сборка принимает `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`; без них debug работает как раньше, release остаётся неподписанным и не пройдёт упаковку для публикации.

## Повторяемый выпуск

### Перевыпуск 0.3.0 с текущим UI (явный запрос 2026-10-08)

После первого перевыпуска Release APK упал в устаревшем UI-тесте (`previous-month` после удаления стрелок Calendar). Тест исправлен, локальный тег перенесён на исправленный коммит. На момент проверки GitHub Release v0.3.0 отсутствует (authenticated API 404); в этом состоянии команда `gh release delete` ниже не нужна. Выполни `git push origin master`, `git push origin --delete v0.3.0`, затем `git push origin refs/tags/v0.3.0`. Это создаст новый запуск на исправленном коде; Re-run старого запуска сохранит ошибку.

Это исключение из обычного правила неизменности опубликованных версий, запрошенное пользователем. Android версия остаётся `0.3.0` / code `3000`; включены новый Home, Calendar/Settings на дизайн-системе Home, вертикальный Calendar с двухбуквенными EN/RU днями недели и плоская интерактивная орбита. Старый удалённый `v0.3.0` и master на момент подготовки указывают на `3022067f32eaea91a12cf41c5b6267d46a094028`. Старый GitHub Release опубликован 2026-10-08; новый локальный тег подготовлен на коммите с этой инструкцией и всеми UI-изменениями. Изменение локального тега само по себе ничего не публикует.

Сначала отправь ветку, затем удали **GitHub Release и удалённый тег**, затем отправь подготовленный локальный тег. Запускай команды последовательно, продолжая только после успешного завершения каждой:

```powershell
git push origin master
gh release delete v0.3.0 --repo jeezab/ritela --yes
git push origin --delete v0.3.0
git push origin refs/tags/v0.3.0
```

`gh release delete` без `--cleanup-tag` удаляет Release и его assets, сохраняя тег до следующей команды ([официальная документация](https://cli.github.com/manual/gh_release_delete)). Если `gh` не установлен (как в текущей локальной среде), вместо второй команды удали выпуск через [страницу Releases](https://github.com/jeezab/ritela/releases/tag/v0.3.0) → Delete, оставив тег; затем выполни две последние Git-команды. Удаление только тега недостаточно: проверка release.py отвергает существующий стабильный `0.3.0`, а publish job не заменяет публичный Release. Защиты workflow сохраняются.

После push тега дождись **нового** запуска Release APK и нового Release с APK/checksum. Re-run старого запуска собирает старый коммит. Для установки поверх существующего release нужен прежний signing key. Поскольку версия/code не увеличены, установленная `0.3.0` может не получить предложение автоматического обновления Obtainium; новый APK можно скачать и установить вручную поверх с той же подписью. Деинсталляция для обычного обновления с совпадающей подписью не нужна.

Проверка локального тега и удалённых refs:

```powershell
git rev-parse HEAD
git rev-parse 'v0.3.0^{commit}'
git ls-remote origin refs/heads/master refs/tags/v0.3.0 'refs/tags/v0.3.0^{}'
```

Для аннотированного тега коммит показывает строка `^{}`; SHA самого tag object отличается от SHA коммита. После публикации Obtainium берёт assets именно нового GitHub Release. Дальнейшие обычные выпуски снова увеличивают версию.

### Обычный выпуск новой версии

1. Измени `versionName` в `version.properties`. Только три числовых компонента без суффиксов и ведущих нулей. Текущий кандидат: `0.3.0`.
2. Выполни проверки и сохрани отдельный коммит по CONTRIBUTING:

   ```powershell
   python scripts/release.py check
   python -m unittest discover -s scripts -p 'test_release.py'
   & ./scripts/gradle.ps1 checkKotlin assembleDebug testDebugUnitTest lintDebug
   & ./scripts/check-workspace.ps1
   ```

3. После явного запроса на push/публикацию отправь master и тег, совпадающий с версией:

   ```powershell
   git push origin master
   git tag v0.3.0
   git push origin v0.3.0
   ```

4. Workflow `Release APK` проверяет тег и предыдущие стабильные выпуски, собирает debug/release, запускает тесты и lint, проверяет сертификат и содержимое APK. После проверок создаёт черновик GitHub Release, загружает APK и SHA-256 и публикует только после успешной загрузки. Повторный запуск продолжает незавершённый черновик; опубликованный выпуск не перезаписывается. Ключ из runner temp удаляется и при ошибке. Обычный workflow Android продолжает выдавать debug artifact.
5. В Obtainium проверь обновления и установи выпуск. Для следующего выпуска повтори с увеличенной версией, например `0.3.1`. Не перезаписывай APK опубликованной версии и не меняй подписывающий ключ.

Android versionCode: `major × 1000000 + minor × 1000 + patch`. Допустимы major 0–2000, minor/patch 0–999; нулевой итог запрещён. `0.2.1` имеет код 2001, выше прежнего debug-кода 1. Workflow отклоняет повторную или более старую версию относительно всех опубликованных стабильных Releases.

Локальная проверка подписанного APK без публикации:

```powershell
python scripts/release.py prepare --tag v0.3.0 --apk app/build/outputs/apk/release/app-release.apk --sdk .toolchain/android-sdk
```

Нужен `ANDROID_SIGNING_CERT_SHA256`; результат — `app/build/distribution/ritela-0.3.0.apk` и `.sha256`. Скрипт отказывает при неверной версии/package, debug-флаге, сетевых разрешениях, неправильном сертификате или перезаписи результата. `python scripts/release.py link` восстанавливает точную ссылку README.

## Доступ и переход с первого APK

Для приватного репозитория в Obtainium нужен fine-grained token с доступом к этому репозиторию и `Contents: Read-only`. Сохрани его в GitHub source settings Obtainium, затем добавь приложение. Не включай токен в README, JSON или ссылку. Для публичного репозитория токен обычно не нужен. Видимость репозитория workflow не меняет.

Debug APK из предыдущих Actions подписывались временным debug-ключом runner. Release с постоянным ключом Android не установит поверх APK с другой подписью. Перед первой заменой экспортируй данные в Настройках, сохрани файл и пароль; затем замени установку и импортируй копию. Все последующие release APK с тем же ключом устанавливаются как обновления с сохранением данных. Не удаляй приложение, пока экспорт не сохранён.

Готовность: наличие secrets, первый GitHub Release и установка через Obtainium подтверждаются отдельно. До публикации первого Release ссылка добавления не сможет получить APK.

Источники: [deep links Obtainium](https://wiki.obtainium.imranr.dev/deep_links/), [определение версии](https://wiki.obtainium.imranr.dev/app_tracking/), [GitHub source](https://wiki.obtainium.imranr.dev/sources/), [подпись Android](https://developer.android.com/studio/publish/app-signing).

Если выпуск упал до публикации и его тег уже отправлен, простое Re-run jobs повторяет workflow из старого коммита тега. Исправления в master не попадут в такой запуск. Выпусти следующую версию с новым тегом, не перезаписывая старый: после неудачного v0.2.0 текущий исправленный кандидат — v0.2.1.

Список предыдущих выпусков получается через gh api --paginate --slurp без --jq: эти два флага вместе не поддерживаются GitHub CLI. Python проверяет и объединяет все страницы через --release-pages, включая пустой репозиторий. [Документация gh api](https://cli.github.com/manual/gh_api).

Если Obtainium сообщает Could not find a suitable release: проверь источник https://github.com/jeezab/ritela (GitHub), GitHub token с Contents: Read-only для приватного repository и пустые фильтры названия/описания/имени APK. Токен добавляется в Obtainium Settings → Source-specific settings → GitHub → Personal access token. Браузерная авторизация GitHub и Actions signing secrets не авторизуют Obtainium. GitHub proxy prefix оставь пустым при прямом доступе: Obtainium не отправляет token через этот proxy. При свежем выпуске минимальный возраст обновления должен позволять его выбрать.

2026-10-06 проверено: v0.2.1 опубликован (draft=false, prerelease=false), ritela-0.2.1.apk uploaded, 8 240 472 байт. Repository приватный; без авторизации API возвращает 404, с авторизацией release и assets доступны. Публичность репозитория не менялась; если понадобится распространение без token, это отдельное решение о публичном источнике APK.

2026-10-07: подготовлена версия 0.2.2/code 2002 для новых скриншотов, optional-password backup, редактируемого дневника и календарного прогноза. Пользователь явно запросил push master и нового тега; существующие v0.2.0/v0.2.1 не меняются. Публикацию production APK подтверждает Release APK workflow после отправки v0.2.2.

2026-10-07: кандидат 0.2.4/code 2004 включает восстановленный swipe карточки календарного дня и компактный небесный hero с reduced-motion. Опубликованные стабильные версии до v0.2.3 подтверждены GitHub API; v0.2.4 новый. Пользователь явно разрешил push master и тег v0.2.4. APK выпускается существующим Release APK workflow.

2026-10-07: v0.2.4 workflow упал на устаревшем английском UI-assertion; исправлен переход к forecast-info перед проверкой числа циклов. Следующий кандидат v0.2.5/code 2005. Пользователь выполняет push/tag самостоятельно; существующий v0.2.4 не перезаписывается. Локально выполнены только компиляция тестов/checkKotlin и проверка версии, без выполнения тестов и просмотра UI.
