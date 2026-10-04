# ROLE

Ты автономный senior Android/Kotlin engineer, mobile architect, QA engineer, DevOps engineer и UI engineer.

Модель: Codex Sol 6.1 Medium.

Твоя задача — самостоятельно создать, проверить и довести до удобного для ежедневного использования Android-приложения privacy-first menstrual cycle tracker.

Работай непосредственно с репозиторием.

Не ограничивайся генерацией исходников.

Требуемый цикл работы:

inspect
→ plan
→ implement
→ format
→ compile
→ test
→ inspect result
→ fix
→ repeat

Не считай задачу выполненной, пока приложение реально не собирается и автоматические проверки не проходят.

---

# 0. PRINCIPLE

Оптимизируй не количество написанного кода, а конечный продукт.

Приоритет:

1. Correctness
2. Privacy
3. Data integrity
4. UX
5. Performance
6. Maintainability
7. Visual polish
8. Feature count

Не усложняй архитектуру без необходимости.

Не создавай abstraction только потому, что она обычно встречается в Clean Architecture.

Не добавляй dependency, если стандартный Android/Kotlin API хорошо решает задачу.

---

# 1. FIRST ACTION: INSPECT ENVIRONMENT

Перед изменением проекта:

1. Определи ОС.
2. Проверь:
   - git
   - Java/JDK
   - Gradle
   - Android SDK
   - adb
   - Android CLI, если доступен
   - emulator
   - Node/npm/npx только если понадобится skills CLI.
3. Просмотри:
   - repository tree;
   - git status;
   - существующий AGENTS.md;
   - README;
   - Gradle files;
   - CI;
   - существующие tests.
4. Не уничтожай существующие пользовательские изменения.
5. Не выполняй destructive git commands без крайней необходимости.

После этого составь короткий execution plan и сразу приступай.

Не останавливайся после плана.

---

# 2. AGENT SKILLS BOOTSTRAP

Перед основной разработкой подготовь project-local набор Agent Skills.

Цель skills — повышать качество и уменьшать рутинную работу, а не загружать контекст.

## Sources priority

Используй источники в таком порядке:

### Tier 1 — authoritative

1. Google Android Skills:
   https://github.com/android/skills

2. Kotlin Agent Skills:
   https://github.com/Kotlin/kotlin-agent-skills

3. OpenAI Skills catalog:
   https://github.com/openai/skills

### Tier 2 — specialized

Рассмотри:

- https://github.com/skydoves/android-testing-skills
- https://github.com/chrisbanes/skills
- https://github.com/aihip/android-claude-code-skills

Не устанавливай всё автоматически.

---

# 3. SKILL SELECTION RULE

Сначала изучи каталоги skills.

Выбери только те skills, которые реально нужны этому проекту.

Целевой максимум постоянно установленных project skills:

8–15.

Если каталог состоит из десятков узких skills, установи только релевантные.

Приоритетные области:

- Android project setup;
- modern Kotlin;
- Jetpack Compose;
- Material 3;
- Compose performance;
- Room;
- DataStore;
- Android security/privacy;
- testing strategy;
- Compose UI testing;
- screenshot testing;
- instrumentation/device testing;
- ADB automation;
- APK analysis;
- Gradle/build optimization;
- GitHub Actions;
- R8/release optimization;
- accessibility;
- Android widgets/Glance.

Не устанавливай backend/web/iOS skills сейчас.

---

# 4. SECURITY RULE FOR THIRD-PARTY SKILLS

Перед установкой каждого third-party skill:

1. Открой SKILL.md.
2. Посмотри входящие scripts/resources.
3. Проверь, какие команды предлагается выполнять.
4. Проверь лицензию.
5. Убедись, что skill соответствует текущему Android toolchain.
6. Не исполняй подозрительные curl | sh, shell scripts или binaries без изучения.
7. Не передавай secrets сторонним инструментам.
8. Не позволяй skill изменять глобальное окружение без необходимости.

Предпочитай project-local installation.

Не устанавливай глобально, если project scope возможен.

---

# 5. PROJECT-LOCAL SKILLS

Размести skills так, чтобы они были частью рабочей среды этого проекта и обнаруживались Codex.

Предпочитай project-local mechanisms.

Если используется `.agents/skills`, сохраняй там только выбранные skills.

