# Проверки

Windows, из корня проекта:

```powershell
& ./scripts/gradle.ps1 formatKotlin
& ./scripts/gradle.ps1 checkKotlin assembleDebug testDebugUnitTest lintDebug
powershell -NoProfile -File scripts/check-workspace.ps1
powershell -NoProfile -File scripts/verify-apk.ps1
git diff --check
```

Скрипт использует локальные Java/SDK и восстанавливает переменные окружения. При первом запуске:

```powershell
powershell -NoProfile -File scripts/bootstrap-toolchain.ps1
powershell -NoProfile -File scripts/setup-sdk.ps1 -AcceptLicenses
```

Linux/macOS: подготовь JDK 17 и Android SDK, установи `platforms;android-37.0`, `build-tools;36.0.0`, `platform-tools`, задай ANDROID_HOME и запусти:

```sh
./gradlew checkKotlin assembleDebug testDebugUnitTest lintDebug --console=plain
```

Тесты запускают Activity/Compose/SQLite в Robolectric на API 35. `HomeScreenTest` проверяет пустой экран, FLAG_SECURE, отсутствие backup/INTERNET и сценарий add → Activity recreation → finish. `PeriodRulesTest` покрывает будущие/обратные даты, один день и 29 февраля. Repository tests проверяют календарные даты на DST-границе, транзакционную защиту от конкурентных дубликатов и восстановление после повторного открытия базы. ViewModel tests проверяют состояния сохранения/ошибки и повторное нажатие.

PNG в `app/build/reports/screenshots/`: пустой главный экран, заполненная история и текущая менструация в светлой/тёмной теме, узкий экран со шрифтом 200%, форма записи и выбор окончания. Это изображения для просмотра с синтетическими данными; pixel regression baseline ещё не создан. Room schema v1 сохраняется в `app/schemas/`; миграций пока нет, при их добавлении нужны отдельные migration tests.

Просмотр интерфейса без APK на телефоне:

```powershell
& ./scripts/preview-ui.ps1 -Open
# Если тесты уже выполнены и PNG актуальны:
& ./scripts/preview-ui.ps1 -SkipRender -Open
```

Без `-SkipRender` скрипт принудительно запускает HomeScreenTest и формирует `app/build/reports/screenshots/index.html`. Требуются настроенные локальные Java/SDK. Без `-Open` браузер не запускается. Галерея содержит 11 снимков настоящего Compose render; отображает время генерации каждого PNG, изображения открываются в полном размере. Это статический просмотр. CI создаёт ту же галерею после тестов; скачай `verification-reports`, распакуй ZIP и открой `screenshots/index.html` вместе с соседними PNG. PNG и HTML не коммитятся.

Для нажатий на компьютере: открой репозиторий в Android Studio, подготовь SDK/JDK по требованиям проекта, создай виртуальный телефон в Device Manager, выбери его и нажми Run. Обновления устанавливаются через Run/Apply Changes, без скачивания artifacts на телефон. [Официальная инструкция эмулятора](https://developer.android.com/studio/run/emulator). Эмулятор в текущем workspace не установлен и не проверен. Для отдельных состояний открой `RitelaApp.kt` в режиме Design/Split: добавлены previews пустого экрана, крупного текста и заполненной тёмной темы. Превью не открывает Room или реальные данные.

Отчёты: `app/build/reports/tests/testDebugUnitTest/index.html` и `app/build/reports/lint-results-debug.html`. APK: `app/build/outputs/apk/debug/app-debug.apk`.

`verify-apk.ps1` проверяет подпись, application ID, min/target SDK, отсутствие сетевых разрешений и пишет SHA-256 рядом с APK. Это проверка debug артефакта, не release signing audit.

Установка на подключённое тестовое устройство Windows:

```powershell
& ./.toolchain/android-sdk/platform-tools/adb.exe devices
& ./.toolchain/android-sdk/platform-tools/adb.exe install -r ./app/build/outputs/apk/debug/app-debug.apk
& ./.toolchain/android-sdk/platform-tools/adb.exe shell am start -n app.ritela/.MainActivity
```

Instrumentation-набор и emulator пока не настроены; device testing нельзя считать пройденным по JVM-тесту. CI повторяет локальные build/unit/lint/format checks, сохраняет debug APK, SHA-256 и отчёты. Пользователь подтвердил успешный CI после исправлений setup-java и Prepare SDK и скачал первый APK. Это подтверждение предыдущего workflow; удалённый запуск текущего изменения UI/галереи ещё не подтверждён.

Для setup-java указывай Adoptium `version_data.semver`, а не имя релиза или каталога JDK: `17.0.20+101` соответствует `jdk-17.0.20.1+1`. Локальный bootstrap сохраняет исходное обозначение релиза и закреплённый архив. При обновлении CI JDK проверяй SemVer и наличие Linux x64 JDK в Adoptium API; затем подтверждай полный удалённый запуск, включая APK artifacts.

На Ubuntu runner вызывай `"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"`, проверив executable, с `--sdk_root="$ANDROID_HOME"`. Не полагайся на наличие sdkmanager в PATH. apksigner/aapt2 также вызываются внутри ANDROID_HOME. Используются предустановленные command-line tools и лицензии образа GitHub; обновления образа могут менять версии этих tools. При изменении run-блоков проверяй Bash syntax и shell-вызов с макетом SDK вне PATH, включая распространение ошибок; такая проверка не заменяет установку пакетов и сборку на Linux runner.
