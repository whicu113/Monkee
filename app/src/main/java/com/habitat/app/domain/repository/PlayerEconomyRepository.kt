package com.habitat.app.domain.repository

import kotlinx.coroutines.flow.Flow

/** Read-only, ledger-derived player economy totals. */
interface PlayerEconomyRepository {
    fun observeSeedsBalance(): Flow<Long>
    fun observeXpTotal(): Flow<Long>
}
