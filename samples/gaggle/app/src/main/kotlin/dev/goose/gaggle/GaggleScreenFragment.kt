package dev.goose.gaggle

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.fragment.app.Fragment
import dev.goose.fragment.gooseScreenView

/**
 * Demonstrates: the app-owned screen host. Fragment-hosted goose screens (the support chat)
 * ride THIS fragment instead of goose's ScreenFragment, so they inherit whatever an app's
 * fragment base class provides — here the app theme via the wrap, and a lifecycle hook of the
 * kind base classes carry (analytics, leak tracking). Registered with
 * `screenHost = ::GaggleScreenFragment` where the FragmentNavigator is built.
 */
class GaggleScreenFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = gooseScreenView { content -> MaterialTheme { content() } }

    override fun onDestroy() {
        super.onDestroy()
        // A real app's fragment base class would report analytics or lifecycle here.
    }
}
