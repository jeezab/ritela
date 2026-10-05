# Ritela

Android-трекер менструального цикла с работой офлайн и без обязательного аккаунта. Период можно записать выбором первого и последнего дня на календаре, завершить позже, изменить даты и удалить с подтверждением. Календарь показывает все семь столбцов, листается вертикально и позволяет выбрать месяц/год по заголовку. Стартовый прогноз доступен после первого начала: цикл 28 дней, длительность 5 дней или по завершённой записи. Исходные значения меняются в Настройках; [правила расчёта](docs/PREDICTION.md) открыты. Данные хранятся локально в Room; будущие, обратные и пересекающиеся даты отклоняются.

Для продолжения работы открой [состояние проекта](docs/STATUS.md) и [карту](docs/PROJECT_MAP.md). Полные требования сохранены в [PROJECT_BRIEF](docs/PROJECT_BRIEF.md), роль и правила работы — в [AGENTS.md](AGENTS.md).

Интерфейс на русском и английском. Выбранный по макету стиль, цветовые tokens и поведение локализации: [STYLE_GUIDE.md](docs/STYLE_GUIDE.md).

Из корня репозитория в Windows PowerShell:

```powershell
powershell -NoProfile -File scripts/resume.ps1
powershell -NoProfile -File scripts/check-workspace.ps1
& ./scripts/read-brief.ps1 -Section 58,60,63
powershell -NoProfile -File scripts/bootstrap-toolchain.ps1
powershell -NoProfile -File scripts/bootstrap-toolchain.ps1 -VerifyOnly
powershell -NoProfile -File scripts/setup-sdk.ps1 -AcceptLicenses
& ./scripts/gradle.ps1 checkKotlin assembleDebug testDebugUnitTest lintDebug
```

Первый скрипт выводит точку продолжения, Git и доступность инструментов. Второй проверяет ссылки документации, реестр скиллов и синтаксис PowerShell. Третий загружает инструменты по закреплённым URL и SHA-256 в игнорируемую `.toolchain/`, повторно использует готовые установки и проверяет Java/Gradle/sdkmanager. `-VerifyOnly` проверяет уже подготовленные инструменты без загрузок. SDK-пакеты устанавливаются отдельным шагом.

APK создаётся в `app/build/outputs/apk/debug/app-debug.apk`. Команды форматирования, Linux/macOS, установки и отчёты: [TESTING.md](docs/TESTING.md). Устройство можно проверить через `& ./.toolchain/android-sdk/platform-tools/adb.exe devices`.

Посмотреть интерфейс на компьютере без установки APK:

```powershell
& ./scripts/preview-ui.ps1 -Open
```

Скрипт обновляет Compose-снимки и открывает их галерею в браузере. После уже выполненных тестов достаточно `& ./scripts/preview-ui.ps1 -SkipRender -Open`. Для интерактивного просмотра используй эмулятор Android Studio. План интерфейса и исследование трекеров: [UI_DESIGN.md](docs/UI_DESIGN.md).

Архитектура: [ARCHITECTURE.md](ARCHITECTURE.md). Текущие меры защиты: [PRIVACY.md](PRIVACY.md). Правила коммитов: [CONTRIBUTING.md](CONTRIBUTING.md). Локальные скиллы: [AGENT_SKILLS.md](docs/AGENT_SKILLS.md).
