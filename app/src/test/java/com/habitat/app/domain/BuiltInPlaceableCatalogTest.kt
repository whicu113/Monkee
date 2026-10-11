package com.habitat.app.domain

import com.habitat.app.domain.model.BuiltInPlaceableCatalog
import com.habitat.app.domain.model.FootprintOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInPlaceableCatalogTest {

    @Test
    fun builtInDefinitionsKeepStableIdsAndIncludeSingleAndMultiCellShapes() = rows(
        "small stone placeholder has stable single-cell definition" to {
            val definition = BuiltInPlaceableCatalog.definitions[BuiltInPlaceableCatalog.SMALL_STONE_ID]
            assertEquals("small stone placeholder has stable single-cell definition: id", "placeholder-small-stone", definition?.id)
            assertEquals(
                "small stone placeholder has stable single-cell definition: footprint",
                setOf(FootprintOffset(0, 0)),
                definition?.footprint
            )
        },
        "two-tile log placeholder has stable multi-cell definition" to {
            val definition = BuiltInPlaceableCatalog.definitions[BuiltInPlaceableCatalog.TWO_TILE_LOG_ID]
            assertEquals("two-tile log placeholder has stable multi-cell definition: id", "placeholder-two-tile-log", definition?.id)
            assertEquals(
                "two-tile log placeholder has stable multi-cell definition: footprint",
                setOf(FootprintOffset(0, 0), FootprintOffset(1, 0)),
                definition?.footprint
            )
        },
        "palette definitions are keyed by stable id" to {
            assertEquals(
                "palette definitions are keyed by stable id: keys",
                setOf(BuiltInPlaceableCatalog.SMALL_STONE_ID, BuiltInPlaceableCatalog.TWO_TILE_LOG_ID),
                BuiltInPlaceableCatalog.definitions.keys
            )
        }
    )

    private fun rows(vararg rows: Pair<String, () -> Unit>) {
        val failures = mutableListOf<String>()
        rows.forEach { (name, check) ->
            try {
                check()
            } catch (failure: AssertionError) {
                failures += "$name: ${failure.message}"
            } catch (failure: Throwable) {
                failures += "$name: unexpected ${failure::class.simpleName}: ${failure.message}"
            }
        }
        assertTrue("BuiltInPlaceableCatalog rows failed:\n${failures.joinToString("\n")}", failures.isEmpty())
    }
}
