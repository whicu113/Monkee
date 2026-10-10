package com.habitat.app.data.repository

import com.habitat.app.data.local.LedgerEntryDao
import com.habitat.app.domain.logic.RewardCurrency
import com.habitat.app.domain.repository.PlayerEconomyRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class RoomPlayerEconomyRepository @Inject constructor(
    private val ledgerEntryDao: LedgerEntryDao
) : PlayerEconomyRepository {
    override fun observeSeedsBalance(): Flow<Long> =
        ledgerEntryDao.observeBalanceForCurrency(RewardCurrency.SEEDS.name)

    override fun observeXpTotal(): Flow<Long> = ledgerEntryDao.observeTotalXp()
}
