package dev.goose.nav3

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import dev.goose.metro.GooseRuntimeAccessors
import dev.goose.metro.gooseGraph
import dev.goose.runtime.LocalNavigator
import dev.goose.runtime.LocalScreenAnimatedContentScope
import dev.goose.runtime.LocalSharedTransitionScope
import dev.goose.runtime.Navigator
import dev.goose.runtime.Screen

/**
 * A persisted back stack whose serializers come from every feature module's contributed
 * `SerializersModule` — the multi-module answer to Nav3's polymorphic NavKey requirement.
 */
@Composable
fun rememberGooseBackStack(vararg roots: Screen): NavBackStack<NavKey> {
    val module = gooseGraph<GooseRuntimeAccessors>().navSerializersModule
    val configuration = remember(module) {
        SavedStateConfiguration { serializersModule = module }
    }
    return rememberNavBackStack(configuration, *roots)
}

/**
 * The Compose host: renders [backStack] with a NavDisplay wired for Goose screens.
 *
 * - Screens resolve through the app graph's ScreenRegistry (contributed by feature modules).
 * - Entries get a ViewModelStore + saveable-state decorator, so Mavericks VMs retain across
 *   config changes and clear on pop.
 * - A [SharedTransitionLayout] wraps the display; screens opt in via Modifier.sharedScreenElement.
 * - Pass [parent] when nesting (a flow hosted inside another stack's entry): unhandled root pops
 *   bubble up the navigator tree.
 */
@Composable
fun ScreenNavDisplay(
    backStack: MutableList<NavKey>,
    modifier: Modifier = Modifier,
    parent: Navigator? = null,
    onRootBack: (() -> Unit)? = null,
) {
    val resultRouter = gooseGraph<GooseRuntimeAccessors>().resultRouter
    val navigator = remember(backStack, parent) { Nav3Navigator(backStack, resultRouter, parent) }
    GooseNavDisplay(backStack, navigator, modifier, onRootBack)
}

/** Shared display core for single-stack and tabbed hosts. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun GooseNavDisplay(
    displayStack: List<NavKey>,
    navigator: Navigator,
    modifier: Modifier = Modifier,
    onRootBack: (() -> Unit)? = null,
) {
    val registry = gooseGraph<GooseRuntimeAccessors>().screenRegistry
    SharedTransitionLayout(modifier) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavDisplay(
                backStack = displayStack,
                onBack = { if (!navigator.pop()) onRootBack?.invoke() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = { key ->
                    val screen = key as? Screen
                        ?: error("Non-Screen NavKey on a Goose back stack: $key")
                    NavEntry(key) {
                        CompositionLocalProvider(
                            LocalNavigator provides navigator,
                            LocalScreenAnimatedContentScope provides LocalNavAnimatedContentScope.current,
                        ) {
                            val entry = remember(screen) { registry.entryFor(screen) }
                            entry.Content(screen, Modifier.fillMaxSize())
                        }
                    }
                },
            )
        }
    }
}
