---
name: ui-quality-audit
description: Apply and review Ritela's saved visual style, bilingual copy and adaptive Compose screens. Use when designing or changing Ritela UI, including reference-based revisions.
---

# Review Ritela UI

Read [STYLE_GUIDE](../../../docs/STYLE_GUIDE.md) and the relevant screen. Theme.kt is the shared implementation: preserve the warm paper/plum/blush palette, one system SansSerif family for every typography role, including brand, dates and headings. Dynamic colors are off by default. Do not rediscover the previous green direction.

- Settings contain language, theme (System/Light/Dark) optional-password export/import and editable forecast duration (default 7), without a cycle-length control. Verify theme selection persists and changes actual screen colors. Preserve the underlying 28-day cycle default and separate measured duration and existing records.
- Adapt references to working mechanics. Phase claims, symptom values, charts and navigation entries require actual domain/storage/actions. Forecast uncertainty follows PREDICTION.md.
- Add copy in both values (English fallback) and values-ru; dates/plurals use the active locale. Exercise the English and Russian forms as well as renders.
- Inspect actual light/dark PNGs, narrow screens and fontScale 1.3/2.0. All seven calendar columns must fit the viewport without horizontal scrolling; check first/last cell bounds. Months scroll vertically and the header opens year/month selection. Target >=48dp, with the documented narrow-grid width exception; hide illustration before compressing data.
- Run the existing Android verification workflow, then `& ./scripts/preview-ui.ps1 -SkipRender`. New screenshot names must be registered in that script to appear in the gallery and CI artifact.
- Update STYLE_GUIDE only for an intentional change in direction; save verified behavior/limitations in STATUS and commit through ritela-checkpoint. JVM images do not prove TalkBack/device behavior.

- Home has measured charts instead of period history; full history/edit/delete belongs to Calendar. Day-log form is shared by today and selected past calendar days. Keep missing values distinct from explicit no pain. Inspect graphs and new dialogs, including password input with scrollable full-screen layout.

- Day journal configuration uses stable IDs, persistent names/icons/tag order, long-press drag and an accessible reorder action. Keep at least one tag per section; allow zero sections while retaining the note. Removing UI sections must preserve historical data and backups. Review red period and pale-green fertile colors in both themes.
