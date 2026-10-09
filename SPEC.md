# Habitat: Product Spec (v0.1)

Working name: **Habitat**. An Android-first, local-first, gamified habit tracker.
All numbers in this document are **starting values**. They live in one `GameConfig` object and are expected to change after playtesting.

## 1. Goals and non-goals

**Goals**
- Help users repeat small behaviors in a stable context (cue-based habits).
- Forgive misses. One miss must never feel like a reset.
- Make the game layer a reward for consistency, not a replacement for the habit.
- Work fully offline. No account required.

**Non-goals (v1)**
- No iOS, no web app, no global leaderboards, no AI features in the core loop.
- No ads. No sale of in-game currency.

## 2. Design principles
1. Habit first, game second.
2. Forgive misses (one miss is fine; the app helps you return).
3. Reward effort and consistency, not perfection.
4. No guilt-based copy or notifications.
5. The core loop is never paywalled.

## 3. Core daily loop
Cue fires (reminder or widget) -> user does the tiny action -> checks it off -> earns Seeds/XP -> world visibly progresses -> companion reacts.

## 4. Habits

### 4.1 Fields
| Field | Notes |
|---|---|
| name, emoji, color | required: name |
| cue | free text, "After I ___" (optional but prompted at creation) |
| full version | text, e.g. "Run 30 minutes" |
| minimum version | text, e.g. "Put on running shoes". Strongly prompted. |
| identity line | optional, e.g. "I'm an athlete" |
| schedule | see 4.2 |
| time window | optional start/end time for reminders |
| difficulty | EASY / MEDIUM / HARD (reward multiplier) |
| group | optional (e.g. Morning Routine), orderable |
| archived | soft delete |

> **Note on Schedule Edits**: Editing a habit's schedule currently rewrites its historical schedule evaluation for replay and scoring.

### 4.2 Schedules
- `DAILY`: repeat every day
- `WEEKDAYS(set)`: chosen days of the week
- `EVERY_N_DAYS(n)`: fixed anchor at the habit's start date
- `N_PER_WEEK(n)`: available all week until `n` completions; its `n` occurrences resolve at week end (`min(c, n)` completions, `n - c` MISSED). Week start day is a global setting (default Monday). For a habit created mid-week, `n` is capped at the days remaining in that first week.
- `UNKNOWN`: habits with an unrecognized schedule version. Excluded from replay and scoring (displayed as "needs app update"). Unknown schedule strings are preserved on save.

### 4.3 Day boundary
A "day" ends at a user-configurable hour (default 04:00 local). All completions are attributed to a logical date, not a calendar date. Time comes from an injected `Clock`.

### 4.4 Completion states
| State | Counts for consistency | Counts for streak | Seeds |
|---|---|---|---|
| DONE | 1.0 | yes | 100% |
| MINIMUM | 0.7 | yes | 60% |
| SKIPPED (planned) | excluded from denominator | neutral (no break, no length) | 0 |
| MISSED | 0 | see 5.2 | 0 |
| PROTECTED (rest token used) | excluded | yes (preserved) | 0 |

Only user-logged states (`DONE`, `MINIMUM`, `SKIPPED`) are persisted. `MISSED` and `PROTECTED` are derived on replay. Today's logical date is pending: NOT `MISSED` and NOT in the consistency denominator unless logged. Users can backfill the previous 7 days in the UI; future dates are blocked.

## 5. Consistency instead of streaks

### 5.1 Consistency score
Per habit, over the trailing 30 scheduled occurrences (excluding SKIPPED/PROTECTED):

```
score = sum(weight of each occurrence) / count(occurrences)      // weights from the table above
```

Returns `null` when there are no resolved occurrences in the trailing 30 window.
Overall score = mean of active habit scores with non-null consistency. This is the **headline number** on the home screen.

### 5.2 Streak (secondary, optional display)
- Streak length counts ONLY `DONE`, `MINIMUM`, and `PROTECTED`.
- **Two-miss rule:** a single `MISSED` between completions neither adds to streak length nor resets it; two consecutive `MISSED` reset the streak to 0. `SKIPPED` is neutral.
- A setting "Strict streaks" makes any `MISSED` reset the streak to 0.

