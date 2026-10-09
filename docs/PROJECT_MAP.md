# Карта проекта

2026-10-09, локальные пользователи: data/UserProfiles.kt — каталог UUID/имён, ProfileManager и закрываемая ProfileSession; RitelaApplication предоставляет репозитории выбранной сессии. data/ProfileSettings.kt и Room v4/MIGRATION_3_4 — независимые настройки в каждой базе, исходный ritela.db остаётся у «Я». ui/ProfileApp.kt — сброс UI по токену сессии и ресурсы языка; ui/UserSettings.kt — выбор/добавление/переименование и язык system/RU/EN, редактор имени через JournalDialog. BackupRepository payload v3 — выбор категорий и атомарный импорт; ui/BackupChoices.kt и BackupActions — категории и привязка системного picker к сессии. UserProfilesTest/UserProfilesUiTest/DayLogBackupTest проверяют изоляцию, миграцию, выбранный импорт и взаимодействия; HomeScreenTest ждёт начальную загрузку профиля. Детали формата: DATA_FORMAT.md/BACKUP.md. Это заменяет прежнюю единственную глобальную базу и настройки SharedPreferences.

2026-10-09, черновик месячных: domain/Period.kt содержит PeriodRange для ещё не записанных дат. PeriodEntry хранит пары epoch-day в rememberSaveable, рисует их через существующий MonthGrid и блокирует пересечения. RitelaApp не пересоздаёт форму после отметки; передаёт весь пакет в PeriodViewModel.savePeriods → PeriodRepository.addAll → атомарный PeriodDao.addAllIfSeparate. Room-схема прежняя. PeriodEntryUiTest проверяет черновик/позицию/Cancel/Back/retry; PeriodRepositoryTest и PeriodViewModelTest — атомарность и пересчёт прогноза. Это заменяет описанную ниже механику немедленного сохранения каждого интервала.

2026-10-09, выбор месячных: `domain/PeriodRangeSelection.kt` — закрытый первый клик, упорядоченная пара в любом направлении, проверка пересечений и возможности открытого периода. PeriodEntry использует HomeTheme/MonthGrid и LazyColumn месяцев 1900..сегодня, фиксированные одинаковые по высоте действия и safeDrawing/IME. RitelaApp различает обычное сохранение и сохранение с продолжением, сбрасывает выбор только после state.saved. PeriodRangeSelectionTest/PeriodEntryUiTest проверяют границы, редактирование, 320dp/200%, последовательные Room-записи.

2026-10-09: `ui/DayFieldEntry.kt` — быстрый выбор одного поля, RitelaApp разделяет поле и полный редактор. `domain/JournalPresentation.kt` — независимые record/intimacy маркеры, совместимый selectable-модель и общий toggle. `ic_mood.xml` / JournalIcon.MOOD — настроение без сердца. CalendarScreen рисует до двух символов, сохраняет цвет и держит Сегодня/История под лентой. JournalPresentationTest/DaySelectionUiTest покрывают сохранение других значений, NONE и значки.

Settings 0.3.4: `ui/LoveSurprise.kt` — Canvas overlay и прокручиваемое письмо; `ui/LoveAnimation.kt` — временный счётчик и генерация частиц без Android. `love_strings.xml` EN/RU содержит кнопку; письмо только в default resources с translatable=false. LoveAnimationTest/LoveSurpriseTest проверяют последовательность, частицы, английскую локаль, закрытие, reduced motion и 320dp/200%.

Уточнение Calendar 0.3.3: compactFertilityWindows больше не фильтрует ориентир по объёму/устойчивости истории — возвращает оценки fertilityWindows, сетка использует исключительно их центральное семидневное likely. PredictionMethodInfo принимает includeOrbit; Calendar передаёт false, Home сохраняет default true.

