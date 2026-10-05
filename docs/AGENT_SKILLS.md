# Локальные скиллы

Скиллы лежат в `.agents/skills/`. Читай только применимый SKILL.md. Это проектные инструкции; `.agent-skills.lock` — внутренний реестр, не стандарт Codex.

| Name | Purpose / зачем | Source / path | Version / commit | License | Дата | Класс |
|---|---|---|---|---|---|---|
| ritela-resume | Продолжить с checkpoint, не исследуя проект заново | Этот репозиторий / `.agents/skills/ritela-resume/SKILL.md` | 1.0.0 / null (локальная разработка) | Не объявлена | 2026-10-04 | project-local |
| ritela-checkpoint | Обновить документы, проверить шаг и сделать коммит | Этот репозиторий / `.agents/skills/ritela-checkpoint/SKILL.md` | 1.0.0 / null (локальная разработка) | Не объявлена | 2026-10-04 | project-local |
| ritela-android-check | Повторяемый цикл format/build/test/lint/render/APK checks | Этот репозиторий / `.agents/skills/ritela-android-check/SKILL.md` | 1.0.0 / null (локальная разработка) | Не объявлена | 2026-10-05 | project-local |
| cycle-prediction-validation | Проверить расчёт и неопределённость, календарь и реакцию на правки | Этот репозиторий / `.agents/skills/cycle-prediction-validation/SKILL.md` | 1.0.0 / null (локальная разработка) | Не объявлена | 2026-10-05 | project-local |

SHA-256 содержимого находятся в lock. При изменении скилла обнови его версию/hash и проверь реестр через `scripts/check-workspace.ps1`.

## Проверка внешних источников

Просмотрены каталоги [Google Android Skills](https://github.com/android/skills), [Kotlin Agent Skills](https://github.com/Kotlin/kotlin-agent-skills) и [OpenAI Skills](https://github.com/openai/skills). Пока ничего из них не установлено: сначала выбрать применимые к фактическому toolchain инструкции, прочитать SKILL.md и все вызываемые scripts/resources, проверить лицензию и закрепить upstream SHA. Кандидат для M0 — Android testing setup; навыки Compose подбираются по конкретным задачам.

Внешние скрипты при просмотре каталогов не выполнялись. Не устанавливать весь каталог. Prediction-validation добавлен после реализации M2; privacy-audit, release-check, ui-quality-audit появятся после проверенных процедур в соответствующих этапах.

### Результат отбора 2026-10-04

- Android upstream просмотрен на SHA `42dc2270e96032bd860bb94511e440aa00a43125`. `testing/testing-setup/SKILL.md` прочитан: не установлен, поскольку предписывает Hilt при отсутствии DI и широкий набор тестовых библиотек. Это конфликтует с простотой архитектуры и отсутствием лишних зависимостей из PROJECT_BRIEF. Содержимое не выполнялось и не перенесено в проект.
- `security/android-permissions-security/SKILL.md` просмотрен как кандидат для будущего manifest/privacy review; не установлен на этапе документации/toolchain. Аудит всех ресурсов и лицензии для установки ещё не завершён.
- Kotlin upstream каталог просмотрен на SHA `c2f90697bf71966a117a13340d5fff787f004140`. Большинство доступных навыков относятся к миграциям, backend или Native; существующий Android-проект для миграции отсутствует. Ничего не установлено.
- OpenAI каталог просмотрен; `skill-creator` применён из существующего окружения для создания/валидации двух собственных скиллов. Его код не скопирован и он не считается установленным project-local скиллом.
