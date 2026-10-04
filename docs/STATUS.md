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

## Следующее действие

Продолжить M0 с toolchain: дополнить существующий bootstrap отсутствующим JDK, закрепить версии и checksum, выполнить и проверить Java/Gradle/sdkmanager. Затем выбрать и проверить минимальные Android/Kotlin skills, закрепить их в lock. Проверить официальную совместимость AGP/Gradle/Kotlin/SDK и создать Android skeleton; добиться зелёных compile/test/lint, затем добавить CI APK.

Исходный bootstrap загружает Gradle 9.6.0 и Android CLI 15859902, но не JDK, SDK-пакеты и лицензии. Эти версии пока не являются выбором совместимого toolchain приложения.

## Проверки этого checkpoint

- `scripts/check-workspace.ps1`: PASS — синтаксис PowerShell, ссылки 10 документов, 2 скилла и SHA-256.
- `skill-creator/scripts/quick_validate.py` для каждого из двух скиллов: Skill is valid.
- `git diff --check`: PASS.
- Оригинал приложения скопирован побайтно; `.gitattributes` сохраняет его без преобразования переводов строк. SKILL.md хранится с LF для устойчивости SHA-256 между checkout.

## Ограничения

APK отсутствует. Сборка, Android lint, unit/UI/device tests ещё невозможны без toolchain и проекта. Не отмечать M0 завершённым по результатам проверки документов.
