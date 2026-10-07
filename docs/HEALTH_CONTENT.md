# Карточки помощи

Проверено по первичным источникам 2026-10-05. Встроены 18 коротких статей EN/RU. Текст доступен офлайн; кнопка источника открывает браузер только по нажатию. Медицинские данные не отправляются источнику. Каталог: `ui/HelpCards.kt`, тексты: `values/help_strings.xml` и `values-ru/help_strings.xml`.

| Карточки | Первичный источник |
|---|---|
| warmth, movement, pain-care, pain-urgent | [NHS: period pain](https://www.nhs.uk/symptoms/period-pain/) |
| headache, headache-urgent, headache-diary | [NHS: headaches](https://www.nhs.uk/symptoms/headaches/) |
| ibuprofen | [NHS: ibuprofen for adults](https://www.nhs.uk/medicines/ibuprofen-for-adults/) |
| paracetamol | [NHS: paracetamol for adults](https://www.nhs.uk/medicines/paracetamol-for-adults/) |
| heavy, bleeding-care | [NHS: heavy periods](https://www.nhs.uk/conditions/heavy-periods/) |
| pms, pms-diary, supplements | [NHS: PMS](https://www.nhs.uk/conditions/pre-menstrual-syndrome/) |
| emergency | [NHS: emergency contraception](https://www.nhs.uk/contraception/emergency-contraception/) |
| condoms | [NHS: condoms](https://www.nhs.uk/contraception/methods-of-contraception/condoms/) |
| test | [NHS: pregnancy tests](https://www.nhs.uk/pregnancy/trying-for-a-baby/doing-a-pregnancy-test/) |
| late | [NHS: missed or late periods](https://www.nhs.uk/symptoms/missed-or-late-periods/) |

Это справочник самопомощи, а не диагностическая или назначающая модель. UK-источники не заменяют местную инструкцию препарата. Дозировки относятся к обычным таблеткам для взрослых 18+, ограничения и инструкция показываются перед дозой. Checkbox — самостоятельное подтверждение, а не проверка возраста/медицинского профиля. Список противопоказаний краткий; беременность, хронические болезни и другие препараты требуют проверки у специалиста. Рецептурные гормоны, антибиотики и схемы лечения по фазе не назначаются.

Порядок карточек определяется явными отметками: сильная головная/тазовая боль → срочная помощь; обильные выделения → их признаки; «без презерватива» → условная информация о контрацепции, без вывода о риске беременности; затем просроченный диапазон, отмеченная боль/настроение и общие статьи. На экране четыре карточки, полный список доступен через «Все статьи». Отметки не устанавливают причину боли или ПМС. В календаре статья относится к выбранному дню, но прогноз остаётся текущим.

`scripts/check-health-content.ps1` проверяет уникальные ID, наличие EN/RU, HTTPS-ссылку NHS и 18+ в названии лекарственной карточки. Он не проверяет действительность URL, точность доз или клиническую безопасность. Ссылки и содержание проверяются вручную по источникам при изменении. Перед публичным распространением нужен отдельный клинический review; сейчас он не проводился.

Обновление 2026-10-06: только condoms EN/RU повторно сверены по NHS: новый/неповреждённый презерватив, срок годности, совместимая смазка, обращение после разрыва/соскальзывания и новый презерватив при продолжении секса. Дозировки не менялись. Надпись help_reviewed удалена из UI, даты проверки остаются здесь. В calendar day details добавлена справка о недостаточности дат для вероятности беременности со ссылкой на [NHS natural family planning](https://www.nhs.uk/contraception/methods-of-contraception/natural-family-planning/) (прочитано 2026-10-06); это не оценка риска/фертильного окна. Каталог 18 статей не расширялся.

2026-10-07: вместо отсутствия оценки показано предполагаемое фертильное окно без персонального процента. Сверены [NICHD](https://www.nichd.nih.gov/newsroom/digital-media/infographics/conception-textalt), [NHS periods](https://www.nhs.uk/conditions/periods/) и NHS natural family planning: календарные даты не подтверждают овуляцию. Семидневный шаблон — продуктовая эвристика, не метод контрацепции. Тексты EN/RU кратко указывают неопределённость; удалён отдельный disclaimer у sex-тегов. Дозы и каталог статей не менялись.
