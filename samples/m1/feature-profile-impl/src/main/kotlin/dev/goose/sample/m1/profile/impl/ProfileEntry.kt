package dev.goose.sample.m1.profile.impl

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
import androidx.compose.ui.unit.dp
import com.airbnb.mvrx.compose.collectAsState
import dev.goose.mavericks.screenViewModel
import dev.goose.runtime.ScreenEntry
import dev.goose.runtime.screenUi
import dev.goose.sample.m1.profile.api.ProfileScreen
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides

@ContributesTo(AppScope::class)
interface ProfileModule {
    companion object {
        @Provides
        @IntoMap
        @ClassKey(ProfileScreen::class)
        fun profileUi(vmFactory: ProfileViewModel.Factory): ScreenEntry =
            screenUi<ProfileScreen> { screen, modifier ->
                val viewModel = screenViewModel<ProfileViewModel, ProfileState>(screen, vmFactory::create)
                val state by viewModel.collectAsState()
                ProfileContent(
                    state = state,
                    onToggleFollow = viewModel::toggleFollow,
                    onAppendNote = viewModel::appendNote,
                    onDone = viewModel::done,
                    modifier = modifier,
                )
            }
    }
}

@Composable
private fun ProfileContent(
    state: ProfileState,
    onToggleFollow: () -> Unit,
    onAppendNote: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Profile: ${state.userId}", style = MaterialTheme.typography.headlineMedium)
        OutlinedButton(onClick = onToggleFollow) {
            Text(if (state.followed) "Following ✓" else "Follow")
        }
        Text("Notes (persisted): ${state.notes.ifEmpty { "—" }}")
        OutlinedButton(onClick = onAppendNote) { Text("Add a goose to notes") }
        Button(onClick = onDone) { Text("Done") }
    }
}
