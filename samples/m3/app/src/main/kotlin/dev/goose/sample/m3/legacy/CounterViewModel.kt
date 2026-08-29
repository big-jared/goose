package dev.goose.sample.m3.legacy

import com.airbnb.mvrx.MavericksState
import com.airbnb.mvrx.MavericksViewModel

data class CounterState(val count: Int = 0) : MavericksState

/**
 * The shared-VM proof: this exact file backs BOTH the legacy [HomeFragment] (via
 * `activityViewModel()`) and the migrated compose Profile screen (via `mavericksViewModel` scoped
 * to the activity). One state machine, two view technologies, zero changes during migration.
 */
class CounterViewModel(initialState: CounterState) : MavericksViewModel<CounterState>(initialState) {
    fun increment() = setState { copy(count = count + 1) }
}
