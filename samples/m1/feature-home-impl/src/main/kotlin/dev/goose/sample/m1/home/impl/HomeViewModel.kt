package dev.goose.sample.m1.home.impl

import com.airbnb.mvrx.Async
import com.airbnb.mvrx.MavericksState
import com.airbnb.mvrx.MavericksViewModel
import com.airbnb.mvrx.MavericksViewModelFactory
import com.airbnb.mvrx.Uninitialized
import dev.goose.mavericks.gooseVmFactory
import dev.goose.runtime.Navigator
import dev.goose.sample.m1.profile.api.ProfileScreen
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.launch

data class HomeState(
    val users: Async<List<String>> = Uninitialized,
    val lastVisited: String? = null,
    val lastFollowed: Boolean? = null,
) : MavericksState

@AssistedInject
class HomeViewModel(
    @Assisted initialState: HomeState,
    @Assisted private val navigator: Navigator,
    private val repository: UserRepository,
) : MavericksViewModel<HomeState>(initialState) {

    init {
        suspend { repository.loadUsers() }.execute { copy(users = it) }
    }

    fun onUserClicked(userId: String) {
        viewModelScope.launch {
            val result = navigator.goToForResult(ProfileScreen(userId))
            setState { copy(lastVisited = userId, lastFollowed = result?.followed) }
        }
    }

    @AssistedFactory
    fun interface Factory {
        fun create(initialState: HomeState, navigator: Navigator): HomeViewModel
    }

    companion object : MavericksViewModelFactory<HomeViewModel, HomeState> by gooseVmFactory(HomeViewModel::class)
}
