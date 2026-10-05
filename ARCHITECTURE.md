# Архитектура Ritela

Сейчас один Android-модуль `app`. `MainActivity` настраивает защищённое окно и edge-to-edge, `ui/RitelaApp.kt` содержит стартовый экран, `ui/Theme.kt` — тему и интервалы. Kotlin встроен в AGP 9.4.1; отдельный kotlin-android plugin не применяется.

На этапе M0 приложение отображает пустую историю и описание локальной приватности. Хранение и прогноз ещё не реализованы. Новые слои появятся вместе с записью данных: чистые модели и проверка дат в domain, Room в data, состояние экрана в ViewModel. Prediction отделяется в чистый Kotlin, когда появляется движок расчётов.

Сборка использует version catalog, Gradle wrapper с SHA-256, configuration cache и build cache. Производственные зависимости сейчас ограничены Compose/Material 3 и AndroidX Activity; Robolectric и ktlint используются только для проверок.
