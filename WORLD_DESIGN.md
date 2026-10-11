# Habitat: World Design (v0.1)

This document extends `SPEC.md` section 7 (The world) and section 6 (Economy). Until `SPEC.md` is updated, `SPEC.md` wins where they differ. Section 15 lists the intended `SPEC.md` changes.

All numbers here are **starting values**. They belong in `GameConfig` or in data definitions and are expected to change after playtesting and after the pacing simulator (slice W1) exists.

Inspiration only: structure and feel are inspired by island-expansion games and village-builder games. No names, art, layouts or assets are copied. All art is original.

## 1. Purpose

The world is the visible result of the habits. Opening it should always show something that reflects what the player did, give a reason to come back tomorrow, and never run out of things to look forward to.

## 2. Principles

1. **Waiting earns nothing.** There is no passive income. Waiting only delivers what habits already earned (an egg hatching, a build finishing, an expedition returning).
2. **Nothing is ever lost.** A missed day never removes, shrinks or locks anything that was built. Current-state effects (glow, fireflies) are cosmetic bonuses only.
3. **Habits are the builders.** Progress is gated by habit activity, not by money alone and not by clock time.
4. **Hard gates use measures that never go down** (active days, total completions). Consistency only unlocks optional bonuses and cosmetics. This keeps "forgive misses" true.
5. **Always something to look forward to** on five timescales: day, week, month, season, year.
6. **No pay-to-skip, no raiding, no loss.** Premium sells cosmetics and convenience only. The core loop is never paywalled.
7. **Content is data.** Adding an item, region or hearth level must not require new code.

## 3. Definitions

