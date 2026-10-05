---
name: health-content-check
description: Review Ritela symptom-based health cards and medicine reference content against current primary sources, bilingual limits and safety behavior. Use when adding or editing recommendations, doses or their selection rules.
---

# Review health cards

Read [the maintained catalogue](../../../docs/HEALTH_CONTENT.md) and only the changed articles. Browse the current primary source before changing medical claims; a saved source URL or the structural script is not clinical verification.

- Keep EN/RU meanings aligned. Medicine reference cards must state adult applicability, formulation, dose interval, daily maximum, duration limits, contraindications and interactions. Preserve the age/caution acknowledgement before displaying doses; this is not an individual prescription or age verification.
- Prioritize urgent-care articles for severe symptoms. An absent day log differs from an explicit symptom-free entry. Do not diagnose a phase, PMS, pregnancy or contraceptive protection from dates or sex tags.
- Source links open only on user action in the external browser. Keep article text available offline; never upload logs to obtain recommendations. Do not expose health records in error messages or diagnostics.
- Run `& ./scripts/check-health-content.ps1` for catalogue/resource/source metadata, then the existing Android checks. Exercise the affected article and day-log flow, including the adult-dose gate, and inspect EN/RU PNGs.
- Update HEALTH_CONTENT with source review date, selection rules and limitations. Product changes do not replace a clinician's review before public distribution. Save verified progress through ritela-checkpoint.