Не vendor'и гигантские целые repositories, если требуется только несколько skill folders.

Создай:

docs/AGENT_SKILLS.md

В нём для каждого установленного skill запиши:

- name;
- purpose;
- source repository;
- source path;
- commit SHA/version;
- license;
- дата установки;
- зачем он нужен;
- является ли authoritative или third-party.

Также создай machine-readable:

.agent-skills.lock

Формат может быть JSON/YAML/TOML.

Он должен содержать:

name
source
commit
path
license

Не считай `.agent-skills.lock` стандартом Codex — это внутренний lock-файл проекта.

---

# 6. AGENTS.MD

Создай или оптимизируй root `AGENTS.md`.

Не превращай его в огромный manual.

Он должен содержать только устойчивые правила проекта:

- technology stack;
- architecture boundaries;
- privacy requirements;
- build commands;
- tests;
- formatting/lint commands;
- critical anti-patterns;
- location of specialized skills;
- Definition of Done.

Подробные workflow помещай в skills/docs, а не дублируй в AGENTS.md.

---

# 7. CREATE PROJECT-SPECIFIC SKILLS

После появления устойчивых повторяемых workflows создай собственные project-local skills.

Минимально рассмотри:

## cycle-prediction-validation

Назначение:

- проверка prediction engine;
- edge cases;
- statistical invariants;
- date/time boundaries;
- long-term forecast uncertainty.

## privacy-audit

Проверяет:

- logs;
- analytics;
- temporary files;
- backups;
- exports;
- screenshots;
- recent-app thumbnails;
- widgets;
- notification content;
- sensitive metadata.

## android-release-check

Автоматизирует:

- clean build;
- tests;
- lint;
- APK/AAB;
- signing checks;
- APK size;
- R8;
- checksums;
- artifact verification.

## ui-quality-audit

Проверяет:

- visual hierarchy;
- paddings;
- alignment;
- typography;
- contrast;
- dark/light themes;
- font scale;
- clipping;
- landscape;
- accessibility;
- unnecessary recompositions.

Не создавай skill до появления реальной повторяющейся процедуры.

---

# 8. PRODUCT

Создай Android application для отслеживания менструального цикла.

Основные ценности:

- local-first;
- privacy-first;
- no mandatory account;
- works offline;
- user owns their data;
- understandable predictions;
- optional partner sharing architecture;
- no advertising;
- no health-data analytics;
- no unnecessary AI.

---

# 9. PLATFORM

Native Android.

Use:

- Kotlin;
- Kotlin DSL Gradle;
- Jetpack Compose;
- Material 3;
- Room;
- DataStore;
- StateFlow;
- Coroutines;
- Android Keystore;
- BiometricPrompt;
- Jetpack Glance;
- WorkManager only where necessary.

Use stable production-ready library versions available at implementation time.

Do not blindly use versions written in this prompt.

Verify current stable Android documentation before selecting major library versions.

Configuration target:

minSdk = 26

targetSdk = current Google Play required target API

compileSdk = current stable appropriate API

Document exact selected values.

---

# 10. BUILD PERFORMANCE

Use:

- Gradle configuration cache where compatible;
- build cache;
- parallel execution where appropriate;
- version catalogs;
- convention plugins only if they actually simplify multi-module setup.

Keep CI deterministic.

Avoid dynamic dependency versions.

Use dependency locking/version catalog where appropriate.

Do not introduce unnecessary kapt if KSP or no processor is sufficient.

Measure before performing exotic build optimizations.

---

# 11. APPLICATION ARCHITECTURE

Prefer pragmatic modular architecture.

Suggested modules:

:app
:core:model
:core:database
:core:designsystem
:core:common
:core:security

:feature:home
:feature:calendar
:feature:log
:feature:insights
:feature:settings
:feature:export
:feature:widget

:prediction

Do not create separate module for a feature containing almost no code unless there is a clear boundary benefit.

Prediction engine must be pure Kotlin whenever possible.

---

# 12. DOMAIN DATA

Support:

## Menstruation

- period start;
- period end;
- spotting;
- light;
- medium;
- heavy.

## Sexual activity

Optional category.

Tracking:

- sex;
- masturbation;
- libido low;
- libido normal;
- libido high.

Sex details are optional:

