# Карта проекта

Начало сессии: `STATUS.md` → `scripts/resume.ps1` → текущие файлы задачи. Не загружай полный промпт каждый раз.

| Путь | Назначение |
|---|---|
| `AGENTS.md` | Роль, устойчивые ограничения и Definition of Done |
| `README.md` | Краткое описание и доступные команды |
| `CONTRIBUTING.md` | Правила проверок и коммитов |
| `docs/STATUS.md` | Последний checkpoint и следующее действие |
| `docs/DECISIONS.md` | Решения, причины и статус |
| `docs/PROJECT_BRIEF.md` | Полный исходный промпт: разделы 0–63 |
| `docs/prompts/2026-10-04-role.txt` | Точная копия приложения пользователя, обрывается в разделе 10 |
| `docs/AGENT_SKILLS.md` | Происхождение и назначение установленных скиллов |
| `.agent-skills.lock` | Версии и SHA-256 локальных скиллов; внутренний формат |
| `.agents/skills/ritela-resume/SKILL.md` | Восстановление контекста без повторного исследования |
| `.agents/skills/ritela-checkpoint/SKILL.md` | Обновление памяти и проверенный коммит |
| `.agents/skills/ritela-android-check/SKILL.md` | Format/build/test/lint/render/APK verification |
| `.agents/skills/cycle-prediction-validation/SKILL.md` | Проверка расчёта, неопределённости и пересчёта после правок |
| `docs/PREDICTION.md` | Формулы прогноза, достаточность данных, ограничения и проверки |
| `scripts/resume.ps1` | Краткий read-only отчёт о состоянии и инструментах |
| `scripts/check-workspace.ps1` | Ссылки, синтаксис скриптов и целостность lock |
| `scripts/read-brief.ps1` | Вывод только выбранных разделов полного промпта |
| `scripts/bootstrap-toolchain.ps1` | Подготовка локального toolchain |
| `scripts/toolchain.lock.json` | Закреплённые URL, версии и SHA-256 архивов инструментов |
| `scripts/setup-sdk.ps1` | Установка SDK-пакетов и проверка adb |
| `scripts/gradle.ps1` | Сборка с локальными Java/SDK и восстановлением environment |
| `scripts/verify-apk.ps1` | Проверка APK, сетевых разрешений и checksum |
| `scripts/preview-ui.ps1` | Обновление Compose-снимков и локальная HTML-галерея без телефона |
| `docs/UI_DESIGN.md` | Исследование трекеров, принципы текста и план интерфейса |
| `docs/STYLE_GUIDE.md` | Выбранный по макету стиль, tokens, адаптация механик и EN/RU |
| `.agents/skills/ui-quality-audit/SKILL.md` | Сохранение стиля, двуязычные формы, темы и размеры |
| `app/src/main/res/values-ru/strings.xml` | Полный русский интерфейс; английский fallback в values |
| `app/src/main/kotlin/app/ritela/ui/CycleOrbit.kt` | Адаптивный Canvas-декор карточки цикла |
| `app/src/main/kotlin/app/ritela/ui/SettingsScreen.kt` | Язык и сохранение системной/светлой/тёмной темы |
| `app/src/main/kotlin/app/ritela/data/SettingsRepository.kt` | Приватные preferences и Flow исходных значений/темы |
| `gradle/libs.versions.toml` | Закреплённые версии зависимостей и plugins |
| `app/src/main/kotlin/app/ritela/` | Activity и Compose UI |
| `app/src/main/kotlin/app/ritela/domain/` | Чистые модели, проверка дат, расчёт цикла и сетка календаря |
| `app/src/main/kotlin/app/ritela/ui/CalendarScreen.kt` | Месяц, выбор дня, просмотр и запись дат |
| `app/src/main/kotlin/app/ritela/ui/ForecastCard.kt` | Ориентир, диапазон и качество истории |
| `app/src/main/kotlin/app/ritela/data/` | Room database, DAO, storage entity, repository |
| `app/schemas/` | Экспортированная схема Room v1 |
| `docs/DATA_FORMAT.md` | Фактический формат базы и инварианты дат |
| `app/src/test/kotlin/app/ritela/` | JVM/Compose тесты и render checks |
| `.github/workflows/android.yml` | Проверки, debug APK, checksum и отчёты |
| `docs/TESTING.md` | Доступные команды и ограничения проверок |
| `ARCHITECTURE.md`, `PRIVACY.md` | Фактическая архитектура и меры приватности |

## Этапы

| Этап | Результат | Статус |
|---|---|---|
| M0 | Toolchain, выбранные skills, Android skeleton, первая зелёная сборка, CI APK | Завершён: локальные проверки, успешный CI и скачанный APK подтверждены пользователем |
| M1 | Room и запись менструации | Add/finish/history/edit/delete реализованы и проверены; схема v1 сохранена |
| M2 | Prediction engine, календарь, главный экран | Первая версия реализована: день цикла, медиана/MAD, диапазоны до 12 циклов, календарь и выбранный день; проверка на устройстве впереди |
| M3 | События, симптомы, сексуальная активность | Не начат |
| M4 | Insights и настройки | Настройки языка/темы реализованы; insights и остальные настройки впереди |
| M5 | Экспорт/импорт, приватность, защита | Не начат |
| M6 | Виджеты и уведомления | Не начат |
| M7 | Визуальная проверка и производительность | Визуальные проверки ведутся уже в M1; добавлена галерея, итоговый аудит впереди |
| M8 | Release pipeline и проверенный APK | Не начат |

Архитектурную карту модулей, форматы данных и команды Android добавлять после появления соответствующего кода. Полные критерии этапов находятся в PROJECT_BRIEF, разделы 58, 60–63.
