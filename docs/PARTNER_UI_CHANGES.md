# Изменения интерфейса и подключения 2026-10-11

Контурное сердце стало серым, опция тегов получила справку и название «Без проникновения / Без партнёра». Новый стандартный ORAL включает её; сохранённые явные значения и записи не меняются.

Partner: новое знакомство, карточки близкого/обмена/дудлов и короткий общий код. При первом подключении A сканирует B и подтверждает, B сканирует обновлённый QR A и подтверждает, A сканирует обновлённый QR B. Обычные обмены после этого не требуют нового кода. Отказ при несовпадении не подключает контакт. Старые доверенные контакты сохраняются. Подробности протокола: [PARTNER](PARTNER.md).

Проверки: форматирование, сборка debug APK, checkKotlin, lint debug/release и 57 релевантных unit/Compose тестов — успешно. В том числе совместимость экспорта/импорта, история тегов, отказ/подписи/новое устройство, 320dp/200% и сканер. Без снимков/эмулятора; Compose Previews добавлены, но изображения не генерировались и не просматривались.

Ограничения: новое подключение требует обновления обоих телефонов до протокола v2; Bluetooth использует уже сопряжённые телефоны; камера/Bluetooth/Keystore требуют проверки на физических устройствах. Перенос личности и календарных записей остаётся разными операциями. Восьмизначный код сам по себе не устанавливает доверие — полный контекст должен совпасть в подписанном подтверждении второго телефона.

Изменённые файлы (два коммита этого запроса):

- `app/src/main/AndroidManifest.xml`
- `app/src/main/kotlin/app/ritela/data/JournalRepository.kt`
- `app/src/main/kotlin/app/ritela/data/PartnerCodec.kt`
- `app/src/main/kotlin/app/ritela/data/PartnerCrypto.kt`
- `app/src/main/kotlin/app/ritela/data/PartnerPairing.kt`
- `app/src/main/kotlin/app/ritela/data/PartnerRepository.kt`
- `app/src/main/kotlin/app/ritela/domain/JournalLayout.kt`
- `app/src/main/kotlin/app/ritela/domain/Partner.kt`
- `app/src/main/kotlin/app/ritela/ui/CalendarScreen.kt`
- `app/src/main/kotlin/app/ritela/ui/DayLogEntry.kt`
- `app/src/main/kotlin/app/ritela/ui/PartnerCaptureActivity.kt`
- `app/src/main/kotlin/app/ritela/ui/PartnerChoices.kt`
- `app/src/main/kotlin/app/ritela/ui/PartnerDesign.kt`
- `app/src/main/kotlin/app/ritela/ui/PartnerScreen.kt`
- `app/src/main/kotlin/app/ritela/ui/PartnerViewModel.kt`
- `app/src/main/res/values-ru/journal_updates.xml`
- `app/src/main/res/values-ru/partner_strings.xml`
- `app/src/main/res/values-ru/partner_updates.xml`
- `app/src/main/res/values/journal_updates.xml`
- `app/src/main/res/values/partner_strings.xml`
- `app/src/main/res/values/partner_updates.xml`
- `app/src/test/kotlin/app/ritela/JournalChangesTest.kt`
- `app/src/test/kotlin/app/ritela/JournalEditingUiTest.kt`
- `app/src/test/kotlin/app/ritela/PartnerControlsTest.kt`
- `app/src/test/kotlin/app/ritela/PartnerPairingTest.kt`
- `app/src/test/kotlin/app/ritela/PartnerReliabilityTest.kt`
- `app/src/test/kotlin/app/ritela/PartnerTest.kt`
- `app/src/test/kotlin/app/ritela/PartnerUiTest.kt`
- `docs/DATA_FORMAT.md`
- `docs/DECISIONS.md`
- `docs/PARTNER.md`
- `docs/PARTNER_UI_CHANGES.md`
- `docs/PROJECT_MAP.md`
- `docs/STATUS.md`
- `docs/STYLE_GUIDE.md`
- `docs/prompts/2026-10-11-partner-onboarding.txt`
