package dev.goose.sample.m4

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.airbnb.mvrx.MavericksState
import com.airbnb.mvrx.MavericksViewModel
import com.airbnb.mvrx.MavericksViewModelFactory
import dev.goose.mavericks.gooseVmFactory
import dev.goose.runtime.GooseUi
import dev.goose.runtime.Navigator
import dev.goose.runtime.Screen
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.serialization.Serializable

@Serializable
data object M4HomeScreen : Screen

data class M4HomeState(val greeting: String = "") : MavericksState

/** A goose ViewModel whose repository comes from the INCLUDED Dagger component. */
@AssistedInject
class M4HomeViewModel(
    @Assisted initialState: M4HomeState,
    @Assisted private val navigator: Navigator,
    repository: GreetingRepository,
) : MavericksViewModel<M4HomeState>(initialState) {

    init {
        setState { copy(greeting = repository.greeting) }
    }

    @AssistedFactory
    fun interface Factory {
        fun create(initialState: M4HomeState, navigator: Navigator): M4HomeViewModel
    }

    companion object : MavericksViewModelFactory<M4HomeViewModel, M4HomeState> by gooseVmFactory(M4HomeViewModel::class)
}

@GooseUi(M4HomeScreen::class)
@Composable
fun M4HomeUi(state: M4HomeState, viewModel: M4HomeViewModel, modifier: Modifier) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Dagger interop", style = MaterialTheme.typography.headlineMedium)
        Text("Legacy repository says: ${state.greeting}")
    }
}
