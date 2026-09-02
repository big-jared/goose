package dev.goose.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import dev.goose.metro.GooseCompositionLocals
import dev.goose.metro.GooseContent
import dev.goose.metro.GooseScope
import dev.goose.metro.GooseGraphHolder
import dev.goose.runtime.Navigator
import dev.goose.runtime.Screen
import kotlin.reflect.KClass

/**
 * Implemented by an activity (or parent fragment) whose FragmentManager owns the stack during
 * migration; hands migrated screens the [FragmentNavigator] to inject into their ViewModels.
 */
interface FragmentNavigatorOwner {
    val gooseNavigator: Navigator
}

/**
 * Carries a child dependency scope ACROSS FragmentManager navigation. A fragment (or activity)
 * that owns a scoped flow implements this; every hosted screen pushed under it finds the
 * NEAREST owner walking up the parent chain and renders its compose content inside
 * `GooseScope(gooseScopeGraph)`. Retain the graph with `retainedGraph(this)` so it survives
 * configuration changes and is disposed exactly once when the owning fragment is destroyed.
 */
interface GooseScopeOwner {
    val gooseScopeGraph: Any
}

/**
 * Builds the fully wired view for a goose screen hosted in a fragment: the screen from
 * [Fragment.getArguments], the nearest navigator and scope from the parent chain, rendered
 * through the registry. This is [ScreenFragment]'s entire implementation, public so a custom
 * host from YOUR base class (its lifecycle, its analytics) is one override:
 * ```
 * class GaggleScreenFragment : GaggleFragment() {
 *     override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
 *         gooseScreenView { content -> AppTheme { content() } }
 * }
 * ```
 * Register it once at install: `installGooseNavigator(containerId, screenHost = GaggleScreenFragment::class)`.
 * [wrap] composes around the screen content (theme, CompositionLocals, chrome); the default
 * renders it bare.
 */
fun Fragment.gooseScreenView(
    wrap: @Composable (content: @Composable () -> Unit) -> Unit = { it() },
): View {
    val screen = ScreenBundler.fromBundle(requireArguments())
    val graph = (requireActivity().application as GooseGraphHolder).gooseGraph
    val navigator = findGooseNavigator()
    val scopeGraph = findGooseScopeGraph()
    return ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            GooseCompositionLocals(graph) {
                wrap {
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

/** Nearest [GooseScopeOwner] up the parent chain, then the activity; null means app scope. */
private fun Fragment.findGooseScopeGraph(): Any? {
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
private fun Fragment.findGooseNavigator(): Navigator {
    var parent = parentFragment
    while (parent != null) {
        if (parent is FragmentNavigatorOwner) return parent.gooseNavigator
        parent = parent.parentFragment
    }
    return requireActivity().gooseNavigator
}

/**
 * Sets the arguments a goose host fragment reads in [gooseScreenView]. For embedding a custom
 * host (or [ScreenFragment]) in your own container without navigating:
 * `childFragmentManager.commit { add(R.id.panel, GaggleScreenFragment().withGooseScreen(screen)) }`.
 */
fun <T : Fragment> T.withGooseScreen(screen: Screen): T = apply {
    arguments = ScreenBundler.toBundle(screen)
}

/**
 * Instantiates [hostClass] through this FragmentManager's own [androidx.fragment.app.FragmentFactory]
 * with [screen]'s arguments set — the SAME path the FragmentManager uses to recreate the
 * fragment after rotation or process death, so a custom factory (constructor injection, test
 * doubles) sees goose's hosts on push and on restore alike.
 */
internal fun androidx.fragment.app.FragmentManager.instantiateGooseHost(
    hostClass: KClass<out Fragment>,
    screen: Screen,
): Fragment = fragmentFactory.instantiate(
    hostClass.java.classLoader!!,
    hostClass.java.name,
).withGooseScreen(screen)

/**
 * Direction 1 of the migration: a fully-migrated compose screen hosted on the LEGACY fragment
 * back stack. The default host; apps whose fragments share a base class register their own via
 * `installGooseNavigator(screenHost = ...)` with [gooseScreenView] as the implementation.
 */
class ScreenFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = gooseScreenView()

    companion object {
        /** Creation through the host FragmentManager's own FragmentFactory. */
        fun newInstance(fragmentManager: androidx.fragment.app.FragmentManager, screen: Screen): ScreenFragment =
            fragmentManager.instantiateGooseHost(ScreenFragment::class, screen) as ScreenFragment
    }
}