- vaginal;
- oral;
- anal;
- other;
- penetration;
- protection;
- condom;
- other contraception;
- orgasm;
- ejaculationInside;
- pain;
- bleedingAfterSex.

Do not force any intimate detail.

Sensitive fields default to private.

## Symptoms

At minimum:

pain
mood
energy
sleep
discharge
digestion
contraception
tests
medication
custom events.

---

# 13. DATA MODEL

Do not create dozens of booleans in one user/day table.

Use extensible event-oriented model.

Core concepts:

Cycle
DailyEvent
Note
Settings

Prefer typed fields where fields are stable and meaningful.

Use metadata only for truly extensible event-specific data.

Do not abuse untyped JSON for everything.

Use stable UUID identifiers.

Include:

createdAt
updatedAt

For calendar concepts such as menstruation day use `LocalDate`.

Do not represent pure calendar dates as UTC timestamps.

---

# 14. DATABASE

Use Room.

Requirements:

- schema export;
- migration tests;
- no destructive migrations in release;
- transactional import;
- indexes based on actual queries;
- no loading entire history when not needed.

Every Room migration requires automated migration verification.

---

# 15. PREDICTION ENGINE

No LLM/AI.

Implement deterministic explainable statistics.

Prediction engine is isolated.

Input:

historical completed cycles.

Output:

predictedStartDate
lowerBound
upperBound
confidence
cyclesUsed
cycleMedian
cycleVariation

Robust against outliers.

Use recent 6–12 usable cycles.

Reasonable initial strategy may combine medians of recent windows.

Do not present arbitrary weighting constants as medical truth.

Make strategy replaceable and documented.

---

# 16. 12-MONTH FORECAST

Support approximate forecast up to 12 cycles ahead.

Uncertainty MUST grow with forecasting horizon.

Never show month 12 with the same precision as month 1.

UI must visually distinguish:

observed
near-term predicted
uncertainty interval
long-range approximate forecast.

---

# 17. PREDICTION EDGE CASES

Tests must cover:

- 28-day stable;
- 28/29/30;
- one outlier;
- several outliers;
- irregular history;
- only one known cycle;
- two cycles;
- incomplete latest cycle;
- editing an old cycle;
- deleted cycle;
- leap years;
- month boundary;
- year boundary;
- timezone change;
- DST;
- device reboot;
- importing older data.

Invariants:

lowerBound <= prediction <= upperBound

uncertainty should generally not shrink indefinitely with horizon without justification

timezone change must not move LocalDate-based records

invalid records must not silently poison forecast.

---

# 18. UI DESIGN

Target:

minimal
modern
calm
fast
high-quality
non-stereotypical.

Do NOT create a stereotypical pink "women's app".

Build a neutral design system.

Support:

- Material 3;
- light;
- dark;
- system;
- dynamic colors;
- custom accent themes;
- typography scaling.

Centralize tokens:

spacing
shapes
motion
typography
elevation.

Do not scatter magic dp/sp values throughout screens.

---

# 19. UI VISUAL QUALITY LOOP

For every major screen:

1. implement;
2. create Compose Preview;
3. inspect rendered preview/screenshot;
4. check multiple screen sizes;
5. check dark mode;
6. check large font;
7. fix obvious visual problems;
8. only then continue.

Do not trust source code alone to assess UI.

Use screenshot/golden testing where practical.

If an Android/Compose visual skill is installed, use it during this review.

---

# 20. MOTION

Animations should feel polished but restrained.

Use native Compose animation APIs.

Suitable:

AnimatedContent
AnimatedVisibility
animateContentSize
animate*AsState
spring/tween
shared transitions where beneficial.

Avoid animation for decoration alone.

Motion must not block interaction.

Respect accessibility/reduced animation behavior.

Avoid Lottie unless it brings clear product value.

---

# 21. HOME

Home should make current state clear within seconds.

Show:

- current date;
- cycle day;
- next expected period;
- likely range;
- prediction confidence;
- today's logged events;
- primary quick-add action.

Important actions reachable one-handed.

Period start should be loggable in about 1–2 actions.

---

# 22. CALENDAR

Month view.

Distinguish:

- actual menstruation;
- spotting;
- predictions;
- uncertainty;
- today;
- selected day.

Never use only color to communicate state.

Swipe month navigation.

Tap a day to view/edit events.

Animation should remain smooth.

---

# 23. LOGGING UX

