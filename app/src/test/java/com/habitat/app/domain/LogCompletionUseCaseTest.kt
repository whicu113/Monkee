package com.habitat.app.domain

import app.cash.turbine.test
import com.habitat.app.data.FakeCompletionDao
import com.habitat.app.data.repository.CompletionRepositoryImpl
import com.habitat.app.domain.WidgetUpdater
import com.habitat.app.domain.model.CompletionState
import com.habitat.app.domain.usecase.CompletionReminderHandler
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
    private lateinit var fakeHandler: FakeCompletionReminderHandler
    private lateinit var useCase: LogCompletionUseCase

    private val fixedToday = LocalDate.of(2025, 5, 10)
    private val clock = object : AppClock {
        override fun instant() = Instant.parse("2025-05-10T12:00:00Z")
        override fun zoneId() = ZoneId.of("UTC")
    }

    @Before
    fun setUp() {
        repository = CompletionRepositoryImpl(FakeCompletionDao())
        fakeHandler = FakeCompletionReminderHandler()
        useCase = LogCompletionUseCase(repository, clock, fakeHandler)
    }

    @Test
    fun `executeLog logs completion for today and past 7 days and invokes reminder handler`() = runTest {
        val successToday = useCase.executeLog("h1", CompletionState.DONE, fixedToday)
        val successPast7 = useCase.executeLog("h1", CompletionState.MINIMUM, fixedToday.minusDays(7))

        assertTrue(successToday)
        assertTrue(successPast7)

        repository.getCompletionsForHabit("h1").test {
            val list = awaitItem()
            assertEquals(2, list.size)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(2, fakeHandler.loggedCalls.size)
        assertEquals("h1" to fixedToday, fakeHandler.loggedCalls[0])
        assertEquals("h1" to fixedToday.minusDays(7), fakeHandler.loggedCalls[1])
    }

    @Test
    fun `executeLog duplicate tap on same date results in one row, no error, and does not invoke handler twice`() = runTest {
        val success1 = useCase.executeLog("h1", CompletionState.DONE, fixedToday)
        assertTrue(success1)
        assertEquals(1, fakeHandler.loggedCalls.size)

        // Duplicate tap: same habit, date, and state
        val success2 = useCase.executeLog("h1", CompletionState.DONE, fixedToday)
        assertTrue(success2)

        // Handler is NOT invoked a second time
        assertEquals(1, fakeHandler.loggedCalls.size)

        repository.getCompletionsForHabit("h1").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `executeLog blocks future dates and dates older than 7 days without invoking handler`() = runTest {
        val futureSuccess = useCase.executeLog("h1", CompletionState.DONE, fixedToday.plusDays(1))
        val oldSuccess = useCase.executeLog("h1", CompletionState.DONE, fixedToday.minusDays(8))

        assertFalse(futureSuccess)
        assertFalse(oldSuccess)
        assertEquals(0, fakeHandler.loggedCalls.size)

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

    @Test
    fun `executeRemove completes when widget updater throws for the row`() = runTest {
        useCase = LogCompletionUseCase(
            repository,
            clock,
            fakeHandler,
            WidgetUpdater { throw IllegalStateException("widget provider unavailable") }
        )
        useCase.executeLog("h1", CompletionState.DONE, fixedToday)

        useCase.executeRemove("h1", fixedToday)

        assertEquals("row 'h1' completion was removed", null, repository.getCompletion("h1", fixedToday))
    }
}

class FakeCompletionReminderHandler : CompletionReminderHandler {
    val loggedCalls = mutableListOf<Pair<String, LocalDate>>()

    override suspend fun onCompletionLogged(habitId: String, logicalDate: LocalDate) {
        loggedCalls.add(habitId to logicalDate)
    }
}
