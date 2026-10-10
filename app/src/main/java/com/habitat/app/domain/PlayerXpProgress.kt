package com.habitat.app.domain

/** XP progress within the current level, derived from total ledger XP. */
data class PlayerXpProgress(
    val level: Long,
    val xpIntoLevel: Long,
    val xpRequiredForNextLevel: Long,
    val fraction: Float
)

/** Derives progress within the current level from the append-only ledger XP total. */
fun playerXpProgress(xpTotal: Long): PlayerXpProgress {
    val currentLevel = level(xpTotal)
    val xpAtLevelStart = xpRequiredBeforeLevel(currentLevel)
    val xpIntoLevel = xpTotal - xpAtLevelStart
    val xpRequired = xpToNext(currentLevel)

    return PlayerXpProgress(
        level = currentLevel,
        xpIntoLevel = xpIntoLevel,
        xpRequiredForNextLevel = xpRequired,
        fraction = xpIntoLevel.toFloat() / xpRequired.toFloat()
    )
}

private fun xpRequiredBeforeLevel(level: Long): Long {
    val completedLevels = level - 1L
    if (completedLevels == 0L) return 0L

    val otherFactor = 175L + 25L * completedLevels
    return if (completedLevels % 2L == 0L) {
        (completedLevels / 2L) * otherFactor
    } else {
        completedLevels * (otherFactor / 2L)
    }
}
