package com.habitat.app.domain.logic

import com.habitat.app.domain.model.CompletionState

enum class WidgetRowActionState {
    SHOW_BUTTONS,
    SHOW_DONE,
    SHOW_MINIMUM,
    SHOW_SKIPPED
}

/** Decides whether a widget row displays completion controls or its existing state. */
fun rowActions(completionState: CompletionState?): WidgetRowActionState =
    when (completionState) {
        null -> WidgetRowActionState.SHOW_BUTTONS
        CompletionState.DONE -> WidgetRowActionState.SHOW_DONE
        CompletionState.MINIMUM -> WidgetRowActionState.SHOW_MINIMUM
        CompletionState.SKIPPED -> WidgetRowActionState.SHOW_SKIPPED
    }
