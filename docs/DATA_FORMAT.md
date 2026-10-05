# Локальное хранение

Room database v1: `ritela.db`, схема [1.json](../app/schemas/app.ritela.data.RitelaDatabase/1.json). Таблица `periods`:

| Поле | Формат |
|---|---|
| id | UUID, TEXT primary key |
| startDay | LocalDate.toEpochDay(), INTEGER, unique index |
| endDay | Epoch day или NULL для продолжающегося периода |
| createdAt | Instant epoch milliseconds |
| updatedAt | Instant epoch milliseconds |

Calendar date не переводится в UTC timestamp. Только адаптер Material DatePicker использует UTC midnight, как требует API picker; результат сразу преобразуется в LocalDate/epoch day.

Правила: начало/окончание не позже текущего локального дня; окончание не раньше начала; один день включительно допустим. Записи не пересекаются, включая граничный день. Открытый период занимает интервал до его явного завершения. Проверка пересечения и вставка атомарны; завершение обновляет только ещё открытый период и сохраняет UUID/createdAt.

Чтение главного экрана ограничено последними 30 записями. Удаление/редактирование, JSON export/import и формат зашифрованного backup пока не реализованы. Для последующих изменений схемы обязательна явная migration с автоматической проверкой; v1 не имеет предшествующей схемы и миграции пока отсутствуют.
