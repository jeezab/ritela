# Архитектура Ritela

Сейчас один Android-модуль `app`. `MainActivity` настраивает защищённое окно и edge-to-edge, `ui/RitelaApp.kt` содержит стартовый экран, `ui/Theme.kt` — тему и интервалы. Kotlin встроен в AGP 9.4.1; отдельный kotlin-android plugin не применяется.

Реализована базовая запись менструального периода. `domain/Period.kt` содержит чистые Kotlin-модель и проверку дат. `data/` содержит Room database/DAO, storage entity и repository. `RitelaApplication` создаёт единственный экземпляр базы лениво, `PeriodViewModel` передаёт состояние через StateFlow. UI наблюдает его с учётом lifecycle.

Новая запись содержит начало и необязательное окончание. Открытый период можно завершить позже. Repository запрещает будущие/обратные даты; DAO проверяет пересечения и вставляет в одной транзакции. Даты хранятся как epoch day, технические timestamps — миллисекунды UTC. Repository читает всю историю для календаря и расчёта; главный экран выводит последние 30 записей.

Правки дат и удаление реализованы через repository/ViewModel. DAO редактирует в транзакции, проверяя overlap без самой записи и сохраняя UUID/createdAt; UI требует подтверждение удаления. Схема Room v1 экспортируется в `app/schemas/`; destructive fallback не используется. Изменений таблиц для этих операций нет. Схема и проверки: [DATA_FORMAT.md](docs/DATA_FORMAT.md).

`domain/CyclePrediction.kt` вычисляет день цикла, медиану/MAD, качество истории и 12 расширяющихся диапазонов. ViewModel пересчитывает анализ при каждом Room Flow emission и обновляет текущий день при ON_RESUME. Производные прогнозы не сохраняются. [Формулы и ограничения](docs/PREDICTION.md). `CalendarScreen` показывает месяц, выбранный день и связанные действия; `ForecastCard` используется на главной и в календаре. Нижняя навигация переключает два работающих раздела. События и симптомы ещё не реализованы.

Тема и тексты соответствуют [STYLE_GUIDE.md](docs/STYLE_GUIDE.md): общие цветовые/типографические tokens, serif для заголовков и дат, адаптивный Canvas-декор без bitmap assets. Системные dynamic colors по умолчанию выключены. Английский fallback в values, полный русский в values-ru. AGP generateLocaleConfig + localeFilters en/ru предоставляют системный выбор языка на Android 13+; более старые ОС используют язык устройства.

Сборка использует version catalog, Gradle wrapper с SHA-256, configuration cache и build cache. Производственные зависимости — Compose/Material 3, AndroidX Activity/Lifecycle и Room; Robolectric и ktlint используются только для проверок.
