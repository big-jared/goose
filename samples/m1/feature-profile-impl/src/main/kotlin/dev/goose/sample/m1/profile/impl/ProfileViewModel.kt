package dev.goose.sample.m1.profile.impl

import com.airbnb.mvrx.MavericksState
import com.airbnb.mvrx.MavericksViewModel
import com.airbnb.mvrx.MavericksViewModelFactory
import com.airbnb.mvrx.PersistState
import dev.goose.mavericks.gooseVmFactory
import dev.goose.runtime.Navigator
import dev.goose.sample.m1.profile.api.ProfileResult
import dev.goose.sample.m1.profile.api.ProfileScreen
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.launch

data class ProfileState(
    val userId: String = "",
    val followed: Boolean = false,
    /** Survives process death via Mavericks' own @PersistState machinery — the M1 proof point. */
    @PersistState val notes: String = "",
) : MavericksState {
    /** Mavericks' fragment-args convention, verbatim: initial state from the screen. */
    constructor(screen: ProfileScreen) : this(userId = screen.userId)
}

@AssistedInject
class ProfileViewModel(
    @Assisted initialState: ProfileState,
    @Assisted private val navigator: Navigator,
) : MavericksViewModel<ProfileState>(initialState) {

    fun toggleFollow() = setState { copy(followed = !followed) }

    fun appendNote() = setState { copy(notes = notes + "🪿") }

    fun done() {
        // Navigate from a Main-dispatched coroutine, not withState: Mavericks runs withState
        // blocks on its background state-store thread, and back stacks are main-thread state.
        viewModelScope.launch {
            navigator.pop(ProfileResult(followed = awaitState().followed))
        }
    }

    @AssistedFactory
    fun interface Factory {
        fun create(initialState: ProfileState, navigator: Navigator): ProfileViewModel
    }

    companion object : MavericksViewModelFactory<ProfileViewModel, ProfileState> by gooseVmFactory(ProfileViewModel::class)
}
