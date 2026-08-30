package dev.goose.fragment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.compose.AndroidFragment
import androidx.navigation3.runtime.NavKey
import dev.goose.runtime.Screen
import dev.goose.runtime.ScreenEntry
import dev.goose.runtime.TypedScreenEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Direction 2 of the migration: a LEGACY fragment hosted on a Nav3-owned back stack. Lets a
 * mostly-converted flow flip to ScreenNavDisplay while its last unconverted screens ride along.
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
class FragmentScreenEntry : TypedScreenEntry<FragmentScreen>() {
    @Composable
    override fun ScreenContent(screen: FragmentScreen, modifier: Modifier) {
        val clazz = remember(screen.fragmentClassName) {
            Class.forName(screen.fragmentClassName).asSubclass(Fragment::class.java)
        }
        val arguments = remember(screen) {
            bundleOf(*screen.stringArgs.map { (k, v) -> k to v }.toTypedArray())
        }
        AndroidFragment(clazz = clazz, modifier = modifier, arguments = arguments)
    }
}

@ContributesTo(AppScope::class)
interface FragmentScreenModule {
    companion object {
        @Provides
        @IntoSet
        fun fragmentScreenSerializers(): SerializersModule = SerializersModule {
            polymorphic(NavKey::class) { subclass(FragmentScreen::class) }
        }
    }
}