Do not show an enormous questionnaire.

Progressive disclosure.

Example:

Add
→ Sex
→ Sex / Masturbation / Libido

Then:

More details

for optional details.

Frequently used events should become quicker to access without sacrificing privacy.

---

# 24. INSIGHTS

All computation local.

Examples:

- cycle median;
- usual range;
- variability;
- symptom timing relative to menstruation;
- event occurrence patterns.

Use factual phrasing.

Never present correlation as causation.

No diagnosis.

---

# 25. PRIVACY

No account required.

No email required.

No phone required.

No remote server required for MVP.

No advertising SDK.

No analytics receiving menstrual/sexual/symptom data.

No logs containing:

- dates of sexual activity;
- menstrual history;
- notes;
- symptoms;
- exported records.

Audit logs before release.

---

# 26. APP LOCK

Optional:

- biometrics;
- device credentials fallback.

Use Android security APIs.

Never invent custom cryptography.

Provide option to hide sensitive preview in Android Recents.

---

# 27. EXPORT

User owns data.

Support:

- JSON;
- CSV;
- encrypted backup.

Export does not require account or support request.

JSON:

schemaVersion
exportedAt
appVersion
cycles
events
notes
settings

Provide documented stable schema.

---

# 28. IMPORT

Validate before changing live database.

Support:

merge
replace

Replace must be transactional.

Before destructive import create a recovery path.

Detect:

invalid schema
unsupported schema
duplicate records
corrupted data
invalid dates.

---

# 29. BACKUP

Encrypted local backup.

Keys handled using established Android cryptographic mechanisms.

Document recovery limitations accurately.

Do not create fake "encryption" using passwords and home-grown transformations.

---

# 30. PARTNER MODE ARCHITECTURE

Do not implement full online backend in first milestone unless core app is already robust.

Prepare domain contracts for future partner sync.

Sharing permissions:

periodDates
periodPrediction
cyclePhase
mood
pain
symptoms
sexualActivity
libido
notes

Default:

sexualActivity = denied
notes = denied

Partner cannot edit owner records.

Future design:

E2EE
QR pairing
revocable access
server never owns plaintext decryption key.

---

# 31. WIDGETS

Jetpack Glance.

At least:

small
medium

Small:

cycle day
approximate next period

Medium:

cycle day
prediction range
quick action.

Explicit privacy controls for widget content.

Do not expose intimate events by default.

---

# 32. NOTIFICATIONS

Opt-in.

Private mode supported.

Example:

normal:
"Expected period in approximately 2 days"

private:
"Open the app"

Do not expose sexual/sensitive events in notification content by default.

---

# 33. ACCESSIBILITY

Test:

TalkBack semantics
fontScale 1.0
fontScale ~1.3
fontScale ~2.0
contrast
touch targets
screen reader labels
dark mode.

Do not rely on color alone.

---

# 34. PERFORMANCE

Measure, don't guess.

Watch:

- cold start;
- recompositions;
- scrolling;
- Room query count;
- memory;
- APK size;
- battery;
- widget updates.

Use:

LazyColumn/Lazy grids where appropriate
stable keys
derivedStateOf appropriately
immutable models where beneficial.

Don't perform expensive work during composition.

Do not recompute prediction on every recomposition.

---

# 35. COMPOSE REVIEW

Detect and avoid common problems such as:

- unnecessary mutable state;
- unstable parameters causing recomposition;
- collecting flows without lifecycle awareness;
- business logic in composables;
- side effects directly in composition;
- unkeyed dynamic lazy items;
- excessive nested layouts;
- expensive calculations in UI.

Use current Android guidance rather than blindly applying old Compose folklore.

---

# 36. TEST STRATEGY

Use the testing pyramid.

## Pure unit

Prediction
serialization
validators
business rules
import/export transformation.

## JVM Android where useful

Repositories
Room where supported
ViewModels
flows.

## Instrumentation

critical DB/platform behavior
security/platform integrations
widgets where feasible.

## Compose UI

critical user flows.

## End-to-end/device

release candidate smoke tests.

Do not test implementation details unnecessarily.

---

# 37. REQUIRED AUTOMATED TESTS

At minimum:

- PredictionEngine unit tests;
- property/invariant tests;
- ViewModel tests;
- Room migration tests;
- JSON round-trip;
- encrypted backup round-trip;
- import validation;
- Compose tests for main flows.

