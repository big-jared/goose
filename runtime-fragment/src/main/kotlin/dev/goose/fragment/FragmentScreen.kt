package dev.goose.fragment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.compose.AndroidFragment
import dev.goose.runtime.Screen
import dev.goose.runtime.ScreenEntry
import dev.goose.runtime.ScreenUi
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import kotlinx.serialization.Serializable

/**
 * Direction 2 of the migration: a LEGACY fragment hosted on a Nav3-owned back stack. Lets a
 * mostly-converted flow flip to NavigableGooseContent while its last unconverted screens ride along.
 */
@Serializable
data class FragmentScreen(
    val fragmentClassName: String,
    val stringArgs: Map<String, String> = emptyMap(),
) : Screen {
    companion object {
        inline fun <reified F : Fragment> of(vararg args: Pair<String, String>): FragmentScreen =
            FragmentScreen(F::class.java.name, args.toMap())
    }
}

@ContributesIntoMap(AppScope::class, binding = binding<ScreenEntry>())
@ClassKey(FragmentScreen::class)
@Inject
class FragmentScreenEntry : ScreenUi<FragmentScreen>() {
    @Composable
    override fun Content(screen: FragmentScreen, modifier: Modifier) {
        val clazz = remember(screen.fragmentClassName) {
            Class.forName(screen.fragmentClassName).asSubclass(Fragment::class.java)
        }
        val arguments = remember(screen) {
            bundleOf(*screen.stringArgs.map { (k, v) -> k to v }.toTypedArray())
        }
        AndroidFragment(clazz = clazz, modifier = modifier, arguments = arguments)
    }
}
