package com.habitat.app.domain

/** XP progress within the current level, derived from total ledger XP. */
data class PlayerXpProgress(
    val level: Long,
    val xpIntoLevel: Long,
    val xpRequiredForNextLevel: Long,
    val fraction: Float
)

/** Pure progress helper API. This initial skeleton intentionally returns the level-one baseline. */
fun playerXpProgress(xpTotal: Long): PlayerXpProgress = PlayerXpProgress(
    level = 1L,
    xpIntoLevel = 0L,
    xpRequiredForNextLevel = xpToNext(1L),
    fraction = 0f
)
