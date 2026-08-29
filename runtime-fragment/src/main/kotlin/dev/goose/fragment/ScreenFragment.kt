package dev.goose.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import dev.goose.metro.GooseGraphHolder
import dev.goose.metro.GooseRuntimeAccessors
import dev.goose.metro.LocalGooseGraph
import dev.goose.runtime.LocalNavigator
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
 * Direction 1 of the migration: a fully-migrated compose screen hosted on the LEGACY fragment
 * back stack. The screen's Ui and ViewModel are already pure Goose; only the host is a fragment.
 * Once every screen in a flow is migrated, the flow flips to a ScreenNavDisplay and this class
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
        val navigator = (requireActivity() as FragmentNavigatorOwner).gooseNavigator
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                CompositionLocalProvider(
                    LocalGooseGraph provides graph,
                    LocalNavigator provides navigator,
                ) {
                    val registry = (graph as GooseRuntimeAccessors).screenRegistry
                    val entry = remember(screen) { registry.entryFor(screen) }
                    entry.Content(screen, Modifier.fillMaxSize())
                }
            }
        }
    }

    companion object {
        fun newInstance(screen: Screen): ScreenFragment = ScreenFragment().apply {
            arguments = ScreenBundler.toBundle(screen)
        }
    }
}
