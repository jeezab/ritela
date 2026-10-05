# Локальное хранение

Room database v2: `ritela.db`, схема [2.json](../app/schemas/app.ritela.data.RitelaDatabase/2.json); исходная [v1](../app/schemas/app.ritela.data.RitelaDatabase/1.json) сохранена для migration. Таблица `periods`:

| Поле | Формат |
|---|---|
| id | UUID, TEXT primary key |
| startDay | LocalDate.toEpochDay(), INTEGER, unique index |
| endDay | Epoch day или NULL для продолжающегося периода |
| createdAt | Instant epoch milliseconds |
| updatedAt | Instant epoch milliseconds |

Calendar date не переводится в UTC timestamp. Только адаптер Material DatePicker использует UTC midnight, как требует API picker; результат сразу преобразуется в LocalDate/epoch day.

Правила: начало/окончание не позже текущего локального дня; окончание не раньше начала; один день включительно допустим. Записи не пересекаются, включая граничный день. Открытый период занимает интервал до его явного завершения. Проверка пересечения и вставка атомарны; завершение обновляет только ещё открытый период и сохраняет UUID/createdAt.

Редактирование проверяет пересечения, исключая собственную запись, и обновляет даты внутри транзакции. UUID/createdAt сохраняются; updatedAt обновляется. Неудачная правка не меняет данные. Удаление — отдельная операция по UUID; UI требует подтверждение. Повторная операция над отсутствующим UUID возвращает ошибку и не создаёт запись. Завершение дополнительно проверяет startDay <= endDay в SQL для защиты от конкурентной смены начала.

DAO/Repository читают все записи: это нужно календарю и расчёту цикла. История доступна в календаре целиком; на главной графики. Производные прогнозы вычисляются заново и не сохраняются в таблице. Реализован зашифрованный JSON backup и merge: [BACKUP.md](BACKUP.md). Схема v2 добавляет day_logs; явная MIGRATION_1_2 не меняет periods. Для последующих изменений схемы обязательна явная migration с автоматической проверкой.

Исходные настройки хранятся отдельно в приватных SharedPreferences `settings`: `cycleLength` INT (default 28, 1..365) и `periodDuration` INT (default 5, 1..60). Они не переписывают даты Room. SettingsRepository использует синхронный commit в IO dispatcher, затем публикует StateFlow; combine с Room вызывает пересчёт. Автоматический системный backup остаётся запрещён; ручная копия зашифрована паролем. Предполагаемые дни будущих периодов не вставляются в таблицу periods.

Тема в тех же preferences: themeMode STRING = SYSTEM / LIGHT / DARK, по умолчанию SYSTEM. Сохранение в IO с последующей публикацией отдельного StateFlow. Ранее сохранённые cycleLength/periodDuration остаются совместимыми; их выбор убран из интерфейса. Настройки темы не меняют схему Room.

Таблица day_logs (v2): day INTEGER PRIMARY KEY (epoch day), nullable TEXT headache/cramps/backache/flow/mood/energy с enum names, sex TEXT (имена в алфавитном порядке через запятую), note TEXT (≤1000 символов). Боль NONE/MILD/MODERATE/SEVERE; flow NONE/LIGHT/MEDIUM/HEAVY; mood CALM/HAPPY/LOW/ANXIOUS/IRRITABLE; energy LOW/NORMAL/HIGH. Sex NONE/CONDOM/NO_BARRIER/VAGINAL/ORAL/ANAL/MASTURBATION/OTHER: несколько тегов за день, NONE отдельно от остальных. NULL/пустой набор — не отмечено; NONE — явное отсутствие. Пустая отметка удаляется, сохранение — Upsert по дню. Будущие дни запрещены. Форма draft переживает пересоздание Activity; ошибочное сохранение не закрывает форму.
