# Архитектура Ritela

Сейчас один Android-модуль `app`. `MainActivity` настраивает защищённое окно и edge-to-edge, `ui/RitelaApp.kt` содержит стартовый экран, `ui/Theme.kt` — тему и интервалы. Kotlin встроен в AGP 9.4.1; отдельный kotlin-android plugin не применяется.

Реализована базовая запись менструального периода. `domain/Period.kt` содержит чистые Kotlin-модель и проверку дат. `data/` содержит Room database/DAO, storage entity и repository. `RitelaApplication` создаёт единственный экземпляр базы лениво, `PeriodViewModel` передаёт состояние через StateFlow. UI наблюдает его с учётом lifecycle.

Новая запись содержит начало и необязательное окончание. Открытый период можно завершить позже. Repository запрещает будущие/обратные даты; DAO проверяет пересечения и вставляет в одной транзакции. Даты хранятся как epoch day, технические timestamps — миллисекунды UTC. Главный экран читает последние 30 периодов, не всю историю.

Прогноз, коррекция/удаление записей и события ещё не реализованы. Prediction отделяется в чистый Kotlin, когда появляется движок расчётов. Схема Room v1 экспортируется в `app/schemas/`; destructive fallback не используется. Схема и проверки: [DATA_FORMAT.md](docs/DATA_FORMAT.md).

Сборка использует version catalog, Gradle wrapper с SHA-256, configuration cache и build cache. Производственные зависимости сейчас ограничены Compose/Material 3 и AndroidX Activity; Robolectric и ktlint используются только для проверок.
