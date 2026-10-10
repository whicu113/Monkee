package com.habitat.app.ui.reward

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RewardMessageResourcesTest {

    private enum class ResourceKind { STRING, PLURALS }

    private data class Row(
        val name: String,
        val resourceName: String,
        val kind: ResourceKind,
        val quantity: Int? = null,
        val arguments: List<Any> = emptyList(),
        val expected: String
    )

    @Test
    fun rewardCopyResourcesMatchEachExpectedRow() {
        val rows = listOf(
            Row(
                name = "Seeds plural quantity one",
                resourceName = "reward_seeds_earned",
                kind = ResourceKind.PLURALS,
                quantity = 1,
                arguments = listOf(1),
                expected = "+1 Seed"
            ),
            Row(
                name = "Seeds plural quantity eleven",
                resourceName = "reward_seeds_earned",
                kind = ResourceKind.PLURALS,
                quantity = 11,
                arguments = listOf(11),
                expected = "+11 Seeds"
            ),
            Row(
                name = "XP earned copy",
                resourceName = "reward_xp_earned",
                kind = ResourceKind.STRING,
                arguments = listOf(10),
                expected = "+10 XP"
            ),
            Row(
                name = "comeback copy",
                resourceName = "reward_comeback",
                kind = ResourceKind.STRING,
                expected = "Welcome back. Your comeback bonus is included."
            ),
            Row(
                name = "daily Seeds cap copy",
                resourceName = "reward_daily_seeds_cap",
                kind = ResourceKind.STRING,
                expected = "You've earned today's maximum Seeds."
            ),
            Row(
                name = "drop found copy",
                resourceName = "reward_drop_found",
                kind = ResourceKind.STRING,
                expected = "You found something!"
            )
        )

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val failures = mutableListOf<String>()

        rows.forEach { row ->
            val resourceType = when (row.kind) {
                ResourceKind.STRING -> "string"
                ResourceKind.PLURALS -> "plurals"
            }
            val resourceId = context.resources.getIdentifier(
                row.resourceName,
                resourceType,
                context.packageName
            )

            if (resourceId == 0) {
                failures += "${row.name}: missing $resourceType resource '${row.resourceName}'"
            } else {
                val actual = when (row.kind) {
                    ResourceKind.STRING -> context.resources.getString(
                        resourceId,
                        *row.arguments.toTypedArray()
                    )
                    ResourceKind.PLURALS -> context.resources.getQuantityString(
                        resourceId,
                        requireNotNull(row.quantity),
                        *row.arguments.toTypedArray()
                    )
                }
                if (actual != row.expected) {
                    failures += "${row.name}: expected:<${row.expected}> but was:<$actual>"
                }
            }
        }

        assertEquals("all reward copy resource rows", emptyList<String>(), failures)
    }
}
