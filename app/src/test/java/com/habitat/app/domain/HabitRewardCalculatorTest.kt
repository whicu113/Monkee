package com.habitat.app.domain

import com.habitat.app.domain.model.CompletionState
import com.habitat.app.domain.model.Difficulty
import com.habitat.app.domain.logic.DropRarity
import com.habitat.app.domain.logic.RewardStage
import com.habitat.app.domain.logic.calculateHabitReward
import com.habitat.app.domain.logic.rewardIdempotencyKey
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HabitRewardCalculatorTest {

    @Test
    fun `formula table applies difficulty and state factors with half up rounding`() {
        data class Row(val name: String, val difficulty: Difficulty, val state: CompletionState, val seeds: Int)
        val rows = listOf(
            Row("easy done", Difficulty.EASY, CompletionState.DONE, 10),
            Row("easy minimum", Difficulty.EASY, CompletionState.MINIMUM, 6),
            Row("medium done", Difficulty.MEDIUM, CompletionState.DONE, 15),
            Row("medium minimum half up", Difficulty.MEDIUM, CompletionState.MINIMUM, 9),
            Row("hard done", Difficulty.HARD, CompletionState.DONE, 20),
            Row("hard minimum", Difficulty.HARD, CompletionState.MINIMUM, 12)
        )

        rows.forEach { row ->
            val result = calculateHabitReward(
                state = row.state,
                difficulty = row.difficulty,
                streakLength = 0,
                comeback = false,
                grantedByStage = emptyMap(),
                seedsEarnedToday = 0,
                xpEarnedToday = 0,
                rng = FixedRewardRandom(99, 99),
                pityCounter = 0
            )
            assertEquals("${row.name}: Seeds entitlement", row.seeds, result.seedsGranted)
        }
    }

    @Test
    fun `streak bonus caps at ten`() {
        val rows = listOf(9 to 19, 10 to 20, 25 to 20)
        rows.forEachIndexed { index, (streak, expected) ->
            val result = calculateHabitReward(
                CompletionState.DONE, Difficulty.EASY, streak, false, emptyMap(), 0, 0,
                FixedRewardRandom(99, 99), 0
            )
            assertEquals("streak row $index: capped Seeds", expected, result.seedsGranted)
        }
    }

    @Test
    fun `comeback bonus multiplies entitlement and rounds half up`() {
        val result = calculateHabitReward(
            CompletionState.MINIMUM, Difficulty.MEDIUM, 0, true, emptyMap(), 0, 0,
            FixedRewardRandom(99, 99), 0
        )
        assertEquals("minimum medium comeback: half up Seeds", 14, result.seedsGranted)
    }

    @Test
    fun `Seeds cap limits grant without capping habit XP`() {
        data class Row(val name: String, val earned: Int, val expected: Int)
        val rows = listOf(Row("remaining room", 295, 5), Row("at cap", 300, 0), Row("over cap", 305, 0))
        rows.forEach { row ->
            val result = calculateHabitReward(
                CompletionState.DONE, Difficulty.EASY, 0, false, emptyMap(), row.earned, 149,
                FixedRewardRandom(99, 99), 0
            )
            assertEquals("${row.name}: Seeds", row.expected, result.seedsGranted)
            assertEquals("${row.name}: uncapped habit XP", 10, result.xpGranted)
        }
    }

    @Test
    fun `habit XP remains uncapped after the focus XP cap`() {
        val result = calculateHabitReward(
            CompletionState.DONE, Difficulty.EASY, 0, false, emptyMap(), 0, 150,
            FixedRewardRandom(99, 99), 0
        )
        assertEquals("habit XP row after focus cap", 10, result.xpGranted)
    }

    @Test
    fun `minimum then done pays entitlement difference and retains first grant inputs`() {
        val minimum = calculateHabitReward(
            CompletionState.MINIMUM, Difficulty.HARD, 4, true, emptyMap(), 0, 0,
            FixedRewardRandom(99, 99), 0
        )
        val upgraded = calculateHabitReward(
            CompletionState.DONE, Difficulty.EASY, 99, false,
            mapOf(RewardStage.MINIMUM to minimum.grantedAmounts!!),
            minimum.seedsGranted, minimum.xpGranted, FixedRewardRandom(99, 99), minimum.pityCounterAfter
        )
        assertEquals("upgrade: difference between original entitlements", 12, upgraded.seedsGranted)
        assertEquals("upgrade: no duplicate XP entitlement", 0, upgraded.xpGranted)
    }

    @Test
    fun `difficulty edits do not change already granted historical entitlement`() {
        val original = calculateHabitReward(
            CompletionState.DONE, Difficulty.HARD, 3, false, emptyMap(), 0, 0,
            FixedRewardRandom(99, 99), 0
        )
        val afterEdit = calculateHabitReward(
            CompletionState.DONE, Difficulty.EASY, 0, false,
            mapOf(RewardStage.DONE to original.grantedAmounts!!),
            original.seedsGranted, original.xpGranted, FixedRewardRandom(0, 0), original.pityCounterAfter
        )
        assertEquals("difficulty edit: no retroactive Seeds", 0, afterEdit.seedsGranted)
        assertEquals("difficulty edit: no retroactive XP", 0, afterEdit.xpGranted)
    }

    @Test
    fun `idempotency key separates habit date and reward stage`() {
        val date = LocalDate.of(2026, 10, 10)
        val rows = listOf(
            Triple("habit-a", date, RewardStage.MINIMUM) to "habit-a:2026-10-10:MINIMUM",
            Triple("habit-a", date, RewardStage.DONE) to "habit-a:2026-10-10:DONE",
            Triple("habit-b", date, RewardStage.MINIMUM) to "habit-b:2026-10-10:MINIMUM",
            Triple("habit-a", date.plusDays(1), RewardStage.MINIMUM) to "habit-a:2026-10-11:MINIMUM"
        )
        rows.forEachIndexed { index, (keyInput, expected) ->
            assertEquals("idempotency row $index: stable stage key", expected,
                rewardIdempotencyKey(keyInput.first, keyInput.second, keyInput.third))
        }
    }

    @Test
    fun `pity guarantees a drop on thirtieth done grant`() {
        val result = calculateHabitReward(
            CompletionState.DONE, Difficulty.EASY, 0, false, emptyMap(), 0, 0,
            FixedRewardRandom(0), 29
        )
        assertEquals("pity row: guaranteed common drop", DropRarity.COMMON, result.drop)
        assertEquals("pity row: reset after drop", 0, result.pityCounterAfter)
    }

    @Test
    fun `seeded rarity distribution follows configured weights within tolerance`() {
        val counts = DropRarity.entries.associateWith { 0 }.toMutableMap()
        val random = java.util.Random(451)
        repeat(10_000) { index ->
            val result = calculateHabitReward(
                CompletionState.DONE, Difficulty.EASY, 0, false, emptyMap(), 0, 0,
                com.habitat.app.domain.logic.RewardRandom { bound -> random.nextInt(bound) }, index % 29
            )
            result.drop?.let { counts[it] = counts.getValue(it) + 1 }
        }
        val expected = mapOf(
            DropRarity.COMMON to 70,
            DropRarity.UNCOMMON to 22,
            DropRarity.RARE to 7,
            DropRarity.LEGENDARY to 1
        )
        val dropCount = counts.values.sum()
        expected.forEach { (rarity, percent) ->
            val observed = counts.getValue(rarity) * 100.0 / dropCount
            assertEquals("rarity row $rarity: weighted share", percent.toDouble(), observed, 1.5)
        }
    }

    private class FixedRewardRandom(vararg values: Int) : com.habitat.app.domain.logic.RewardRandom {
        private val rolls = values.toList()
        private var index = 0

        override fun nextInt(boundExclusive: Int): Int {
            val value = rolls.getOrElse(index) { rolls.last() }
            index += 1
            return value.coerceIn(0, boundExclusive - 1)
        }
    }
}
