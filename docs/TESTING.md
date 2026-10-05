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

Тесты запускают Activity/Compose/SQLite в Robolectric на API 35. `HomeScreenTest` проверяет FLAG_SECURE/backup/INTERNET, add → Activity recreation → finish, редактирование с восстановлением формы и удаление с отменой/подтверждением. Календарные сценарии: выбранный прошлый день, открытие существующей записи, будущий день без кнопки записи, диапазон прогноза, свайп месяца и узкий экран со шрифтом 130%/200%. Все семь столбцов помещаются по ширине; месяцы переключаются вертикальными свайпами/стрелками и через заголовок с выбором года и месяца. Проверяются bounds первого/последнего столбцов.

`PeriodRulesTest` покрывает будущие/обратные даты, один день и 29 февраля. Repository tests проверяют DST, конкурентные вставки/правки, сохранность UUID/createdAt, отсутствие изменений при ошибке, reopen/delete и восстановление базы; история из 31 записи не усекается. ViewModel tests проверяют сохранение/ошибки, повторное нажатие и пересчёт прогноза из Room после правки/удаления. `CyclePredictionTest` проверяет расчёт и ограничения из [PREDICTION.md](PREDICTION.md), включая растущие диапазоны и отсутствие выдуманных прошедших циклов.

PNG в `app/build/reports/screenshots/`: пустой главный экран, заполненная история и текущая менструация в светлой/тёмной теме, узкий экран со шрифтом 200%, формы записи/окончания/правки/удаления, календарь с записями/прогнозом/тёмной темой и узкий календарь со шрифтом 130%/200%. Добавлены EN home/calendar/form, карточка прогноза в обеих темах/языках и EN forecast 320dp/fontScale 2.0 с доступным действием после прокрутки. Английский сценарий выполняет add/edit/reopen/delete и системный date picker. Это изображения для просмотра с синтетическими данными; pixel regression baseline ещё не создан. Room schema v1 сохраняется в `app/schemas/`; миграций пока нет, при их добавлении нужны отдельные migration tests.

Просмотр интерфейса без APK на телефоне:

```powershell
& ./scripts/preview-ui.ps1 -Open
# Если тесты уже выполнены и PNG актуальны:
& ./scripts/preview-ui.ps1 -SkipRender -Open
```

Без `-SkipRender` скрипт принудительно запускает HomeScreenTest и формирует `app/build/reports/screenshots/index.html`. Требуются настроенные локальные Java/SDK. Без `-Open` браузер не запускается. Галерея содержит 29 снимков настоящего Compose render; отображает время генерации каждого PNG, изображения открываются в полном размере. Это статический просмотр. CI создаёт ту же галерею после тестов; скачай `verification-reports`, распакуй ZIP и открой `screenshots/index.html` вместе с соседними PNG. PNG и HTML не коммитятся.

Для нажатий на компьютере: открой репозиторий в Android Studio, подготовь SDK/JDK по требованиям проекта, создай виртуальный телефон в Device Manager, выбери его и нажми Run. Обновления устанавливаются через Run/Apply Changes, без скачивания artifacts на телефон. [Официальная инструкция эмулятора](https://developer.android.com/studio/run/emulator). Эмулятор в текущем workspace не установлен и не проверен. Для отдельных состояний открой `RitelaApp.kt` в режиме Design/Split: добавлены previews пустого экрана, крупного текста и заполненной тёмной темы. Превью не открывает Room или реальные данные.

Новые сценарии: Настройки → изменение 28/5 → Save → Activity recreation; выбор года/месяца без клавиатуры; swipe up/down; запись диапазона двумя тапами через 29 февраля/границу месяца. Domain tests проверяют стартовую оценку после первого начала, measured duration и предполагаемые дни. ViewModel/settings tests проверяют повторное открытие preferences и пересчёт без изменения записей. Snapshot helper выбирает активный Dialog, чтобы не захватить уже закрытое окно выбора месяца.

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
