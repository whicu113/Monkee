package com.habitat.app.domain.model

import java.util.UUID

/**
 * Domain representation of an optional habit group.
 * SPEC Section 4.1 & 9.
 */
data class HabitGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val sortOrder: Int = 0
) {
    init {
        require(name.isNotBlank()) { "Habit group name cannot be blank" }
    }
}
