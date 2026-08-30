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
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import dev.goose.metro.GooseContent
import dev.goose.metro.GooseRuntimeAccessors
import dev.goose.metro.gooseGraph
import dev.goose.runtime.LocalScreenAnimatedContentScope
import dev.goose.runtime.LocalSharedTransitionScope
import dev.goose.runtime.Navigator
import dev.goose.runtime.OverlayScreen
import dev.goose.runtime.Screen

/**
 * A persisted back stack whose serializers come from every feature module's contributed
 * `SerializersModule` — the multi-module answer to Nav3's polymorphic NavKey requirement.
 */
@Composable
fun rememberGooseBackStack(vararg roots: Screen): NavBackStack<NavKey> =
    rememberGooseBackStack(roots.toList())

/**
 * List overload for synthesized stacks — e.g. a deep link that should land on a detail screen
 * with its parents beneath it: `rememberGooseBackStack(listOf(HomeScreen, ItemDetailScreen(id)))`.
 */
@Composable
fun rememberGooseBackStack(initial: List<Screen>): NavBackStack<NavKey> {
    val module = gooseGraph<GooseRuntimeAccessors>().navSerializersModule
    val configuration = remember(module) {
        SavedStateConfiguration { serializersModule = module }
    }
    return rememberNavBackStack(configuration, *initial.toTypedArray())
}

/**
 * The stack host — the analogue of NavigableCircuitContent. Renders [backStack] with a NavDisplay
 * wired for Goose screens:
 *
 * - Screens resolve through the app graph's ScreenRegistry (contributed by feature modules).
 * - Entries get a ViewModelStore + saveable-state decorator, so Mavericks VMs retain across
 *   config changes and clear on pop.
 * - [OverlayScreen]s render in a dialog over the previous entry (DialogSceneStrategy).
 * - A [SharedTransitionLayout] wraps the display; screens opt in via Modifier.sharedScreenElement.
 * - Pass [parent] when nesting (a flow hosted inside another stack's entry): unhandled root pops
 *   bubble up the navigator tree. [onRootBack] fires when a back event goes entirely unhandled.
 */
@Composable
fun NavigableGooseContent(
    backStack: MutableList<NavKey>,
    modifier: Modifier = Modifier,
    parent: Navigator? = null,
    onRootBack: (() -> Unit)? = null,
) {
    val resultRouter = gooseGraph<GooseRuntimeAccessors>().resultRouter
    val navigator = remember(backStack, parent) { Nav3Navigator(backStack, resultRouter, parent) }
    GooseNavDisplay(
        displayStack = backStack,
        navigator = navigator,
        isStackRoot = { key -> key === backStack.firstOrNull() },
        modifier = modifier,
        onRootBack = onRootBack,
    )
}

/** Shared display core for single-stack and tabbed hosts. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun GooseNavDisplay(
    displayStack: List<NavKey>,
    navigator: Navigator,
    isStackRoot: (NavKey) -> Boolean,
    modifier: Modifier = Modifier,
    onRootBack: (() -> Unit)? = null,
) {
    SharedTransitionLayout(modifier) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavDisplay(
                backStack = displayStack,
                onBack = { if (!navigator.pop()) onRootBack?.invoke() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                // Falls back to single-pane for non-overlay entries.
                sceneStrategies = remember { listOf(DialogSceneStrategy<NavKey>()) },
                entryProvider = { key ->
                    val screen = key as? Screen
                        ?: error("Non-Screen NavKey on a Goose back stack: $key")
                    // A stack's ROOT never renders as a dialog: nav3 requires a non-empty scene
                    // beneath an overlay, and in a tab host the entry beneath would belong to
                    // ANOTHER tab. Root overlays degrade to full-screen entries.
                    val metadata =
                        if (screen is OverlayScreen && !isStackRoot(key)) {
                            DialogSceneStrategy.dialog()
                        } else {
                            emptyMap()
                        }
                    NavEntry(key, metadata = metadata) {
                        CompositionLocalProvider(
                            LocalScreenAnimatedContentScope provides LocalNavAnimatedContentScope.current,
                        ) {
                            GooseContent(screen, navigator, Modifier.fillMaxSize())
                        }
                    }
                },
            )
        }
    }
}
