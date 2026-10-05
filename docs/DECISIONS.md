# Решения

## 2026-10-04: память проекта

STATUS хранит текущий checkpoint; PROJECT_MAP — пути и этапы; AGENTS — устойчивые правила; PROJECT_BRIEF — полные требования. Так следующей сессии достаточно коротких входных документов. Оригиналы промптов сохраняются без редактирования отдельно от рабочих заметок.

Приложенный промпт короче существующего PROJECT_BRIEF и заканчивается на «Measure before». Полный PROJECT_BRIEF сохраняется; копия приложения дополняет происхождение требований, не заменяет полный документ.

## 2026-10-04: автоматизация и skills

Созданы только два собственных скилла для уже используемых процедур: продолжение сессии и checkpoint/коммит. Не создавать пустые skills для prediction/release/UI до появления рабочих процедур. 8–15 из промпта — верхний целевой диапазон, а не повод заполнять его ненужными скиллами.

Lock хранит версию и hash каждого локального SKILL.md. Собственные скиллы не имеют upstream commit; поле commit равно null. Лицензия проекта пока не объявлена — это явно указано, сторонние лицензии не присваиваются.

## 2026-10-04: коммиты

Пользователь явно требует регулярные коммиты. Отдельный проверенный логический шаг сохраняется через `type(scope): concrete change`; правила находятся в CONTRIBUTING. Переписывание истории и автоматический push не входят в процедуру.

## 2026-10-04: bootstrap toolchain

Существующий bootstrap дополнен JDK 17 и JSON с фиксированными URL/SHA-256. Gradle 9.6.0 оставлен: [AGP 9.4 compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes) указывает Gradle 9.6.0 и JDK 17. Сам AGP пока не установлен. [Google Play requirements](https://developer.android.com/google/play/requirements/target-sdk) требует target API 36 для новых обычных Android-приложений с 31 августа 2026; значение внести в Gradle при создании проекта.

JDK-архив и checksum получены из официального [Adoptium API](https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse); bootstrap использует конкретный release URL, а не latest API. Gradle checksum проверен по официальному distribution endpoint. Android CLI URL/hash сохранены из исходного bootstrap и требуют проверки фактической загрузкой.

Архивы проверяются до распаковки. Готовые установки повторно используются по marker соответствующей checksum; `-VerifyOnly` запускает version checks без скачивания. Этот marker подтверждает исходный архив, а не побайтную целостность всех распакованных файлов. SDK licenses и пакеты — отдельный шаг.

Фактическая загрузка всех трёх архивов и version checks прошли. При сетевой задержке curl докачивает сохранённый partial, делает не более трёх попыток и завершает шаг ошибкой при неуспехе. Унаследованный DEBUG включал подробное echo в upstream batch launcher; bootstrap временно снимает его только на время version checks и восстанавливает вместе с JAVA_HOME.

SDK tools содержит Android CLI launcher, который отдельно скачал runtime 1.0.16500706 при проверке версии. Этот runtime пока не является закреплённой частью bootstrap. CLI сообщает о сборе метрик и поддержке `--no-metrics`; дальнейшие вызовы выполнять с этим флагом. Не вызывать рекомендуемый им `android init`, поскольку проект выбирает и проверяет скиллы по одному.

## 2026-10-05: Android skeleton и проверка UI

Один модуль app, без пустых feature-модулей и DI framework. AGP 9.4.1/Gradle 9.6.0/JDK 17; встроенный Kotlin 2.2.10 и совпадающая версия Compose Compiler plugin. Используется [built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin), Compose BOM 2026.09.00 и Activity Compose 1.13.0; наличие стабильных артефактов проверено в официальных Maven metadata.

Установлены SDK platform 37.0, build-tools 36.0.0, platform-tools 37.0.1. compileSdk/targetSdk 37, minSdk 26: приложение ориентируется на текущую платформу и выполняет Play минимум 36. Версии приложения закреплены в version catalog; platform-tools ставится по SDK package ID и его фактическая версия записана здесь.

Robolectric 4.17 с native graphics выбран для поведения Compose и рендера без устройства. Это не замена device smoke test. FLAG_SECURE включён сразу, системный backup/transfer исключены, сетевые разрешения не добавлены. В UI нет фиктивных прогнозов и медицинских данных.

Форматирование — ktlint 1.8.0 через две JavaExec задачи, без отдельного Gradle plugin. CI на Ubuntu 24.04 использует actions, закреплённые по upstream SHA; содержит сборку, проверки и debug APK artifact. Публикация и release signing пока не реализованы.

Lint остаётся строгим для ошибок и предупреждений кода. Только AndroidGradlePluginVersion/NewerVersionAvailable имеют severity informational: они сообщают об обновлениях закреплённого toolchain, не о дефектах приложения. Gradle оставлен на официальной совместимой версии 9.6.0, Compose Compiler совпадает со встроенным Kotlin, вместо автоматического перехода на новую связку по совету lint. AGP обновлён до доступного stable patch 9.4.1; OldTargetApi исправлен переходом на target 37.

## 2026-10-05: обозначение JDK в GitHub Actions

Первый удалённый запуск остановился в setup-java: имя релиза `17.0.20.1+1` не проходит SemVer validation. CI использует `17.0.20+101` из Adoptium API для того же Linux x64 JDK; локальный bootstrap продолжает использовать закреплённый архив `jdk-17.0.20.1+1`. Это изменение обозначения, а не обновление Java или переход на диапазон версий.

Закреплённый [setup-java проверяет input через semver.validRange](https://github.com/actions/setup-java/blob/b6effb05e454b25005698d916606bdc6ffcbf961/src/distributions/base-installer.ts), [Temurin installer читает version_data.semver](https://github.com/actions/setup-java/blob/b6effb05e454b25005698d916606bdc6ffcbf961/src/distributions/temurin/installer.ts), а [сопоставление полного input учитывает build через compareBuild](https://github.com/actions/setup-java/blob/b6effb05e454b25005698d916606bdc6ffcbf961/src/util.ts). При обновлении JDK сверять официальный SemVer и наличие пакета для платформы runner, затем проверять полный CI.

## 2026-10-05: первая запись периода

Добавлены [Room 2.8.5](https://developer.android.com/jetpack/androidx/releases/room), KSP 2.3.12, Lifecycle 2.11.0. Минимальная модель — период с началом и необязательным окончанием; события/интенсивность кровотечения добавляются позже отдельными типизированными сущностями, а не набором boolean-полей периода.

Pure domain проверяет calendar dates; Room хранит epoch days и технические timestamp milliseconds. Создание проверяет пересечения внутри транзакции. Уникальный индекс начала — дополнительная защита. Открытый период блокирует добавление пересекающихся дат до завершения. Главный экран ограничен 30 последними записями; полная история будет доступна через будущий календарь.

Готовность хранения проверяется повторным открытием базы, конкурентными вставками и UI recreation. Schema v1 экспортирована, destructive fallback отсутствует. База не зашифрована: текущая защита — sandbox/backup exclusions/FLAG_SECURE; усиление безопасности относится к M5.
