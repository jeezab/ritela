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
| `app/src/main/kotlin/app/ritela/ui/SettingsScreen.kt` | Язык, системная/светлая/тёмная тема и backup |
| `app/src/main/kotlin/app/ritela/data/SettingsRepository.kt` | Приватные preferences и Flow исходных значений/темы |
| `gradle/libs.versions.toml` | Закреплённые версии зависимостей и plugins |
| `app/src/main/kotlin/app/ritela/` | Activity и Compose UI |
| `app/src/main/kotlin/app/ritela/domain/` | Чистые модели, проверка дат, расчёт цикла и сетка календаря |
| `app/src/main/kotlin/app/ritela/ui/CalendarScreen.kt` | Свободная лента месяцев, детали дня, история и действия |
| `app/src/main/kotlin/app/ritela/ui/ForecastCard.kt` | Дата прогноза, диапазон и качество истории |
| `app/src/main/kotlin/app/ritela/data/` | Room database, DAO, storage entity, repository |
| `app/schemas/` | Экспортированные схемы Room v1/v2/v3 |
| `docs/DATA_FORMAT.md` | Фактический формат базы и инварианты дат |
| `app/src/test/kotlin/app/ritela/` | JVM/Compose тесты и render checks |
| `.github/workflows/android.yml` | Проверки, debug APK, checksum и отчёты |
| `.github/workflows/release.yml` | Подписанный APK по тегу, проверки и GitHub Release для Obtainium |
| `version.properties` | Единая версия приложения; versionCode вычисляется из SemVer |
| `scripts/release.py`, `scripts/test_release.py` | Проверки версий/подписи, упаковка APK, ссылка Obtainium и regression checks |
| `docs/RELEASES.md` | Настройка подписи и повторяемый выпуск через Obtainium |
| `docs/TESTING.md` | Доступные команды и ограничения проверок |
| `ARCHITECTURE.md`, `PRIVACY.md` | Фактическая архитектура и меры приватности |

## Этапы

| Этап | Результат | Статус |
|---|---|---|
| M0 | Toolchain, выбранные skills, Android skeleton, первая зелёная сборка, CI APK | Завершён: локальные проверки, успешный CI и скачанный APK подтверждены пользователем |
| M1 | Room и запись менструации | Add/finish/history/edit/delete реализованы и проверены; схема v1 сохранена |
| M2 | Prediction engine, календарь, главный экран | Первая версия реализована: день цикла, медиана/MAD, диапазоны до 12 циклов, календарь и выбранный день; проверка на устройстве впереди |
| M3 | События, симптомы, сексуальная активность | Первая версия: дневные отметки и офлайн-карточки помощи |
| M4 | Insights и настройки | Графики цикла/длительности/боли, язык/тема и backup реализованы; дальнейшие insights впереди |
| M5 | Экспорт/импорт, приватность, защита | Шифрованный backup и транзакционный merge реализованы; app lock впереди |
| M6 | Виджеты и уведомления | Не начат |
| M7 | Визуальная проверка и производительность | Визуальные проверки ведутся уже в M1; добавлена галерея, итоговый аудит впереди |
| M8 | Release pipeline и проверенный APK | Workflow и Obtainium подготовлены; production secrets, первый Release и установка впереди |

Архитектурную карту модулей, форматы данных и команды Android добавлять после появления соответствующего кода. Полные критерии этапов находятся в PROJECT_BRIEF, разделы 58, 60–63.

Новые файлы: domain/DayLog.kt — типы и валидация отметок; data/DayLogEntity/Dao/Repository — Room/Flow; data/BackupRepository — шифрование, preview и atomic merge; ui/DayLogEntry — общая форма; ui/Insights — реальные графики; ui/HelpCards — каталог/подбор/детали; ui/BackupActions — SAF/пароль/подтверждение. Документы [HEALTH_CONTENT.md](HEALTH_CONTENT.md), [BACKUP.md](BACKUP.md); скрипт scripts/check-health-content.ps1 и skill health-content-check. DayLogBackupTest проверяет миграцию и целостность копии.

Доработка UI 2026-10-06: [исходный промпт](prompts/2026-10-06-ui-polish.txt); domain/MeasuredSeries.kt — годовые измерения для статистики; domain/Period.kt — periodConflict для проверки диапазона до сохранения. preview-ui.ps1 -CompareBefore строит сравнение с локально сохранёнными исходными PNG.

Редактируемый дневник: `domain/JournalLayout.kt` — модель/инварианты и преобразование selections; `data/JournalRepository.kt` — Room DAO/JSON codec/поток настройки; `ui/DayLogEntry.kt` — редактор и drag; `journal_strings.xml` EN/RU. Room v3 сохраняет custom selections и иконку дня; backup payload v2 переносит layout. `JournalLayoutTest` и DayLogBackupTest проверяют изменения/миграции/перенос; HomeScreenTest — полноценное редактирование.

Иконка: [ritela.png](../ritela.png) — пользовательский исходник; drawable-nodpi/launcher_art.png — точная копия; mipmap-anydpi/ic_launcher.xml — adaptive icon для minSdk 26, drawable/ic_launcher_foreground.xml — отступ до безопасной центральной области 66/108, drawable/ic_launcher_background.xml — бумажный фон. [update-launcher-icon.ps1](../scripts/update-launcher-icon.ps1) проверяет квадратный PNG и обновляет копию без перекодирования: `& ./scripts/update-launcher-icon.ps1`.
