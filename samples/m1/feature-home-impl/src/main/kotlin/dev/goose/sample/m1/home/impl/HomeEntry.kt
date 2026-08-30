package dev.goose.sample.m1.home.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.airbnb.mvrx.Loading
import com.airbnb.mvrx.Success
import com.airbnb.mvrx.Uninitialized
import com.airbnb.mvrx.compose.collectAsState
import dev.goose.mavericks.screenViewModel
import dev.goose.runtime.GooseUi
import dev.goose.sample.m1.home.api.HomeScreen

/**
 * The entire registration is this one annotation: goose-compiler generates the Metro
 * contribution keyed by HomeScreen, injecting vmFactory from the graph.
 */
@GooseUi(HomeScreen::class)
@Composable
fun HomeUi(screen: HomeScreen, modifier: Modifier, vmFactory: HomeViewModel.Factory) {
    val viewModel = screenViewModel<HomeViewModel, HomeState>(screen, vmFactory::create)
    val state by viewModel.collectAsState()
    HomeContent(state, onUserClicked = viewModel::onUserClicked, modifier = modifier)
}

@Composable
private fun HomeContent(
    state: HomeState,
    onUserClicked: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Team", style = MaterialTheme.typography.headlineMedium)
        if (state.lastVisited != null) {
            Text(
                "Last visited: ${state.lastVisited}" +
                    (state.lastFollowed?.let { if (it) " (followed!)" else " (not followed)" } ?: " (no answer)"),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        when (val users = state.users) {
            is Uninitialized, is Loading -> CircularProgressIndicator()
            is Success -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(users()) { user ->
                    Button(onClick = { onUserClicked(user) }, modifier = Modifier.fillMaxWidth()) {
                        Text(user)
                    }
                }
            }
            else -> Text("Failed to load users")
        }
    }
}
