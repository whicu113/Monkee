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

/** Maps a reward result to UI resource keys. This API skeleton intentionally emits no messages. */
fun mapRewardResultToMessages(result: HabitRewardResult): List<RewardMessageSpec> = emptyList()
