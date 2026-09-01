package dev.goose.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.fragment.app.Fragment
import dev.goose.metro.GooseCompositionLocals
import dev.goose.metro.GooseContent
import dev.goose.metro.GooseRuntimeAccessors
import dev.goose.metro.GooseScope
import dev.goose.metro.GooseGraphHolder
import dev.goose.metro.gooseGraph
import dev.goose.runtime.GooseDecoration
import dev.goose.runtime.Navigator
import dev.goose.runtime.Screen

/**
 * Implemented by an activity (or parent fragment) whose FragmentManager owns the stack during
 * migration; hands migrated screens the [FragmentNavigator] to inject into their ViewModels.
 */
interface FragmentNavigatorOwner {
    val gooseNavigator: Navigator
}

/**
 * Carries a child dependency scope ACROSS FragmentManager navigation. A fragment (or activity)
 * that owns a scoped flow implements this; every [ScreenFragment] pushed under it (its child
 * FragmentManager, or deeper) finds the NEAREST owner walking up the parent chain and renders
 * its compose content inside `GooseScope(gooseScopeGraph)` — so a scope-registered screen
 * resolves the flow's child graph after crossing into a fragment host, app-scoped screens keep
 * their parent fallback, and nested FragmentManagers resolve the nearest owning graph.
 *
 * Retain the graph with `retainedGraph(this)` (the fragment's own ViewModelStore): it then
 * survives configuration changes and is disposed exactly once when the owning fragment is
 * destroyed for real. The graph is resolved live at each screen's view creation and is never
 * serialized; process death rebuilds it like every other dependency.
 */
interface GooseScopeOwner {
    val gooseScopeGraph: Any
}

/**
 * Direction 1 of the migration: a fully-migrated compose screen hosted on the LEGACY fragment
 * back stack. The screen's Ui and ViewModel are already pure Goose; only the host is a fragment.
 * Once every screen in a flow is migrated, the flow flips to a NavigableGooseContent and this class
 * stops being used — no screen code changes.
 */
class ScreenFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val screen = ScreenBundler.fromBundle(requireArguments())
        val graph = (requireActivity().application as GooseGraphHolder).gooseGraph
        val navigator = findNavigator()
        val scopeGraph = findScopeGraph()
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                GooseCompositionLocals(graph) {
                    // This ComposeView is a fresh composition root, OUTSIDE the app shell's
                    // theme — apply the graph's contributed decorations (theme, providers)
                    // that a Compose-hosted screen would inherit from the shell.
                    val accessors = gooseGraph<GooseRuntimeAccessors>()
                    val decorations = remember(accessors) { accessors.gooseDecorations.toList() }
                    Decorated(decorations) {
                        if (scopeGraph != null) {
                            GooseScope(scopeGraph) {
                                GooseContent(screen, navigator, Modifier.fillMaxSize())
                            }
                        } else {
                            GooseContent(screen, navigator, Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }

    /** Nests each decoration around the next; empty renders [content] bare. */
    @Composable
    private fun Decorated(decorations: List<GooseDecoration>, content: @Composable () -> Unit) {
        if (decorations.isEmpty()) {
            content()
        } else {
            decorations.first().Decorate {
                Decorated(decorations.subList(1, decorations.size), content)
            }
        }
    }

    /** Nearest [GooseScopeOwner] up the parent chain, then the activity; null means app scope. */
    private fun findScopeGraph(): Any? {
        var parent = parentFragment
        while (parent != null) {
            if (parent is GooseScopeOwner) return parent.gooseScopeGraph
            parent = parent.parentFragment
        }
        return (activity as? GooseScopeOwner)?.gooseScopeGraph
    }

    /**
     * Nearest wins: a parent fragment implementing [FragmentNavigatorOwner] (nested legacy
     * stacks), then the activity's navigator (a [FragmentNavigatorOwner] implementation or the
     * one retained by installGooseNavigator).
     */
    private fun findNavigator(): Navigator {
        var parent = parentFragment
        while (parent != null) {
            if (parent is FragmentNavigatorOwner) return parent.gooseNavigator
            parent = parent.parentFragment
        }
        return requireActivity().gooseNavigator
    }

    companion object {
        fun newInstance(screen: Screen): ScreenFragment = ScreenFragment().apply {
            arguments = ScreenBundler.toBundle(screen)
        }

        /**
         * Creation through the host's [androidx.fragment.app.FragmentFactory], so a host that
         * installs a custom factory (constructor-injected fragments, test doubles) sees goose's
         * fragments go through the same path as its own.
         */
        fun newInstance(fragmentManager: androidx.fragment.app.FragmentManager, screen: Screen): ScreenFragment {
            val fragment = fragmentManager.fragmentFactory.instantiate(
                ScreenFragment::class.java.classLoader!!,
                ScreenFragment::class.java.name,
            ) as ScreenFragment
            fragment.arguments = ScreenBundler.toBundle(screen)
            return fragment
        }
    }
}
