# Ritela

Android-трекер менструального цикла с локальным хранением данных, работой офлайн и без обязательного аккаунта. Сейчас репозиторий находится на подготовительном этапе M0: Android-приложение и APK ещё не созданы.

Для продолжения работы открой [состояние проекта](docs/STATUS.md) и [карту](docs/PROJECT_MAP.md). Полные требования сохранены в [PROJECT_BRIEF](docs/PROJECT_BRIEF.md), роль и правила работы — в [AGENTS.md](AGENTS.md).

Из корня репозитория в Windows PowerShell:

```powershell
powershell -NoProfile -File scripts/resume.ps1
powershell -NoProfile -File scripts/check-workspace.ps1
powershell -NoProfile -File scripts/bootstrap-toolchain.ps1
```

Первый скрипт выводит точку продолжения, Git и доступность инструментов. Второй проверяет ссылки документации, реестр скиллов и синтаксис PowerShell. Третий загружает проверенные инструменты в игнорируемую `.toolchain/`; SDK-пакеты устанавливаются отдельным шагом.

Команды сборки, тестов, lint, установки и пути APK будут добавлены вместе с рабочим Android-проектом. Правила коммитов: [CONTRIBUTING.md](CONTRIBUTING.md). Локальные скиллы: [AGENT_SKILLS.md](docs/AGENT_SKILLS.md).
