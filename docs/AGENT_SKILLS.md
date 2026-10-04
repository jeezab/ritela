# Локальные скиллы

Скиллы лежат в `.agents/skills/`. Читай только применимый SKILL.md. Это проектные инструкции; `.agent-skills.lock` — внутренний реестр, не стандарт Codex.

| Name | Purpose / зачем | Source / path | Version / commit | License | Дата | Класс |
|---|---|---|---|---|---|---|
| ritela-resume | Продолжить с checkpoint, не исследуя проект заново | Этот репозиторий / `.agents/skills/ritela-resume/SKILL.md` | 1.0.0 / null (локальная разработка) | Не объявлена | 2026-10-04 | project-local |
| ritela-checkpoint | Обновить документы, проверить шаг и сделать коммит | Этот репозиторий / `.agents/skills/ritela-checkpoint/SKILL.md` | 1.0.0 / null (локальная разработка) | Не объявлена | 2026-10-04 | project-local |

SHA-256 содержимого находятся в lock. При изменении скилла обнови его версию/hash и проверь реестр через `scripts/check-workspace.ps1`.

## Источники для следующего шага M0

Просмотрены каталоги [Google Android Skills](https://github.com/android/skills), [Kotlin Agent Skills](https://github.com/Kotlin/kotlin-agent-skills) и [OpenAI Skills](https://github.com/openai/skills). Пока ничего из них не установлено: сначала выбрать применимые к фактическому toolchain инструкции, прочитать SKILL.md и все вызываемые scripts/resources, проверить лицензию и закрепить upstream SHA. Кандидат для M0 — Android testing setup; навыки Compose подбираются по конкретным задачам.

Внешние скрипты при просмотре каталогов не выполнялись. Не устанавливать весь каталог. Скиллы prediction-validation, privacy-audit, release-check, ui-quality-audit появятся после проверенных процедур в соответствующих этапах.