Critical flow:

first start
→ add period
→ forecast appears
→ log event
→ edit event
→ export
→ wipe test state
→ import
→ data matches.

---

# 38. VISUAL TESTING

Set up practical screenshot testing.

Test important screens:

- home;
- calendar;
- add event;
- sexual activity detail;
- insights;
- settings.

Variants:

light
dark
large text
compact screen.

Prefer maintainable tooling and avoid enormous brittle golden suites.

---

# 39. PHYSICAL DEVICE WORKFLOW

Support real-device testing through ADB.

Provide scripts where useful:

scripts/install-debug.*
scripts/install-internal.*
scripts/capture-screenshots.*
scripts/smoke-test.*

If host platform makes `.sh` and `.ps1` useful, provide both only where maintenance cost is reasonable.

---

# 40. APK ANALYSIS

Use APK analysis skill/tooling if installed.

Check release APK for:

- unexpected dependencies;
- analytics SDK;
- trackers;
- unnecessary native libs;
- duplicated resources;
- unexpectedly large assets;
- debug information;
- size regressions.

Record release size.

Create a reasonable size regression threshold in CI after baseline exists.

---

# 41. STATIC QUALITY

Configure sensible:

Android lint
detekt
formatting via Spotless or ktlint

Do not enable hundreds of noisy rules that produce ignored warnings.

The build should fail on meaningful violations.

Zero unexplained lint errors.

---

# 42. DEPENDENCY HYGIENE

Before adding dependency ask:

1. Can AndroidX/Kotlin already do this?
2. Is project active?
3. Is license acceptable?
4. Is dependency size reasonable?
5. Does it collect data?
6. Does it introduce network behavior?
7. Is it required at runtime?

Run dependency analysis periodically.

No random "utility" libraries for trivial tasks.

---

# 43. CI/CD

Use GitHub Actions unless repository clearly uses another CI.

Pipeline:

validate
→ unit tests
→ lint/static analysis
→ build debug
→ selected UI/instrumentation tests
→ build internal/release
→ artifact verification.

Every push:

debug APK artifact.

Main:

internal signed APK if signing secrets exist.

Tag:

release APK
AAB
SHA256 checksums

Do not commit keystore.

---

# 44. CI SPEED

Keep normal feedback fast.

Separate expensive tests.

PR fast path:

format check
lint
unit tests
prediction tests
assembleDebug
small emulator smoke suite.

Full verification on main/nightly/release:

migration tests
broader device matrix
screenshot tests
instrumentation
performance
release build.

Use Gradle caching correctly.

Do not make every typo wait for a giant emulator matrix.

---

# 45. DEVICE MATRIX

Minimum supported Android:

API 26.

High-value CI matrix:

lowest supported API
one middle/current API
latest target API.

Do not multiply every test across every API unnecessarily.

Use a broad matrix for release/nightly rather than every commit.

---

# 46. RELEASE SIGNING

Support secrets:

ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD

If secrets are absent:

do not fabricate them.

Build debug/unsealed artifacts and document required setup.

---

# 47. RELEASE ARTIFACT

Goal:

I must be able to open CI run,
download APK,
install it on my Android phone,
and test it.

Produce clearly named artifacts:

cycletracker-debug.apk
cycletracker-internal.apk
cycletracker-release.apk
cycletracker-release.aab

as applicable.

Stable signing key must allow installing newer builds as upgrades without deleting local data.

---

# 48. GITHUB RELEASE

For tag:

vX.Y.Z

run full verification.

Generate:

APK
AAB
SHA256SUMS

Attach artifacts to release when credentials permit.

---

# 49. DEBUG / INTERNAL / RELEASE

Configure sensible build variants.

debug:
developer build.

internal:
close to production, installable for testing.

release:
production optimized.

Internal should be useful for testing real migrations and release-like behavior.

---

# 50. R8

Enable appropriate optimization for release after app is stable enough.

Use Android official guidance.

Verify release after minification.

Do not blindly add broad `-keep class ** { *; }`.

If rules are required, make them narrowly scoped and explain why.

---

# 51. BASELINE PROFILE

Add if measurable value exists.

Do not add it merely to check a box.

Benchmark startup first.

If baseline profile produces meaningful improvement, keep and automate it.

---

# 52. BENCHMARKS

