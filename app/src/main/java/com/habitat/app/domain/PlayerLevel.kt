package com.habitat.app.domain

/** XP required to advance from [level] to the next level. */
fun xpToNext(level: Long): Long {
    require(level >= 1L) { "level must be at least 1" }
    val increments = level - 1L
    if (increments > (Long.MAX_VALUE - GameConfig.XP_TO_NEXT_LEVEL_BASE) / GameConfig.XP_TO_NEXT_LEVEL_STEP) {
        return Long.MAX_VALUE
    }
    return GameConfig.XP_TO_NEXT_LEVEL_BASE + GameConfig.XP_TO_NEXT_LEVEL_STEP * increments
}

/** Returns the player's level from their non-negative, ledger-derived XP total. */
fun level(xpTotal: Long): Long {
    require(xpTotal >= 0L) { "xpTotal must be non-negative" }

    var low = 1L
    var high = 2L
    while (hasReachedLevel(xpTotal, high)) {
        low = high
        high *= 2L
    }
    while (low + 1L < high) {
        val middle = low + (high - low) / 2L
        if (hasReachedLevel(xpTotal, middle)) low = middle else high = middle
    }
    return low
}

private fun hasReachedLevel(xpTotal: Long, targetLevel: Long): Boolean {
    val increments = targetLevel - 1L
    if (increments == 0L) return true
    if (increments > (Long.MAX_VALUE - 175L) / 25L) return false

    var left = increments
    var right = 175L + 25L * increments
    if (left % 2L == 0L) left /= 2L else right /= 2L
    return left == 0L || right <= xpTotal / left
}
