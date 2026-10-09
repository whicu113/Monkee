package com.habitat.app.data

import app.cash.turbine.test
import com.habitat.app.data.local.CompletionDao
import com.habitat.app.data.local.CompletionEntity
import com.habitat.app.data.repository.CompletionRepositoryImpl
import com.habitat.app.domain.model.CompletionState
import com.habitat.app.domain.model.LoggedCompletion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class CompletionRepositoryTest {

    private lateinit var fakeDao: FakeCompletionDao
    private lateinit var repository: CompletionRepositoryImpl

    @Before
    fun setUp() {
        fakeDao = FakeCompletionDao()
        repository = CompletionRepositoryImpl(fakeDao)
    }

    @Test
    fun `logging completion enforces unique constraint on habitId and logicalDate`() = runTest {
        val date = LocalDate.of(2025, 5, 10)
        val completion1 = LoggedCompletion(habitId = "h1", logicalDate = date, state = CompletionState.MINIMUM)
        val completion2 = LoggedCompletion(habitId = "h1", logicalDate = date, state = CompletionState.DONE)

        repository.logCompletion(completion1)

        repository.getCompletionsForHabit("h1").test {
            val list1 = awaitItem()
            assertEquals(1, list1.size)
            assertEquals(CompletionState.MINIMUM, list1[0].state)

            // Upsert with new state for same habit and date
            repository.logCompletion(completion2)

            val list2 = awaitItem()
            assertEquals(1, list2.size)
            assertEquals(CompletionState.DONE, list2[0].state)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removing completion deletes entry (uncheck)`() = runTest {
        val date = LocalDate.of(2025, 5, 10)
        val completion = LoggedCompletion(habitId = "h1", logicalDate = date, state = CompletionState.DONE)

        repository.logCompletion(completion)
        repository.removeCompletion("h1", date)

        repository.getCompletionsForHabit("h1").test {
            val list = awaitItem()
            assertEquals(0, list.size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

class FakeCompletionDao : CompletionDao {
    private val completionsState = MutableStateFlow<Map<Pair<String, LocalDate>, CompletionEntity>>(emptyMap())

    override fun getCompletionsForHabit(habitId: String): Flow<List<CompletionEntity>> {
        return completionsState.map { map ->
            map.values.filter { it.habitId == habitId }.sortedBy { it.logicalDate }
        }
    }

    override fun getAllCompletions(): Flow<List<CompletionEntity>> {
        return completionsState.map { map ->
            map.values.sortedBy { it.logicalDate }
        }
    }

    override suspend fun insertOrUpdate(completion: CompletionEntity): Long {
        val key = Pair(completion.habitId, completion.logicalDate)
        completionsState.value = completionsState.value + (key to completion)
        return 1L
    }

    override suspend fun deleteCompletion(habitId: String, logicalDate: LocalDate): Int {
        val key = Pair(habitId, logicalDate)
        if (completionsState.value.containsKey(key)) {
            completionsState.value = completionsState.value - key
            return 1
        }
        return 0
    }

    override suspend fun getCompletion(habitId: String, logicalDate: LocalDate): CompletionEntity? {
        return completionsState.value[Pair(habitId, logicalDate)]
    }
}
