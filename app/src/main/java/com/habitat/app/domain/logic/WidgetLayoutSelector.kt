package com.habitat.app.domain.logic

/** Widget size expressed in dp so layout selection stays platform-independent and testable. */
data class WidgetSizeDp(val width: Float, val height: Float)

enum class WidgetLayout {
    COMPACT_LIST,
    STANDARD_LIST
}

/** Selects a layout for the current widget size. */
object WidgetLayoutSelector {
    const val STANDARD_WIDTH_DP = 250f
    const val STANDARD_HEIGHT_DP = 380f

    fun choose(size: WidgetSizeDp): WidgetLayout =
        if (size.width >= STANDARD_WIDTH_DP && size.height >= STANDARD_HEIGHT_DP) {
            WidgetLayout.STANDARD_LIST
        } else {
            WidgetLayout.COMPACT_LIST
        }
}
