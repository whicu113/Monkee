package com.habitat.app.ui.reward

import com.habitat.app.domain.HabitRewardResult

/** Resource key categories returned by the copy-free UI reward mapper. */
enum class RewardMessageKey {
    SEEDS_EARNED,
    XP_EARNED,
    COMEBACK,
    DAILY_SEEDS_CAP,
    DROP_FOUND
}

/** Message key and numeric formatting arguments; Android resources resolve the copy in the UI. */
data class RewardMessageSpec(
    val key: RewardMessageKey,
    val arguments: List<Int> = emptyList()
)

/** Maps a reward result to ordered UI resource keys without resolving user-facing copy. */
fun mapRewardResultToMessages(result: HabitRewardResult): List<RewardMessageSpec> {
    if (result !is HabitRewardResult.Granted) return emptyList()

    return buildList {
        if (result.seeds > 0) {
            add(RewardMessageSpec(RewardMessageKey.SEEDS_EARNED, listOf(result.seeds)))
        } else if (result.capApplied > 0) {
            add(RewardMessageSpec(RewardMessageKey.DAILY_SEEDS_CAP))
        }

        if (result.xp > 0) {
            add(RewardMessageSpec(RewardMessageKey.XP_EARNED, listOf(result.xp)))
        }
        if (result.comebackApplied) {
            add(RewardMessageSpec(RewardMessageKey.COMEBACK))
        }
        if (result.drop != null) {
            add(RewardMessageSpec(RewardMessageKey.DROP_FOUND))
        }
    }
}
