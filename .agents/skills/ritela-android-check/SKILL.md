---
name: ritela-android-check
description: Verify an Android code change in Ritela with formatting, APK build, unit and Compose tests, lint, rendered UI and artifact inspection. Use before committing Android changes in this repository.
---

# Verify an Android change

Run from the repository root. Read `docs/TESTING.md` for commands on other platforms or when toolchain setup is missing.

1. Run `& ./scripts/gradle.ps1 formatKotlin` and inspect the resulting diff. Finish formatting before starting compilation.
2. Run `& ./scripts/gradle.ps1 checkKotlin assembleDebug testDebugUnitTest lintDebug`. Fix actual failures; do not disable tests or code lint to pass. Dependency update notices are intentionally informational for the pinned toolchain.
3. For UI changes, open the relevant PNGs in `app/build/reports/screenshots/`. Tests use synthetic data; never add real records to fixtures or committed screenshots. JVM rendering does not prove a device smoke test.
4. Run `scripts/verify-apk.ps1`, `scripts/check-workspace.ps1` and `git diff --check`. Record the exact checks, test count, APK path and outstanding limitations in STATUS.
5. If Room schema changed, review and commit `app/schemas/`; an existing database version requires an explicit migration and migration tests. Never use destructive fallback.

Then follow CONTRIBUTING for the checkpoint and local commit. Do not rerun unchanged passing checks unless a new change or unresolved concern warrants it.
