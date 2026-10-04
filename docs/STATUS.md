# Точка продолжения

Обновлено: 2026-10-04 (Europe/Moscow). Активный этап: M0. Приложение ещё не создано; MVP и первая зелёная Android-сборка не достигнуты.

## Восстановленное исходное состояние

- Единственный коммит при начале: `504d7ae init`; рабочее дерево чистое.
- Существовали полный `docs/PROJECT_BRIEF.md`, `.gitignore` и `scripts/bootstrap-toolchain.ps1`. Исходников Android, Gradle wrapper, CI и тестов нет.
- Windows, PowerShell; доступны Git, curl и Python 3.11.9. JDK/Gradle/SDK/adb/emulator в PATH и стандартных каталогах не найдены. JAVA_HOME/ANDROID_HOME/ANDROID_SDK_ROOT не заданы.
- Origin: `https://github.com/jeezab/ritela.git`; push не выполнялся.

## Сделано

- Сохранены роль и правила в AGENTS, оригинал приложения отдельно от полного промпта.
- Созданы карта, решения, README, правила коммитов и два локальных скилла.
- Автоматизированы read-only восстановление контекста и проверка документации/скиллов/PowerShell.
- Коммит памяти проекта: `bb0d9a0 docs(agent): preserve role, checkpoints and repeatable workflows`.
- Bootstrap дополнен JDK, закреплёнными URL/SHA-256, докачкой частичных файлов и повторным использованием установок. Подготовлены локальные Temurin 17.0.20.1+1, Gradle 9.6.0, Android command-line tools 15859902 (sdkmanager 22.0).
- Добавлена выборка разделов полного промпта через `scripts/read-brief.ps1`; resume показывает фактические локальные пути.
- Просмотрены каталоги Android/Kotlin/OpenAI, результаты отбора записаны в AGENT_SKILLS. Внешние скиллы не установлены; testing-setup отклонён из-за обязательных лишних зависимостей.

## Следующее действие

Продолжить M0 с Android SDK: запустить `scripts/bootstrap-toolchain.ps1 -VerifyOnly`, установить SDK platform/build-tools/platform-tools в `.toolchain/android-sdk` и проверить adb. Выбрать применимые skills после проверки ресурсов/лицензий. Затем создать Android skeleton с version catalog и wrapper, добиться зелёных compile/test/lint и добавить CI APK.

Официальная совместимость AGP 9.4: Gradle 9.6.0, JDK 17, build-tools 36.0.0; новое приложение для Play должно target API 36 или выше. Источники сохранены в DECISIONS. Сам AGP, Kotlin приложения и compileSdk пока не закреплены. Версия Kotlin 2.3.21 в выводе `gradle --version` относится к Gradle, а не к приложению.

Для вызова sdkmanager из PowerShell временно задай `$env:JAVA_HOME = (Resolve-Path '.toolchain/jdk-17.0.20.1+1').Path` и укажи `--sdk_root` локального SDK. Bootstrap восстанавливает JAVA_HOME после version checks и не меняет постоянный PATH.

## Проверки этого checkpoint

- `scripts/check-workspace.ps1`: PASS — синтаксис PowerShell, ссылки 10 документов, 2 скилла и SHA-256.
- `skill-creator/scripts/quick_validate.py` для каждого из двух скиллов: Skill is valid.
- `git diff --check`: PASS.
- Оригинал приложения скопирован побайтно; `.gitattributes` сохраняет его без преобразования переводов строк. SKILL.md хранится с LF для устойчивости SHA-256 между checkout.
- `scripts/resume.ps1`: PASS, отображает checkpoint, Git и установленные локальные инструменты.
- `scripts/bootstrap-toolchain.ps1`: PASS, все три архива проверены по SHA-256; Java/Gradle/sdkmanager version checks успешны.
- `scripts/bootstrap-toolchain.ps1 -VerifyOnly`: PASS, повторный запуск без скачивания. Отдельная проверка до установки корректно отклонила отсутствующий toolchain.
- Выборка brief: оглавление содержит 64 раздела; выбор 58,60,63 не включает соседние разделы — PASS.
- Проверка в отдельной временной копии отклонила изменённый SKILL.md; hash скиллов в Git также совпадает с lock — PASS.

## Ограничения

APK отсутствует. SDK-пакеты/лицензии, adb, emulator, Android-проект и CI ещё не подготовлены. Сборка, Android lint, unit/UI/device tests не запускались. Не отмечать M0 завершённым по результатам проверки документов/toolchain.

sdkmanager сообщил о deprecation в пользу `android sdk`. Проверка соседнего `android.exe --version` загрузила отдельный Android CLI 1.0.16500706, `android sdk --help` прошёл. Этот runtime не закреплён в toolchain.lock (там закреплён SDK tools архив), поэтому для воспроизводимой проверки пока используется sdkmanager. При дальнейших вызовах Android CLI добавлять `--no-metrics`, как указано самим CLI; не запускать `android init`, автоматически устанавливающий skills.
