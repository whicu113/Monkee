package com.habitat.app.ui.reward

import com.habitat.app.domain.HabitRewardResult
import com.habitat.app.domain.RewardNoRewardReason
import com.habitat.app.domain.logic.DropRarity
import org.junit.Assert.assertEquals
import org.junit.Test

class RewardMessageMapperTest {

    @Test
    fun rewardResultsMapToExpectedMessageRows() {
        data class Row(
            val name: String,
            val result: HabitRewardResult,
            val expected: List<RewardMessageSpec>
        )

        val rows = listOf(
            Row(
                name = "first grant",
                result = HabitRewardResult.Granted(15, 10, null, false, 0),
                expected = listOf(
                    RewardMessageSpec(RewardMessageKey.SEEDS_EARNED, listOf(15)),
                    RewardMessageSpec(RewardMessageKey.XP_EARNED, listOf(10))
                )
            ),
            Row(
                name = "minimum to done delta has zero XP",
                result = HabitRewardResult.Granted(5, 0, null, false, 0),
                expected = listOf(RewardMessageSpec(RewardMessageKey.SEEDS_EARNED, listOf(5)))
            ),
            Row(
                name = "comeback grant",
                result = HabitRewardResult.Granted(23, 10, null, true, 0),
                expected = listOf(
                    RewardMessageSpec(RewardMessageKey.SEEDS_EARNED, listOf(23)),
                    RewardMessageSpec(RewardMessageKey.XP_EARNED, listOf(10)),
                    RewardMessageSpec(RewardMessageKey.COMEBACK)
                )
            ),
            Row(
                name = "zero Seeds at daily cap differs from already rewarded",
                result = HabitRewardResult.Granted(0, 10, null, false, 15),
                expected = listOf(
                    RewardMessageSpec(RewardMessageKey.DAILY_SEEDS_CAP),
                    RewardMessageSpec(RewardMessageKey.XP_EARNED, listOf(10))
                )
            ),
            Row(
                name = "zero Seeds and XP without cap has no message",
                result = HabitRewardResult.Granted(0, 0, null, false, 0),
                expected = emptyList()
            ),
            Row(
                name = "Done then Minimum is already rewarded with no message",
                result = HabitRewardResult.NoReward(RewardNoRewardReason.ALREADY_REWARDED),
                expected = emptyList()
            ),
            Row(
                name = "not scheduled has no message",
                result = HabitRewardResult.NoReward(RewardNoRewardReason.NOT_SCHEDULED),
                expected = emptyList()
            ),
            Row(
                name = "backfill has no message",
                result = HabitRewardResult.NoReward(RewardNoRewardReason.BACKFILL),
                expected = emptyList()
            ),
            Row(
                name = "unknown schedule has no message",
                result = HabitRewardResult.NoReward(RewardNoRewardReason.UNKNOWN_SCHEDULE),
                expected = emptyList()
            ),
            Row(
                name = "weekly goal reached has no message",
                result = HabitRewardResult.NoReward(RewardNoRewardReason.WEEKLY_GOAL_REACHED),
                expected = emptyList()
            ),
            Row(
                name = "already rewarded has no message",
                result = HabitRewardResult.NoReward(RewardNoRewardReason.ALREADY_REWARDED),
                expected = emptyList()
            ),
            Row(
                name = "skipped has no message",
                result = HabitRewardResult.NoReward(RewardNoRewardReason.SKIPPED),
                expected = emptyList()
            ),
            Row(
                name = "drop message uses the granted result field",
                result = HabitRewardResult.Granted(15, 10, DropRarity.RARE, false, 0),
                expected = listOf(
                    RewardMessageSpec(RewardMessageKey.SEEDS_EARNED, listOf(15)),
                    RewardMessageSpec(RewardMessageKey.XP_EARNED, listOf(10)),
                    RewardMessageSpec(RewardMessageKey.DROP_FOUND)
                )
            )
        )

        val failures = mutableListOf<String>()
        rows.forEach { row ->
            val actual = mapRewardResultToMessages(row.result)
            if (row.expected != actual) {
                failures += "${row.name}: mapped messages expected:<${row.expected}> but was:<$actual>"
            }
        }
        assertEquals("all reward message rows", emptyList<String>(), failures)
    }
}
