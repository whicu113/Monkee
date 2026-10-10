package com.habitat.app.domain

import kotlinx.coroutines.flow.StateFlow

/** Monotonic refresh version observed by live widget compositions. */
interface WidgetRefreshSignal {
    val refreshGeneration: StateFlow<Long>
}
