package dev.goose.daggerinterop

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
import dev.goose.runtime.GooseUi
import dev.goose.runtime.Navigator
import dev.goose.runtime.Screen
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.serialization.Serializable

@Serializable
data object InteropHomeScreen : Screen

data class InteropHomeState(val greeting: String = "") : MavericksState

/** A goose ViewModel whose repository comes from the INCLUDED Dagger component. */
@AssistedInject
class InteropHomeViewModel(
    @Assisted initialState: InteropHomeState,
    @Assisted private val navigator: Navigator,
    repository: GreetingRepository,
) : MavericksViewModel<InteropHomeState>(initialState) {

    init {
        setState { copy(greeting = repository.greeting) }
    }

    @AssistedFactory
    fun interface Factory {
        fun create(initialState: InteropHomeState, navigator: Navigator): InteropHomeViewModel
    }

    // No Mavericks factory companion: goose-compiler-plugin generates the nested GooseFactory.
}

@GooseUi(InteropHomeScreen::class)
@Composable
fun InteropHomeUi(state: InteropHomeState, viewModel: InteropHomeViewModel, modifier: Modifier) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Dagger interop", style = MaterialTheme.typography.headlineMedium)
        Text("Legacy repository says: ${state.greeting}")
    }
}
