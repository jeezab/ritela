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

`HomeScreenTest` запускает Activity/Compose в Robolectric на API 35, проверяет пустой экран, описание приватности, FLAG_SECURE, отсутствие backup и INTERNET. Отдельный тест рендерит светлую и тёмную темы в PNG: `app/build/reports/screenshots/home-light.png`, `home-dark.png`. Эти изображения предназначены для просмотра; pixel regression baseline ещё не создан.

Отчёты: `app/build/reports/tests/testDebugUnitTest/index.html` и `app/build/reports/lint-results-debug.html`. APK: `app/build/outputs/apk/debug/app-debug.apk`.

`verify-apk.ps1` проверяет подпись, application ID, min/target SDK, отсутствие сетевых разрешений и пишет SHA-256 рядом с APK. Это проверка debug артефакта, не release signing audit.

Установка на подключённое тестовое устройство Windows:

```powershell
& ./.toolchain/android-sdk/platform-tools/adb.exe devices
& ./.toolchain/android-sdk/platform-tools/adb.exe install -r ./app/build/outputs/apk/debug/app-debug.apk
& ./.toolchain/android-sdk/platform-tools/adb.exe shell am start -n app.ritela/.MainActivity
```

Instrumentation-набор и emulator пока не настроены; device testing нельзя считать пройденным по JVM-тесту. CI повторяет локальные build/unit/lint/format checks, сохраняет debug APK, SHA-256 и отчёты. До push фактический удалённый запуск CI не проверен.
