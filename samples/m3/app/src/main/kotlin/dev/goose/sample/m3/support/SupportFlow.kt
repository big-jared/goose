package dev.goose.sample.m3.support

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
import dev.goose.metro.GooseGraphHolder
import dev.goose.metro.GooseRuntimeAccessors
import dev.goose.metro.GooseScopeAccessors
import dev.goose.metro.retainedGraph
import dev.goose.runtime.GooseUi
import dev.goose.runtime.Navigator
import dev.goose.runtime.Screen
import dev.goose.sample.m3.R
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.Serializable

/** A support SESSION: one child graph per support flow, on the FRAGMENT side of the migration. */
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

/** Scope-registered screen: resolvable only under the support flow's GooseScope. */
@GooseUi(SupportChatScreen::class, scope = SupportScope::class)
@Composable
fun SupportChatUi(modifier: Modifier, session: SupportSession) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Support chat", style = MaterialTheme.typography.headlineMedium)
        Text("Ticket ${session.ticketId}")
    }
}

/**
 * A LEGACY-side scoped flow host: an unmigrated fragment that owns a child FragmentManager
 * stack AND a child dependency graph. Implementing [GooseScopeOwner] is what carries the scope
 * across the FragmentManager boundary: every ScreenFragment pushed on the child stack finds
 * this owner (nearest wins over any outer owner) and resolves [SupportScope] screens and
 * dependencies. The graph is retained in THIS fragment's ViewModelStore: it survives rotation
 * and is disposed exactly once when the flow fragment is removed for real.
 */
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
            containerId = R.id.goose_support_container,
            binders = emptyMap(),
            resultRouter = (appGraph as GooseRuntimeAccessors).resultRouter,
            stackTag = "support-flow",
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = FragmentContainerView(requireContext()).apply { id = R.id.goose_support_container }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (childFragmentManager.fragments.isEmpty()) {
            gooseNavigator.goTo(SupportChatScreen)
        }
    }
}
