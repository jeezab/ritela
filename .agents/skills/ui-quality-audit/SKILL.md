---
name: ui-quality-audit
description: Apply and review Ritela's saved visual style, bilingual copy and adaptive Compose screens. Use when designing or changing Ritela UI, including reference-based revisions.
---

# Review Ritela UI

Read [STYLE_GUIDE](../../../docs/STYLE_GUIDE.md) and the relevant screen. Theme.kt is the shared implementation: preserve the warm paper/plum/blush palette, serif headings and sans actions. Dynamic colors are off by default. Do not rediscover the previous green direction.

- Adapt references to working mechanics. Phase claims, symptom values, charts and navigation entries require actual domain/storage/actions. Forecast uncertainty follows PREDICTION.md.
- Add copy in both values (English fallback) and values-ru; dates/plurals use the active locale. Exercise the English and Russian forms as well as renders.
- Inspect actual light/dark PNGs, narrow screens and fontScale 1.3/2.0. All seven calendar columns must fit the viewport without horizontal scrolling; check first/last cell bounds. Months scroll vertically and the header opens year/month selection. Target >=48dp, with the documented narrow-grid width exception; hide illustration before compressing data.
- Run the existing Android verification workflow, then `& ./scripts/preview-ui.ps1 -SkipRender`. New screenshot names must be registered in that script to appear in the gallery and CI artifact.
- Update STYLE_GUIDE only for an intentional change in direction; save verified behavior/limitations in STATUS and commit through ritela-checkpoint. JVM images do not prove TalkBack/device behavior.
