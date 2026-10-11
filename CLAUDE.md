# CLAUDE.md: Habitat

Read `SPEC.md` before doing anything. It is the source of truth for behavior. If code and spec disagree, ask which is right before changing either.

For any work on the world (M7 and later: items, shop, regions, hearth, monuments, eggs, wonders), read WORLD_DESIGN.md first. It extends SPEC.md sections 6 and 7; where they differ, SPEC.md wins until it is updated.

## Project
Habitat is an Android-first, local-first, gamified habit tracker written in Kotlin.

## Stack
- Kotlin, Jetpack Compose, Material 3
- Room (SQLite) for storage, DataStore for simple settings
- AlarmManager for inexact battery-efficient reminders
- Glance for widgets
- WorkManager is not used yet; it will be added when a feature needs it.
- Hilt for dependency injection
- JUnit 4 for unit tests; Turbine for Flow tests
- minSdk 26. Handle Android 13+ notification permission (`POST_NOTIFICATIONS`).

Do not add a new dependency without asking. Pin all versions in the version catalog.

## Architecture
```
app/            Android UI, DI wiring, Application class
domain/         Pure Kotlin module. NO Android imports.
                  - schedule evaluation
                  - consistency score, streak, rest tokens
                  - economy: Seeds/XP, caps, drops, level curve
                  - GameConfig (all tunable constants)
data/           Room entities, DAOs, repositories
platform/       Alarms, notifications, widgets, foreground service
```

Rules:
- All game rules live in `domain/` and are covered by unit tests.
- UI observes state via `StateFlow`; screens have a ViewModel; no logic in composables.
- Time always comes from an injected `Clock`. Never call `System.currentTimeMillis()` or `LocalDate.now()` directly in `domain/`.
- "Day" means the logical date defined by the user's day boundary (SPEC 4.3), not the calendar date.
- Currency balances are derived from the append-only ledger. Never store a mutable balance.
- Magic numbers go in `GameConfig`, not inline.

## Working style
1. **Plan first.** For any task bigger than a small fix, propose a plan and list open questions. Wait for a go-ahead.
2. **One milestone slice per session** (see SPEC section 12). Don't start the next milestone unprompted.
3. **Every slice ends runnable.** The app must build and launch before you say you're done.
4. **Tests for logic.** Any change in `domain/` needs tests. For consistency, streak, tokens and economy, write table-driven tests, including edge cases (day boundary, DST, month ends, backfill).
5. **Small commits** with clear messages. Suggest a commit message when finishing a slice.
6. Show diffs or summarize changed files. Don't silently rewrite unrelated files.
7. If something in the spec is ambiguous, ask instead of guessing.

## Code style
- Idiomatic Kotlin, immutable data classes, sealed types for states (`CompletionState`, `ScheduleType`).
- Public functions in `domain/` have KDoc stating the rule they implement and the SPEC section.
- No `!!` outside tests. Prefer explicit error types over exceptions for expected failures.
- Compose: stateless composables with hoisted state, previews for every screen.
- Accessibility: content descriptions, minimum 48dp touch targets, support font scaling.

## Product guardrails (don't violate without asking)
- No guilt-based or shaming copy. Missed-day messages are neutral and encouraging.
- Never sell Seeds or XP. Never paywall the core loop.
- No account required. App must work fully offline.
- Request permissions only when the feature needs them, with an explanation first.
- Cap daily earnings exactly as in SPEC 6.3.

## Commands
```
./gradlew assembleDebug
./gradlew test
./gradlew connectedAndroidTest
```

## Definition of done for a slice
- Builds and runs on a device or emulator
- New logic has passing unit tests
- No new warnings of note
- SPEC acceptance criteria for the milestone are met, or deviations are listed
- Summary of what changed and what's next