### 5.3 Rest tokens
- Earned: +1 progress per `DONE`/`MINIMUM` completion of that habit. Award +1 rest token at 7 progress points and reset progress to 0. Cap 3 tokens per habit. No progress accumulates while at the cap of 3 tokens.
- On a `MISSED` occurrence, if the habit has ≥ 1 token and "auto-use" is on, the occurrence becomes `PROTECTED` and one token is spent.
- Never purchasable. Never repairable after the fact.

### 5.4 Comeback bonus
The first `DONE` or `MINIMUM` within 2 scheduled occurrences after a `MISSED` pays +50% Seeds and shows a short "back on track" message. `PROTECTED` does not trigger the comeback bonus. Computed at log time, not on replay. Copy must be encouraging, never shaming.

## 6. Economy

### 6.1 Currencies
- **Seeds**: spendable.
- **XP**: drives level only (never spent).

### 6.2 Earning (starting values)
```
habitSeeds = 10 * difficultyMultiplier * stateFactor + streakBonus
  difficultyMultiplier: EASY 1.0, MEDIUM 1.5, HARD 2.0
  stateFactor: DONE 1.0, MINIMUM 0.6
  streakBonus: min(streakLength, 10)   // flat Seeds, DONE/MINIMUM only
comebackBonus: +50% on that completion
habitXp = 10 per DONE/MINIMUM
focusSeeds = 1 per focused minute (sessions >= 5 min)
focusXp = 1 per focused minute
```

### 6.3 Caps (anti-exploit)
- Habit Seeds per logical day: 300.
- Focus Seeds per logical day: 120. Focus XP per day: 120.
- A focus session's duration is measured from monotonic time; sessions are rejected if the clock jumps.
- Every currency change is an append-only ledger row (see 9).

### 6.4 Level curve
`xpToNext(level) = 100 + 25 * (level - 1)`

### 6.5 Drops (variable reward)
- 5% chance per DONE completion of a random item/character drop.
- Pity: guaranteed drop after 30 completions without one.
- Rarity weights: common 70, uncommon 22, rare 7, legendary 1.

### 6.6 Sinks
Decorations, terrain painting (10 Seeds per tile), new islands, cosmetics, companion outfits.

### 6.7 Fairness
- Premium never sells Seeds or XP.
- Level-gated items exist but all are earnable for free.

## 7. The world

### 7.1 Map
- Isometric tile grid. Terrain types: deep water, shallow water, sand, dirt, grass.
- Placeable items occupy 1 or more tiles and can be moved, rotated, flipped and removed.
- Build mode: palette, place-many, eraser, undo/redo.
- Islands: start with a small island; buy additional islands (3x3 up to 7x7).

### 7.2 Living world
- Characters path-find on walkable tiles and interact with some items (hammock, fire).
- Day/night follows real local time. Weather states: clear, rain, fog.
- Ambient audio (waves, birds, fire) with a mute toggle.
- The companion settles in a spot during focus sessions.

### 7.3 Expeditions
- When every habit scheduled today is DONE, MINIMUM or SKIPPED, the companion departs on an expedition.
- Returns after 3 to 6 hours (random) with a drop from the expedition table.
- Max one per logical day.
- Widget shows the expedition status.

### 7.4 Reset
"Re-design island" keeps owned items and refunds land. "Restart game" wipes the world and progress after a confirmation. Habit data is never wiped by either.

## 8. Features

### 8.1 Reminders
- Per habit: fixed time or **adaptive time** = median of the last 7 to 14 completion times (needs at least 5 samples), clamped to the habit's window.
- Notification actions: Done, Minimum, Skip, Snooze (15/30/60 min).
- Global cap: 4 habit notifications per day. Quiet hours supported.
- Copy rules: encouraging, specific, no guilt.
- Android: exact alarms need the right permission; reschedule after reboot.

### 8.2 Focus mode
- Countdown and stopwatch, pause/resume, breaks with a return reminder, session notes and tags.
- Ongoing notification via a foreground service; end-of-session alarm.
- **Strict mode (optional, v3):** blocks chosen apps during the session. Reward 1.25x. Quitting early forfeits that session's reward (no Seed penalty).
- Habit-linked focus blocks (v3): block chosen apps in a time window, or until a habit is completed. Temporary unblock costs Seeds; the amount is configurable.

