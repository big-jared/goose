package dev.goose.metro

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import dev.goose.runtime.LocalNavigator
import dev.goose.runtime.Navigator
import dev.goose.runtime.Screen
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/**
 * Provides the Goose ambients for a subtree — the analogue of CircuitCompositionLocals. Wrap the
 * root of every host in it:
 * ```
 * GooseCompositionLocals(graph) {
 *   NavigableGooseContent(rememberGooseBackStack(HomeScreen))
 * }
 * ```
 */
@Composable
fun GooseCompositionLocals(graph: Any, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGooseGraph provides graph, content = content)
}

/**
 * Renders a single [screen] through the registry — the analogue of CircuitContent. Stack hosts
 * (NavigableGooseContent, ScreenFragment) build on this; it is also useful for previews and for
 * embedding one Goose screen inside non-Goose UI.
 */
@Composable
fun GooseContent(
    screen: Screen,
    navigator: Navigator,
    modifier: Modifier = Modifier,
) {
    val registry = gooseGraph<GooseRuntimeAccessors>().screenRegistry
    CompositionLocalProvider(LocalNavigator provides navigator) {
        val entry = remember(screen) { registry.entryFor(screen) }
        entry.UntypedContent(screen, modifier)
    }
}

/**
 * Sugar for the per-feature serializer contribution Nav3 persistence needs:
 * ```
 * @Provides @IntoSet fun serializers(): SerializersModule =
 *   screenSerializers { subclass(ProfileScreen::class) }
 * ```
 */
fun screenSerializers(builder: PolymorphicModuleBuilder<NavKey>.() -> Unit): SerializersModule =
    SerializersModule { polymorphic(NavKey::class, builderAction = builder) }
