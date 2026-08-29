package dev.goose.sample.m1.profile.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.airbnb.mvrx.compose.collectAsState
import dev.goose.mavericks.MavericksVmCreator
import dev.goose.mavericks.screenViewModel
import dev.goose.runtime.Screen
import dev.goose.runtime.ScreenEntry
import dev.goose.sample.m1.profile.api.ProfileScreen
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@ContributesIntoMap(AppScope::class)
@ClassKey(ProfileScreen::class)
@Inject
class ProfileEntry : ScreenEntry {
    @Composable
    override fun Content(screen: Screen, modifier: Modifier) {
        val viewModel = screenViewModel<ProfileViewModel, ProfileState>(screen)
        val state by viewModel.collectAsState()
        ProfileUi(
            state = state,
            onToggleFollow = viewModel::toggleFollow,
            onNotesChanged = viewModel::onNotesChanged,
            onDone = viewModel::done,
            modifier = modifier,
        )
    }
}

@Composable
private fun ProfileUi(
    state: ProfileState,
    onToggleFollow: () -> Unit,
    onNotesChanged: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Profile: ${state.userId}", style = MaterialTheme.typography.headlineMedium)
        OutlinedButton(onClick = onToggleFollow) {
            Text(if (state.followed) "Following ✓" else "Follow")
        }
        OutlinedTextField(
            value = state.notes,
            onValueChange = onNotesChanged,
            label = { Text("Notes (persisted)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onDone) { Text("Done") }
    }
}

@ContributesTo(AppScope::class)
interface ProfileModule {
    companion object {
        @Provides
        @IntoMap
        @ClassKey(ProfileViewModel::class)
        fun profileVmCreator(factory: ProfileViewModel.Factory): MavericksVmCreator =
            MavericksVmCreator { state, _, navigator ->
                factory.create(state as ProfileState, navigator)
            }

        @Provides
        @IntoSet
        fun profileSerializers(): SerializersModule = SerializersModule {
            polymorphic(NavKey::class) { subclass(ProfileScreen::class) }
        }
    }
}