- **Active day:** a logical date on which at least one DONE or MINIMUM completion was logged **on that same logical day** (completion logged for today's logical date, not backfilled). Backfilled days are not active days. SKIPPED does not count.
- **Pause (vacation/sick mode):** active-day counters and build timers stop; nothing is lost; nothing advances.
- **Hearth:** the central campfire. Its level is the main gate for content.
- **Region:** a fogged area around the island, belonging to one biome.
- **Monument:** a per-habit landmark that grows with that habit's completions.
- **Egg:** an item that hatches into a character after N active days.
- **Wonder:** a rare large landmark unlocked by a long-term feat.
- **World:** one island theme with its own hearth track. Finishing one opens the next.

## 4. Layer 1: The Hearth (vertical progression)

Only **one hearth upgrade runs at a time**. Starting an upgrade costs Seeds. It then builds for a number of **active days** (build days). During the build the island shows scaffolding and a progress strip.

Start requirements for level L: cumulative active days >= A(L) and Seeds >= cost(L). Build duration is B(L) active days.

| Level | A (cum. active days) | Seeds | B (build days) | Unlocks |
|---|---:|---:|---:|---|
| 2 | 2 | 50 | 1 | Hut, egg slot 1, Meadow region |
| 3 | 6 | 120 | 2 | Beach region, dock |
| 4 | 12 | 250 | 3 | Forest region, egg slot 2 |
| 5 | 20 | 450 | 4 | Expeditions (M9), larger island footprint |
| 6 | 35 | 700 | 5 | Rocky Hills region |
| 7 | 55 | 1,000 | 6 | Lagoon region, egg slot 3 |
| 8 | 80 | 1,400 | 7 | Wonder slot 1 |
| 9 | 110 | 1,900 | 8 | Volcano region |
| 10 | 150 | 2,500 | 10 | Snow Peak region, egg slot 4 |
| 11 | 190 | 3,200 | 12 | Sky Islet region |
| 12 | 240 | 4,000 | 14 | Wonder slot 2, portal to World 2 |

Notes:
- Sum of Seeds for levels 2 to 12 is 15,570.
- Sum of build days is 72. The earliest possible finish is bounded by A(12) = 240 active days.
- Levels beyond 12 belong to later worlds (each world defines its own track).
- Upgrade state: `targetLevel`, `buildActiveDaysDone`, `startedOn`. Each new active day (while not paused) adds 1 to `buildActiveDaysDone`. The upgrade completes when it reaches B(L).
- Level never decreases.

## 5. Layer 2: Island expansion (regions)

- The island grows by **regions** revealed from fog. Fogged tiles are visible as soft cloud but cannot be built on.
- A region has: id, biome, tile shape (set of tile coordinates), Seeds cost, minimum hearth level, one hidden treasure (a guaranteed item or egg), biome palette.
- Unlocking: pay Seeds, requires hearth level >= region.minHearth. The fog lifts with a reveal animation and the treasure is added to the inventory.
- Starting values (World 1):

| Region | Biome | Min hearth | Seeds |
|---|---|---:|---:|
| 1 | Meadow | 2 | 150 |
| 2 | Beach | 3 | 300 |
| 3 | Forest | 4 | 600 |
| 4 | Rocky Hills | 6 | 1,000 |
| 5 | Lagoon | 7 | 1,600 |
| 6 | Volcano | 9 | 2,400 |
| 7 | Snow Peak | 10 | 3,200 |
| 8 | Sky Islet | 11 | 4,000 |

- Region sum is 13,250 Seeds.
- Each biome has its own palette so the island never looks monotonous.
- Terrain painting inside owned regions keeps the SPEC cost (10 Seeds per tile).

## 6. Layer 3: Habit monuments (depth)

- Every habit gets a **monument** at its first completion, placed near the hearth. The player can move it.
- The player chooses a **monument family** at habit creation (default: tree). Launch families: Tree, Cairn. More later.
- Stage depends on the habit's total DONE/MINIMUM completions (backfilled completions count, because honest history should not be punished; they still pay no Seeds):

| Stage | Completions | Name (Tree family) |
|---|---:|---|
| 0 | 0 | Seed |
| 1 | 1 | Sprout |
| 2 | 7 | Sapling |
| 3 | 21 | Young tree |
| 4 | 66 | Grove |
| 5 | 150 | Ancient grove |
| 6 | 365 | Evergreen wonder |

- Stage never decreases. An archived habit's monument stays as a "retired" landmark and stops growing.
- Stage is **derived** from the Completion table, not stored.
- Rest tokens are shown as stones next to the monument (cosmetic).
- A small stage-up animation plays on the completion that crosses a threshold.

## 7. Layer 4: Discoveries (breadth)

### 7.1 Drops
The existing drop (5% on the first DONE grant of a habit and date, pity 30, rarity 70/22/7/1) now yields inventory items from the **unlocked pool** (hearth level and owned regions):

| Rarity | Result |
|---|---|
| common | decoration |
| uncommon | better decoration |
| rare | egg |
| legendary | wonder blueprint (counts toward wonder slots) |

### 7.2 Eggs
- Eggs go in a **nest** item. Slots are limited by hearth level.
- Incubation: 3 / 7 / 14 active days by rarity. Hatching yields a character from that rarity's table.
- No duplicate characters until the rarity tier is complete (the table skips owned characters first).

### 7.3 Wonders
Rare multi-cell landmarks unlocked by feats, placed once each. Starting feats: first 66 completions of a habit, 100 total completions, 30 active days, a full week with every scheduled habit done.

### 7.4 Almanac
A collection book of every item, character and wonder. Undiscovered entries show silhouettes. Discovery is recorded when an item first enters the inventory.

## 8. Layer 5: New worlds (it never ends)

- After hearth level 12 and all regions revealed, a portal opens to World 2: new biome theme, new hearth track, new regions, new catalog.
- Cosmetics, characters, the almanac and the player's level carry over.
- World 1 stays accessible.
- Starting plan: ship World 1 well first. World 2 follows the same data format.

## 9. What the player sees when opening the world

A **welcome moment** built from `WorldEvent` records since the last visit. Events are shown once, in order, then marked seen:

- build progress ("1 day left on your campfire upgrade")
- hearth level reached
- egg hatched or nearly ready
- region ready to reveal
- expedition returned (M9)
- monument stage up

If there are no events, show a neutral idle message. Never guilt copy. The HUD shows the single active project with days remaining.

Later: a timelapse of the island growing, replayed from item placement timestamps.

## 10. Economy and pacing

- Typical medium-habit completion pays about 25 Seeds (15 + up to 10 streak bonus). With 4 habits a day, roughly 100 Seeds a day, about 36,000 a year. The daily habit-Seeds cap of 300 bounds a maximum earner at about 110,000 a year.
- **World 1 total sink target: about 55,000 Seeds** (hearth 15,570 + regions 13,250 + items, terrain, eggs, cosmetics about 26,000). A typical player finishes World 1 in roughly 12 to 20 months. The earliest possible finish is about 8 months because of the active-day gate (A(12) = 240).
- Purchases are ledger entries with negative deltas and unique idempotency keys, written in the same transaction as the state change. A purchase fails if the balance would go negative. No refunds except the SPEC section 7.4 land refund on island re-design.
- No passive Seeds, no timer skips, no paid currency.
- A pure **pacing simulator** in `domain/` (slice W1) simulates players (50, 100, 300 Seeds a day; 70%, 85%, 100% active days) and reports the day each hearth level and region is reached. Pacing changes must pass through it.

## 11. Visual direction and performance

- Flat, soft-outlined, pastel isometric 2D with a palette per biome. Idle animations (swaying trees, water shimmer, character idles), particle bursts on habit completion, soft fog edge, existing day/night and weather.
- Rendering stays Canvas **provisional**. The M6 latency benchmark failure is still open: measure before adding effects, cache static layers, cull off-screen tiles. No performance claims without measurements on the target device.
- Art pipeline: placeholder geometric markers until a style is chosen; then a small consistent set (hearth 1 to 5, two biomes, about 30 items) before expanding.

## 12. Data model (design sketch, no schema yet)

Static content (code or data objects, no new dependencies):
- `ItemDefinition(id, name, category, rarity, footprint, seedCost?, minHearth, biome, artKey)` (extends `BuiltInPlaceableCatalog`; existing stable IDs stay)
- `HearthLevelDefinition(world, level, activeDaysRequired, seedCost, buildDays, unlocks)`
- `RegionDefinition(id, world, biome, tiles, seedCost, minHearth, treasure)`
- `CharacterDefinition`, `WonderDefinition(feat)`, `MonumentFamily(stages)`

Player state (Room, later migrations, each with tests):
- `ActiveDay(logicalDate PRIMARY KEY)`: written in the same transaction as the completion log, only when logged on its own logical day, and idempotent.
- `HearthState(world, level, targetLevel?, startedOn?, buildActiveDaysDone)`
- `RegionState(regionId, unlockedAt)`
- `InventoryEntry(itemId, count)`
- `HabitMonument(habitId, familyId, placedItemId?)`; stage derived
- `Egg(id, rarity, nestPlacedItemId?, startedOn?, activeDaysDone, requiredDays)`
- `Discovery(definitionId, firstSeenAt)`
- `WorldEvent(id, type, payload, createdAt, seenAt?)`
- Existing: `LedgerEntry`, `DropRecord`, placed world items (add `placedAt` for the timelapse).

## 13. Slice plan

Each slice: plan, open questions, my "go", small commits, tests, on-device checklist, owner result, owner tags. Unit tests for all pure rules.

| Slice | Scope | Acceptance |
|---|---|---|
| **W0** | Close M7 build mode: run the instrumented suite, device checklist (toolbar at 130% font, TalkBack, rotation, kill and reopen read-only, dark mode), tag | Suite green on one device |
| **W1** | Item catalog, inventory, shop with Seeds, terrain painting cost, **pacing simulator** | Purchase is atomic and idempotent; balance never negative; simulator runs in unit tests |
| **W2** | Regions and fog, expansion purchase, reveal animation, treasure | Region cost/min-hearth rules tested; fog tiles not buildable |
| **W3** | `ActiveDay` + Hearth upgrade (start, build, finish), requirements UI, pause handling | Build advances only on active days; backfill never advances it |
| **W4** | Habit monuments (auto-placement, stages, stage-up animation, retired state) | Stage derivation table-tested; never decreases |
| **W5** | Drops become items; eggs, nests, hatching; almanac | Pity and rarity tables tested; no early duplicates |
| **W6** | Welcome moment (`WorldEvent`), HUD project strip | Each event shown once |
| **M8** | Focus mode (as in SPEC) | per SPEC |
| **M9** | Expeditions (SPEC 7.3), weekly chest | per SPEC |
| **W7** | Biome art pass, World 2 data and portal | World 2 loads from data only |
| **W8** | Timelapse, share card, seasonal areas | as defined later |

## 14. Risks

- **Content volume** is the real risk for a solo build. Start small and keep content data-driven.
- **Economy pacing** can silently break. Use the simulator before shipping any number change.
- **Rendering performance** is unproven (M6 benchmark failure open).
- **Scope creep:** each slice stays small and device-verified.
- **Backfill and clock exploits:** active days exclude backfill; clock shifts are bounded by daily caps. Accepted: a local clock change can fake days in a single-player app.

## 15. Intended `SPEC.md` changes (separate docs commit, owner approval first)

- Section 7: add regions, hearth, monuments, eggs and wonders by reference to this document.
- Section 6.5: drops yield inventory items (rarity mapping in 7.1 above).
- Section 13 risks: add the accepted clock-exploit note for active days.

## 16. Open decisions (recommended defaults)

1. Active-day build timers, no wall-clock timers or paid skips (recommended).
2. Hard gates on active days and total completions; consistency only for bonuses (recommended).
3. No passive Seeds income (recommended).
4. One island first, then new worlds (recommended).
5. Monument created automatically at a habit's first completion and movable (recommended).
6. Backfilled completions count toward monument stage but not toward active days (recommended).
7. Hearth name: "Hearth" or "Campfire" (owner choice).
8. Monument families at launch: Tree and Cairn (owner can add).

## 17. Non-goals

PvP, raiding, loss or decay, passive Seeds, paid timer skips, ads, global leaderboards (until a deliberate later decision).
