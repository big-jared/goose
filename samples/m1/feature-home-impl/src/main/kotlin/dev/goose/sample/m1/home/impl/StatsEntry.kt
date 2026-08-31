package dev.goose.sample.m1.home.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.goose.runtime.GooseUi
import dev.goose.runtime.Navigator
import dev.goose.runtime.StateHolder
import dev.goose.runtime.rememberStateHolder
import dev.goose.sample.m1.home.api.TeamStatsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class TeamStatsState(
    val geeseSpotted: Int = 0,
    val honksHeard: Int = 0,
)

/**
 * The presenter-agnostic option: a plain StateHolder instead of a Mavericks ViewModel. Pure
 * Kotlin + coroutines, no Mavericks, same entry-scoped lifecycle (retained across rotation,
 * cleared on pop). Both styles coexist per screen; this one screen uses it, the rest of m1
 * stays Mavericks.
 */
class TeamStatsHolder(
    private val navigator: Navigator,
) : StateHolder<TeamStatsState>(TeamStatsState()) {

    fun spotGoose() = setState { copy(geeseSpotted = geeseSpotted + 1) }

    fun listenForHonk() {
        holderScope.launch {
            delay(50)
            setState { copy(honksHeard = honksHeard + 1) }
        }
    }

    fun done() {
        navigator.pop()
    }
}

@GooseUi(TeamStatsScreen::class)
@Composable
fun StatsUi(modifier: Modifier) {
    val holder = rememberStateHolder { navigator -> TeamStatsHolder(navigator) }
    val state by holder.state.collectAsState()
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Team stats", style = MaterialTheme.typography.headlineMedium)
        Text("Geese spotted: ${state.geeseSpotted}")
        Text("Honks heard: ${state.honksHeard}")
        OutlinedButton(onClick = holder::spotGoose) { Text("Spot a goose") }
        OutlinedButton(onClick = holder::listenForHonk) { Text("Listen for a honk") }
        Button(onClick = holder::done) { Text("Back to team") }
    }
}