Calendar day UX 2026-10-08: domain/CalendarPresentation.kt — компактная семидневная подсветка/ограничения определённости и CalendarSelection с единым приоритетом. PredictionEngine не изменён. ui/CalendarDayFormatter.kt — локализованные даты/статусы/краткие описания вне composables, переиспользуется общим formattedDate/periodDates и сеткой. CalendarDayDetails — bottom sheet по содержимому с max-height, safeDrawing/IME insets, раскрываемыми диапазонами и скроллом; CalendarDayRecords в Insights сохраняет видимость архивных разделов. На Home длительность месячных только в малой плитке. Новые CalendarPresentationTest/CalendarDayFormatterTest и целевые HomeScreenTest; selected_day_strings.xml EN/RU.

UI 0.3.2: ForecastCard — дата/countdown в Row, увеличенный день цикла; HomeInsightTiles — две карточки общей высоты через IntrinsicSize; CycleOrbitDetail — центрированные кнопки дня. CalendarScreen окрашивает только месячные, сохраняя расчёт фертильности/цветок/подробности. CalendarDesign содержит соответствующую краткую легенду. Insights — последние интервалы/график и ожидаемая продолжительность месячных, без median/range summary. Данные/алгоритмы без изменений.

Прогноз 2026-10-08: domain/PredictionStatistics.kt — взвешенная медиана, rolling-origin backtesting/MAE/медианные ошибки и Monte Carlo на календарный год; domain/FertilityPrediction.kt — независимые модели овуляции/окна/слизи. CyclePrediction.kt объединяет результат и классифицирует календарь. Новые проверки PersonalizedPredictionTest, дополнительные Room/Compose сценарии в PeriodViewModelTest/HomeScreenTest. EN/RU справка/диапазоны — prediction_strings.xml. Room v3/backup без изменений; SettingsRepository игнорирует прежние ручные presets.

Упрощение UI 2026-10-08: CalendarAtmosphericHeader и CalendarOrbitDecoration удалены; CalendarDesign содержит акценты/анимируемые сводки, CalendarScreen — карточки истории с jump к месяцу начала. RitelaApp использует SaveableStateHolder для Calendar (позиция и сворачивание сохраняются между вкладками). CycleOrbitScene показывает число дня по центру, ForecastCard/CycleOrbitDetail используют одинаковые пропорции эллипса; phaseName — названия без повторяемого suffix, справка сохраняет смысл оценки. HomeDesign содержит две плитки и shortcuts mood/discharge/sex/note. Insights показывает ожидаемую длительность из preset. DayLogEntry/редактор повторно используют HomeTheme; JournalLayout разделяет новые defaults и исторический built-in registry, discharge хранится существующим custom JSON. `refinement_strings.xml` EN/RU и три новые vector icons поддерживают подписи/сворачивание/стрелки. SettingsScreen больше не содержит theme selector. Старые описания декора/трёх плиток/выбора темы ниже относятся к предыдущим шагам.

Home orbit 2026-10-08: `ui/OrbitGeometry.kt` — один замкнутый Path, позиции по длине и обратная проекция жеста; `OrbitMarker` — пять символических вех. `ui/OrbitInteractionState.kt` — текущий день/фокус/дата/существующая оценка фазы, без записи данных. `ui/CycleOrbitScene.kt` — общий Canvas компактного и подробного режима; `ui/CycleOrbitDetail.kt` — read-only подробности, drag, slider, доступные кнопки и справка. `orbit_strings.xml` EN/RU; OrbitGeometryTest/OrbitInteractionStateTest и целевые HomeScreenTest проверяют геометрию, даты, взаимодействия и 200%. `ui/CalendarOrbitDecoration.kt` сохраняет прежний независимый рисунок Calendar; новый Home его не изменяет. Старые записи о drawOval/OrbitPhaseMarkers ниже относятся к прежней реализации.

Начало сессии: `STATUS.md` → `scripts/resume.ps1` → текущие файлы задачи. Не загружай полный промпт каждый раз.

