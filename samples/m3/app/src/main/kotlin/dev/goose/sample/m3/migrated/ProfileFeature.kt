package dev.goose.sample.m3.migrated

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.airbnb.mvrx.MavericksState
import com.airbnb.mvrx.MavericksViewModel
import com.airbnb.mvrx.MavericksViewModelFactory
import com.airbnb.mvrx.compose.collectAsState
import com.airbnb.mvrx.compose.mavericksViewModel
import dev.goose.mavericks.MavericksVmCreator
import dev.goose.mavericks.findComponentActivity
import dev.goose.mavericks.gooseVmFactory
import dev.goose.mavericks.screenViewModel
import dev.goose.runtime.Navigator
import dev.goose.runtime.Screen
import dev.goose.runtime.ScreenEntry
import dev.goose.sample.m3.DetailScreen
import dev.goose.sample.m3.ProfileResult
import dev.goose.sample.m3.ProfileScreen
import dev.goose.sample.m3.legacy.CounterState
import dev.goose.sample.m3.legacy.CounterViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

data class M3ProfileState(
    val userId: String = "",
    val legacyAnswer: String? = null,
) : MavericksState {
    constructor(screen: ProfileScreen) : this(userId = screen.userId)
}

/** Fully migrated Goose VM — yet (during migration) it runs on the legacy fragment stack. */
@AssistedInject
class M3ProfileViewModel(
    @Assisted initialState: M3ProfileState,
    @Assisted private val navigator: Navigator,
) : MavericksViewModel<M3ProfileState>(initialState) {

    /** Compose VM awaiting a result from a LEGACY fragment screen — the migration money shot. */
    fun askLegacyDetail() {
        viewModelScope.launch {
            val result = navigator.goToForResult(DetailScreen("asked-by-compose"))
            setState { copy(legacyAnswer = result?.message ?: "no answer") }
        }
    }

    fun done(counterAtClose: Int) {
        navigator.pop(ProfileResult(counterAtClose))
    }

    @AssistedFactory
    fun interface Factory {
        fun create(initialState: M3ProfileState, navigator: Navigator): M3ProfileViewModel
    }

    companion object : MavericksViewModelFactory<M3ProfileViewModel, M3ProfileState> by gooseVmFactory(M3ProfileViewModel::class)
}

@ContributesIntoMap(AppScope::class)
@ClassKey(ProfileScreen::class)
@Inject
class ProfileEntry : ScreenEntry {
    @Composable
    override fun Content(screen: Screen, modifier: Modifier) {
        val viewModel = screenViewModel<M3ProfileViewModel, M3ProfileState>(screen)
        val state by viewModel.collectAsState()

        // The SAME CounterViewModel instance the legacy HomeFragment uses (activity-scoped).
        val activity = checkNotNull(LocalContext.current.findComponentActivity())
        val counterViewModel = mavericksViewModel<CounterViewModel, CounterState>(scope = activity)
        val counterState by counterViewModel.collectAsState()

        Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Migrated Profile (compose)", style = MaterialTheme.typography.headlineMedium)
            Text("User: ${state.userId}")
            Text("Shared counter from legacy home: ${counterState.count}")
            Button(onClick = { counterViewModel.increment() }) { Text("+1 (same VM as fragment)") }
            OutlinedButton(onClick = viewModel::askLegacyDetail) { Text("Ask legacy detail for a result") }
            state.legacyAnswer?.let { Text("Legacy answered: $it") }
            Button(onClick = { viewModel.done(counterState.count) }) { Text("Done") }
        }
    }
}

@ContributesTo(AppScope::class)
interface ProfileVmModule {
    companion object {
        @Provides
        @IntoMap
        @ClassKey(M3ProfileViewModel::class)
        fun profileVmCreator(factory: M3ProfileViewModel.Factory): MavericksVmCreator =
            MavericksVmCreator { state, _, navigator ->
                factory.create(state as M3ProfileState, navigator)
            }
    }
}