### 8.3 Insights
- Consistency over time (line chart), calendar heat map per habit.
- Weakest habits, best day of week, best time of day.
- **Miss reasons:** after a MISSED, one tap on a chip (tired, no time, forgot, plans changed, other).
- **Weekly review:** summarizes patterns and suggests shrinking a minimum or moving a cue.

### 8.4 Journal
Daily entry with optional prompts. A journal streak grows a bonfire in the world.

### 8.5 Social (v3)
- Buddy or small group of 2 to 5, shared weekly quest, encouragement-only messages.
- Optional small weekly leagues. No global leaderboard by default.

### 8.6 Wellbeing
- Vacation and sick modes pause scheduling with no penalty and no token loss.
- Notification and animation intensity settings.

### 8.7 Widgets
- Today's progress ring plus tappable habit list (completes in place).
- Companion/expedition widget.

### 8.8 Health Connect (v3)
Optional auto-completion for step, sleep and workout habits.

### 8.9 Data
- Local-first. Export and import as a single JSON file.
- Optional encrypted sync (later).
- Request only the permissions a feature needs, at the moment it's used.

## 9. Data model (Room / SQLite)

```
Habit(id, name, emoji, color, cue, fullVersion, minVersion, identity,
      scheduleType, scheduleParams, windowStart, windowEnd,
      difficulty, groupId, sortOrder, archived, createdAt)
HabitGroup(id, name, sortOrder)
Completion(id, habitId, logicalDate, state, completedAt, reason, note)
RestToken(habitId, count, earnedProgress)
LedgerEntry(id, currency, delta, reason, refId, createdAt)   // append-only; balance = sum
PlayerState(level, xp, lastDropPity, settings...)
WorldItem(id, islandId, type, x, y, rotation, flipped)
Island(id, width, height, terrain blob)
Character(id, type, rarity, positionOrNull)
Expedition(id, startedAt, returnsAt, claimed, rewardJson)
FocusSession(id, habitId?, startedAt, endedAt, mode, tag, note, rewardGranted)
JournalEntry(id, logicalDate, text)
Settings(key, value)
```

Rules:
- Balances are derived from `LedgerEntry`, never stored as a mutable number.
- `Completion` has a unique constraint on (`habitId`, `logicalDate`).

## 10. Screens
1. Onboarding (pick 1 to 3 starter habits, set cue and minimum, set reminder time)
2. Home / Today (consistency ring, habit list, check-in)
3. World (map, build mode, shop)
4. Habit editor
5. Focus
6. Insights and weekly review
7. Journal
8. Settings (day boundary, streak mode, notifications, data export, wellbeing modes)

## 11. Monetization (suggested)
- Free: unlimited habits, widgets, core loop, all gameplay-earnable items.
- Premium (one-time or subscription): cosmetics, themes, encrypted sync, advanced analytics.
- No paywall before the user has completed their first habit.

## 12. Milestones and acceptance criteria

**M0: Project skeleton.** App launches, git initialized, CI-less local build works.

**M1: Habit CRUD.** Create, edit, archive habits; persisted; unit tests for schedule evaluation.

**M2: Check-off and consistency.** Four states work; consistency and streak rules (5.1 to 5.3) pass a table of unit tests, including two-miss rule, token use and day boundary edge cases.

**M3: Reminders.** Fixed-time reminders survive reboot; notification actions work; cap respected.

**M4: Widget.** Today's list updates and completes in place.

**M5: Economy.** Ledger, caps, drops with pity, level curve; tests for every formula in section 6.

**M6: World spike.** Isometric grid with placeholder tiles holds 60 fps with 200 items on a mid-range device.

**M7: World v1.** Build mode, shop, save/load.

**M8: Focus mode.**

**M9: Expeditions, insights, weekly review.**

**M10 and later:** strict mode and app blocking, buddy quests, sync, Health Connect.

## 13. Risks
- Economy balance and exploits (mitigate with caps, ledger, tests).
- Notification fatigue (mitigate with caps and quiet hours).
- Art scope creep (use placeholder art until M7 is stable).
- Evidence for adaptive personalization is mixed, so keep those features optional helpers.