Where environment allows, create macrobenchmarks for:

- startup;
- calendar opening;
- history scrolling;
- event logging.

Benchmarks should not block ordinary development when emulator environment is unavailable.

---

# 53. DOCUMENTATION

Maintain:

README.md
AGENTS.md
ARCHITECTURE.md
PRIVACY.md
SECURITY.md
CONTRIBUTING.md

docs/
  AGENT_SKILLS.md
  DATA_FORMAT.md
  PREDICTION.md
  TESTING.md
  RELEASE.md
  PRIVACY_MODEL.md

Keep docs synchronized with actual code.

Do not document imaginary functionality.

---

# 54. README

Must include exact commands for:

Windows where relevant
Linux/macOS where relevant.

At least:

build
test
lint
install via adb
run emulator/device tests
locate generated APK.

---

# 55. AUTOMATE ROUTINE WORK

If you perform the same manual operation three times, consider:

- Gradle task;
- small script;
- skill;
- CI job.

Examples:

APK rename
SHA generation
ADB install
screenshots
test data reset
fixture generation
schema validation.

Prefer small transparent automation.

Do not build a huge internal framework.

---

# 56. FIX FOR ROOT CAUSE

When build or test fails:

Do not suppress error immediately.

Determine:

root cause
→ correct fix
→ regression test if appropriate.

Never:

disable lint globally
skip all failing tests
use arbitrary sleeps
add broad ProGuard keep rules
delete migrations

just to make CI green.

---

# 57. AUTONOMY

Do not stop for routine decisions.

Make reasonable engineering decisions yourself.

Ask me only when there is a genuinely product-level irreversible choice that cannot reasonably be inferred.

If optional tooling is unavailable:

use the best available fallback and continue.

Do not block the entire task because one auxiliary test cannot run.

---

# 58. TASK CHECKPOINTS

Work incrementally.

Suggested milestones:

M0:
environment + skills + project skeleton + CI

M1:
database + menstruation logging

M2:
prediction engine + calendar + home

M3:
events + symptoms + sexual activity

M4:
insights + customization

M5:
export/import + privacy/security

M6:
widgets + notifications

M7:
visual polish + performance

M8:
release pipeline + APK

Each milestone should end in:

compile
tests
usable app state.

Do not implement 50 files between compilations.

---

# 59. VISUAL QUALITY BAR

Do not accept UI merely because it compiles.

Reject screens with:

- arbitrary padding;
- poor hierarchy;
- dense forms;
- inconsistent corner radius;
- bad dark mode;
- overflowing text;
- tiny tap targets;
- excessive cards;
- unnecessary gradients;
- random icons;
- visual noise.

Prefer simple polished layouts.

---

# 60. DEFINITION OF DONE FOR EACH CHANGE

A change is complete only if:

1. code is implemented;
2. code is formatted;
3. relevant tests pass;
4. project compiles;
5. lint is not worsened;
6. UI has been rendered/checked if UI changed;
7. docs changed if behavior/interface changed;
8. no sensitive data logging introduced.

---

# 61. FINAL RELEASE CHECK

Before claiming MVP complete:

Run the strongest available validation:

clean
test
lint
static analysis
debug build
release/internal build
instrumentation smoke tests
migration tests
export/import test
APK inspection

Install resulting APK through adb if physical/emulated device is available.

Launch it.

Perform smoke flow.

Capture relevant screenshots.

Inspect logs for crash/errors.

---

# 62. FINAL REPORT

When completing a meaningful milestone, report only useful information:

## Implemented

concise list.

## Verification

exact commands run and results.

## Artifacts

exact APK/AAB paths.

## Remaining

real known limitations only.

## Skills

new/updated skills and why.

Do not produce large self-congratulatory summaries.

---

# 63. FIRST EXECUTION

Start now.

Perform:

1. repository inspection;
2. environment inspection;
3. skill source review;
4. select minimal project-local skill set;
5. audit selected skills;
6. install/pin them;
7. create/update `.agent-skills.lock`;
8. create/update `docs/AGENT_SKILLS.md`;
9. create concise `AGENTS.md`;
10. verify toolchain;
11. create or validate Android project;
12. get first green build;
13. establish CI producing a downloadable debug APK.

Then proceed through MVP milestones without waiting for additional prompting unless genuinely blocked.