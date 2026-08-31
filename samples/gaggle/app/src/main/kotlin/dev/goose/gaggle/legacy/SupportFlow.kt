package dev.goose.gaggle.legacy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import dev.goose.fragment.FragmentNavigator
import dev.goose.fragment.FragmentNavigatorOwner
import dev.goose.fragment.GooseScopeOwner
import dev.goose.fragment.fragmentScreenEntry
import dev.goose.gaggle.R
import dev.goose.gaggle.auth.api.SupportFlowScreen
import dev.goose.metro.GooseGraphHolder
import dev.goose.metro.GooseRuntimeAccessors
import dev.goose.metro.GooseScopeAccessors
import dev.goose.metro.retainedGraph
import dev.goose.runtime.GooseUi
import dev.goose.runtime.Navigator
import dev.goose.runtime.Screen
import dev.goose.runtime.ScreenEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.Serializable

/**
 * Demonstrates: a child dependency scope crossing a FragmentManager boundary. The support flow
 * is a LEGACY fragment owning a child FragmentManager stack AND a SupportScope graph
 * (GooseScopeOwner); the compose screen it pushes resolves the session from that graph, and
 * the graph is retained across rotation and disposed when the flow fragment is removed.
 */
abstract class SupportScope private constructor()

@SingleIn(SupportScope::class)
@Inject
class SupportSession {
    val ticketId: String = "T-42"
}

@ContributesTo(SupportScope::class)
interface SupportSessionAccessor {
    val session: SupportSession
}

@GraphExtension(SupportScope::class)
interface SupportGraph : GooseScopeAccessors {
    @GraphExtension.Factory
    @ContributesTo(AppScope::class)
    interface Factory {
        fun createSupportGraph(): SupportGraph
    }
}

@Serializable
data object SupportChatScreen : Screen

data class SupportChatState(val messagesSent: Int = 0) : com.airbnb.mvrx.MavericksState

/**
 * Demonstrates: the screen-scoped ViewModel contract ON THE FRAGMENT HOST. This VM's screen
 * rides a FragmentManager back stack (inside SupportFlowFragment's child FM), yet retention
 * across rotation and clearing on pop behave exactly as on a Nav3 host; the hardening tests
 * pin both. The session dependency comes from the SupportScope graph across the FM boundary.
 */
@dev.zacsweers.metro.AssistedInject
class SupportChatViewModel(
    @dev.zacsweers.metro.Assisted initialState: SupportChatState,
    @dev.zacsweers.metro.Assisted private val navigator: Navigator,
) : com.airbnb.mvrx.MavericksViewModel<SupportChatState>(initialState) {

    fun sendHonk() = setState { copy(messagesSent = messagesSent + 1) }

    @dev.zacsweers.metro.AssistedFactory
    fun interface Factory {
        fun create(initialState: SupportChatState, navigator: Navigator): SupportChatViewModel
    }

    companion object : com.airbnb.mvrx.MavericksViewModelFactory<SupportChatViewModel, SupportChatState>
    by dev.goose.mavericks.gooseVmFactory(SupportChatViewModel::class)
}

@GooseUi(SupportChatScreen::class, scope = SupportScope::class)
@Composable
fun SupportChatUi(
    state: SupportChatState,
    viewModel: SupportChatViewModel,
    modifier: Modifier,
    session: SupportSession,
) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Support chat", style = MaterialTheme.typography.headlineMedium)
        Text("Ticket ${session.ticketId}")
        Text("Honks sent: ${state.messagesSent}")
        androidx.compose.material3.OutlinedButton(onClick = viewModel::sendHonk) { Text("Send a honk") }
    }
}

@ContributesTo(AppScope::class)
interface SupportEntriesModule {
    companion object {
        @Provides
        @IntoMap
        @ClassKey(SupportFlowScreen::class)
        fun supportFlowEntry(): ScreenEntry =
            fragmentScreenEntry<SupportFlowFragment, SupportFlowScreen>()
    }
}

class SupportFlowFragment : Fragment(), GooseScopeOwner, FragmentNavigatorOwner {

    override val gooseScopeGraph: Any
        get() = retainedGraph(this) {
            val appGraph = (requireActivity().application as GooseGraphHolder).gooseGraph
            (appGraph as SupportGraph.Factory).createSupportGraph()
        }

    override val gooseNavigator: Navigator by lazy {
        val appGraph = (requireActivity().application as GooseGraphHolder).gooseGraph
        FragmentNavigator(
            fragmentManager = childFragmentManager,
            containerId = R.id.gaggle_support_container,
            binders = emptyMap(),
            resultRouter = (appGraph as GooseRuntimeAccessors).resultRouter,
            stackTag = "support-flow",
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = FragmentContainerView(requireContext()).apply { id = R.id.gaggle_support_container }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (childFragmentManager.fragments.isEmpty()) {
            gooseNavigator.goTo(SupportChatScreen)
        }
    }
}
