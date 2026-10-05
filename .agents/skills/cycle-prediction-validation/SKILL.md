---
name: cycle-prediction-validation
description: Validate changes to Ritela cycle estimates, forecast uncertainty, calendar date classification and recalculation after record mutations. Use when changing these rules or their UI presentation.
---

# Validate a Ritela forecast change

Read [the maintained algorithm](../../../docs/PREDICTION.md) and the changed domain/UI code. Update that document whenever formulas, data sufficiency or suppression rules change; parameters are engineering heuristics, not validated medical probabilities.

- Preserve LocalDate/epoch-day arithmetic, full-history calendar access and the distinction between recorded days, estimated starts, possible ranges and distant estimates. Home has measured charts; full period history stays in Calendar.
- Check stable/irregular intervals, sparse input, isolated/repeated outliers, missing records, open periods, invalid/duplicate/overlapping input, edit/delete, year/leap-day boundaries, ordering and restart reconstruction. Assert lower <= point <= upper and growing uncertainty. Do not silently invent elapsed cycles when a forecast is overdue.
- Sparse/open history after the first start uses explicit 28-day/5-day settings, until three intervals permit history statistics. Measured completed-period duration replaces the duration preset; estimated days remain distinct from facts. Verify settings persistence and reactive recalculation without changing records.
- During domain iteration run `& ./scripts/gradle.ps1 testDebugUnitTest --tests app.ritela.CyclePredictionTest`. Verify the Room -> ViewModel recalculation path for storage changes; domain tests alone cannot prove reactive updates.
- For presentation changes inspect actual calendar PNGs, including light/dark, narrow screens and large text. Future dates remain view-only. Quality labels describe history, never accuracy percentages or fertile/safe days.
- Before checkpoint run the existing Android verification workflow in `.agents/skills/ritela-android-check/SKILL.md`; save concrete results and limitations in STATUS. Do not claim device or remote CI validation from JVM tests.

- Both ends of a measured interval must belong to the past-year window (start >= today.minusYears(1)); use at most 12 recent intervals. Period duration is the median of at most 12 completed records in that window. Check ageing-out/leap-year boundaries, one long duration and fallback when no recent data remain. This window is a product heuristic informed by research, not a validated clinical prediction model.