| Путь | Назначение |
|---|---|
| `AGENTS.md` | Роль, устойчивые ограничения и Definition of Done |
| `README.md` | Краткое описание и доступные команды |
| `CONTRIBUTING.md` | Правила проверок и коммитов |
| `docs/STATUS.md` | Последний checkpoint и следующее действие |
| `docs/prompts/2026-10-08-home-rebuild.txt` | Оригинальный промпт; текущий scope пользователя — только Home |
| `app/src/main/kotlin/app/ritela/ui/HomeDesign.kt` | Home как источник общей палитры/типографики трёх экранов; insight-плитки и круглые действия Home |
| `app/src/main/kotlin/app/ritela/ui/CalendarDesign.kt` | Calendar на tokens Home: атмосферная шапка, акценты состояний, прогноз/фаза/легенда |
| `app/src/main/kotlin/app/ritela/ui/SettingsDesign.kt` | Группы и доступные строки Settings на существующих tokens Home |
| `app/src/main/res/values/calendar_weekdays.xml`, `values-ru/calendar_weekdays.xml` | Двухбуквенные EN/RU подписи дней недели основной сетки Calendar |
| `app/src/main/res/values/home_strings.xml`, `values-ru/home_strings.xml` | EN/RU подписи нового Home |
| `app/src/test/kotlin/app/ritela/HomeScreenTest.kt` | Взаимодействия Home и целевые рендеры композиции/320dp/200% |
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
| `app/src/main/kotlin/app/ritela/ui/CalendarScreen.kt` | Один месяц / свободная лента месяцев, детали дня, история и действия |
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

Иконка: [ritela.png](../ritela.png) — пользовательский исходник; drawable-nodpi/launcher_art.png — точная копия; mipmap-anydpi/ic_launcher.xml — adaptive icon для minSdk 26, drawable/ic_launcher_foreground.xml — отступ 16.6667% до центральной области 72/108, drawable/ic_launcher_background.xml — сливовый фон #4B2938. [update-launcher-icon.ps1](../scripts/update-launcher-icon.ps1) проверяет квадратный PNG и обновляет копию без перекодирования: `& ./scripts/update-launcher-icon.ps1`.

[Промпт небесного hero](prompts/2026-10-07-orbit-hero.txt) сохранён без изменений. domain/CycleScene.kt — предполагаемая фаза/прогресс представления без изменения расчёта; ui/CycleOrbitScene.kt — лёгкая рисованная орбита; ui/OrbitMotion.kt — lifecycle, системные анимации и low-RAM; CycleSceneTest — границы шаблона и недоступный прогноз.

ui/ForecastCard.kt — новый hero, компактные локализованные даты, info-dialog, ForecastDurationInsight и статичные light/dark/large-font Preview; прежний ui/CycleOrbit.kt удалён. HomeScreen подключает отдельную длительность и CTA с каплей. HomeScreenTest проверяет новый текст и info на 320dp/200%; существующие CI screenshot-тесты Home используют новый hero без новых имён PNG.

Орбита hero: ui/OrbitGeometry.kt — аналитическая эллиптическая геометрия, rotation matrix и упорядоченные OrbitPhaseMarkers; ui/CycleOrbitScene.kt — drawOval, пять вех и кольцо дня. OrbitGeometryTest проверяет принадлежность точек эллипсу, замыкание, положение 8/29 и clamping. HomeScreenTest.renderPolishedOrbitHeroInLightDarkAndLargeText создаёт три целевых PNG orbit-hero-light/dark/large-text, зарегистрированных в preview-ui.ps1. Производственный прогноз и domain/CycleScene.kt не изменены.

UI 2026-10-08: [пользовательский промпт](prompts/2026-10-08-ui-polish.txt) сохранён без изменений. ui/ScreenPreviews.kt — Home/Calendar (normal, narrow EN, dark, 200% RU) на синтетических данных. ui/Insights.kt — JournalQuickActions с реальными разделами дневника; ui/CalendarScreen.kt — один месяц и переключаемая вертикальная лента. res/values/cycle_articles.xml и values-ru/cycle_articles.xml — пять новых образовательных статей; HelpArticlesTest проверяет локализации и приоритет срочных советов. Theme.kt содержит общие RitelaColors/Typography/Shapes/Motion и Spacing. Пользовательские docs/ui остаются отдельными исходными материалами и не включаются автоматически в коммит.
