package dev.goose.fragment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import android.os.Bundle
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
 * TYPED fragment hosting on a Nav3 stack: a feature-owned `@Serializable` screen renders a
 * legacy fragment, with arguments built from the screen's own fields — Parcelables, enums,
 * whatever Bundle carries — instead of a string map. Register it like any screen:
 * ```
 * @Provides @IntoMap @ClassKey(TermsScreen::class)
 * fun termsEntry(): ScreenEntry = fragmentScreenEntry<TermsFragment, TermsScreen> { screen ->
 *     bundleOf("termsId" to screen.termsId, "author" to Author(screen.authorName))
 * }
 * ```
 * The fragment is instantiated through the host FragmentManager's own FragmentFactory, so
 * constructor-injected fragments keep working. Restoration is the screen's: the typed screen
 * value rides the persisted back stack and rebuilds the equivalent fragment after recreation
 * or process death. No reflection, no string parsing.
 */
inline fun <reified F : Fragment, reified S : Screen> fragmentScreenEntry(
    noinline arguments: (S) -> Bundle = { Bundle() },
): ScreenEntry = ScreenEntry { screen, modifier ->
    val typed = screen as S
    val args = remember(typed) { arguments(typed) }
    AndroidFragment(clazz = F::class.java, modifier = modifier, arguments = args)
}

/**
 * Direction 2 of the migration, quick form: a LEGACY fragment hosted on a Nav3-owned back
 * stack, addressed by class name with string arguments. Handy for fragments with trivial args;
 * destinations with typed arguments should use [fragmentScreenEntry] with a feature-owned
 * screen instead (no reflection, no string parsing).
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
