package dev.goose.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import dev.goose.metro.GooseCompositionLocals
import dev.goose.metro.GooseContent
import dev.goose.metro.GooseGraphHolder
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
        val navigator = findNavigatorOwner().gooseNavigator
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                GooseCompositionLocals(graph) {
                    GooseContent(screen, navigator, Modifier.fillMaxSize())
                }
            }
        }
    }

    /** Nearest owner wins: parent fragments first (nested legacy stacks), then the activity. */
    private fun findNavigatorOwner(): FragmentNavigatorOwner {
        var parent = parentFragment
        while (parent != null) {
            if (parent is FragmentNavigatorOwner) return parent
            parent = parent.parentFragment
        }
        return requireActivity() as? FragmentNavigatorOwner
            ?: error(
                "No FragmentNavigatorOwner found. The host activity (or a parent fragment) must " +
                    "implement FragmentNavigatorOwner to host migrated screens."
            )
    }

    companion object {
        fun newInstance(screen: Screen): ScreenFragment = ScreenFragment().apply {
            arguments = ScreenBundler.toBundle(screen)
        }
    }
}
