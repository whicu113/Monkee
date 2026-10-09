package com.habitat.app.domain

import app.cash.turbine.test
import com.habitat.app.data.FakeCompletionDao
import com.habitat.app.data.repository.CompletionRepositoryImpl
import com.habitat.app.domain.model.CompletionState
import com.habitat.app.domain.usecase.LogCompletionUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class LogCompletionUseCaseTest {

    private lateinit var repository: CompletionRepositoryImpl
    private lateinit var useCase: LogCompletionUseCase

    private val fixedToday = LocalDate.of(2025, 5, 10)
    private val clock = object : AppClock {
        override fun instant() = Instant.parse("2025-05-10T12:00:00Z")
        override fun zoneId() = ZoneId.of("UTC")
    }

    @Before
    fun setUp() {
        repository = CompletionRepositoryImpl(FakeCompletionDao())
        useCase = LogCompletionUseCase(repository, clock)
    }

    @Test
    fun `executeLog logs completion for today and past 7 days`() = runTest {
        val successToday = useCase.executeLog("h1", CompletionState.DONE, fixedToday)
        val successPast7 = useCase.executeLog("h1", CompletionState.MINIMUM, fixedToday.minusDays(7))

        assertTrue(successToday)
        assertTrue(successPast7)

        repository.getCompletionsForHabit("h1").test {
            val list = awaitItem()
            assertEquals(2, list.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `executeLog blocks future dates and dates older than 7 days`() = runTest {
        val futureSuccess = useCase.executeLog("h1", CompletionState.DONE, fixedToday.plusDays(1))
        val oldSuccess = useCase.executeLog("h1", CompletionState.DONE, fixedToday.minusDays(8))

        assertFalse(futureSuccess)
        assertFalse(oldSuccess)

        repository.getCompletionsForHabit("h1").test {
            val list = awaitItem()
            assertEquals(0, list.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `executeRemove deletes completion`() = runTest {
        useCase.executeLog("h1", CompletionState.DONE, fixedToday)
        useCase.executeRemove("h1", fixedToday)

        repository.getCompletionsForHabit("h1").test {
            val list = awaitItem()
            assertEquals(0, list.size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
