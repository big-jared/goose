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
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                GooseCompositionLocals(graph) {
                    GooseContent(screen, navigator, Modifier.fillMaxSize())
                }
            }
        }
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
