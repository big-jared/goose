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
import dev.goose.metro.screenSerializers
import dev.goose.runtime.ScreenEntry
import dev.goose.runtime.ScreenUi
import dev.goose.sample.m1.home.api.HomeScreen
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.binding
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.subclass

@ContributesIntoMap(AppScope::class, binding = binding<ScreenEntry>())
@ClassKey(HomeScreen::class)
@Inject
class HomeUi(
    private val vmFactory: HomeViewModel.Factory,
) : ScreenUi<HomeScreen>() {
    @Composable
    override fun Content(screen: HomeScreen, modifier: Modifier) {
        val viewModel = screenViewModel<HomeViewModel, HomeState>(screen) { state, navigator ->
            vmFactory.create(state, navigator)
        }
        val state by viewModel.collectAsState()
        HomeContent(state, onUserClicked = viewModel::onUserClicked, modifier = modifier)
    }
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

@ContributesTo(AppScope::class)
interface HomeModule {
    companion object {
        @Provides
        @IntoSet
        fun homeSerializers(): SerializersModule = screenSerializers {
            subclass(HomeScreen::class)
        }
    }
}
