package dev.goose.nav3

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import android.util.Log
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.SavedState
import androidx.savedstate.serialization.SavedStateConfiguration
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.modules.plus
import java.util.UUID
import dev.goose.metro.GooseContent
import dev.goose.metro.goose
import dev.goose.runtime.LocalScreenAnimatedContentScope
import dev.goose.runtime.LocalSharedTransitionScope
import dev.goose.runtime.Navigator
import dev.goose.runtime.Screen
import dev.goose.runtime.ScreenTransitions
import dev.goose.runtime.effectiveOverlay
import dev.goose.runtime.effectiveTransitions

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
 *
 * Restoration is resilient BY DESIGN: if a saved stack cannot be decoded (a screen class was
 * renamed or removed in an app update, lives in an unloaded dynamic feature, or its serialized
 * shape changed incompatibly), the stack restarts at [initial] instead of crashing on launch.
 * Losing navigation state on a bad restore is recoverable; a crash loop is not.
 */
@Composable
fun rememberGooseBackStack(initial: List<Screen>): NavBackStack<NavKey> {
    val module = goose().navSerializersModule
    val configuration = remember(module) {
        // Push records must always round-trip, whether or not the app's Goose was assembled by
        // Metro (where this module arrives by contribution) or by hand (where it wouldn't).
        SavedStateConfiguration {
            serializersModule = module + PushRecordSerializers.pushRecordSerializers()
        }
    }
    return rememberSaveable(
        saver = Saver(
            save = { stack -> encodeToSavedState(navBackStackSerializer, stack, configuration) },
            restore = { saved -> decodeBackStackOrNull(saved, configuration) },
        ),
    ) { NavBackStack(*initial.map { it.pushed() }.toTypedArray<NavKey>()) }
}

internal val navBackStackSerializer = NavBackStack.serializer(PolymorphicSerializer(NavKey::class))

// Deliberately quicker than the runtime's default slide: a crossfade carries less motion than
// a slide, so at equal length it reads slower. Don't unify the two durations.
internal const val ROOT_FADE_MS = 220

/**
 * The per-entry NavDisplay metadata: dialog scenes for overlays, transition specs for motion.
 * Pure — split out of the entryProvider so tests can pin every branch without composing.
 *
 * - A stack's ROOT never renders as a dialog: nav3 requires a non-empty scene beneath an
 *   overlay, and in a tab host the entry beneath would belong to ANOTHER tab. Root overlays
 *   degrade to full-screen entries.
 * - A stack ROOT arriving at or leaving the top is a stack CHANGE (a tab switch, or back
 *   falling through to the primary tab), never a push or pop within one stack — so roots
 *   crossfade instead of playing stack motion, and the predictive gesture previews the same
 *   fade. Non-root entries animate per screen, falling back to the host default.
 */
internal fun entryMetadata(
    screen: Screen,
    isRoot: Boolean,
    defaultTransitions: ScreenTransitions?,
): Map<String, Any> {
    // Facet resolution: the screen's own declaration, then its Presentation's, then the host's.
    val overlay = screen.effectiveOverlay()
    var metadata: Map<String, Any> =
        if (overlay != null && !isRoot) {
            DialogSceneStrategy.dialog(overlay.dialogProperties())
        } else {
            emptyMap()
        }
    if (isRoot) {
        val fade = { fadeIn(tween(ROOT_FADE_MS)) togetherWith fadeOut(tween(ROOT_FADE_MS)) }
        metadata = metadata +
            NavDisplay.transitionSpec { fade() } +
            NavDisplay.popTransitionSpec { fade() } +
            NavDisplay.predictivePopTransitionSpec { _ -> fade() }
    } else {
        val transitions = screen.effectiveTransitions(defaultTransitions)
        if (transitions != null) {
            transitions.enterTransition()?.let { t -> metadata = metadata + NavDisplay.transitionSpec { t } }
            transitions.exitTransition()?.let { t -> metadata = metadata + NavDisplay.popTransitionSpec { t } }
            metadata = metadata + NavDisplay.predictivePopTransitionSpec { edge ->
                transitions.predictivePopTransition(edge)
                    ?: (fadeIn() togetherWith fadeOut())
            }
        }
    }
    return metadata
}

/** Decodes a saved stack, or returns null (restart fresh) when the saved form is unreadable. */
internal fun decodeBackStackOrNull(
    saved: SavedState,
    configuration: SavedStateConfiguration,
): NavBackStack<NavKey>? = try {
    decodeFromSavedState(navBackStackSerializer, saved, configuration)
} catch (e: Exception) {
    Log.w("Goose", "Back stack restore failed (app update removed a screen class?); starting fresh", e)
    null
} catch (e: LinkageError) {
    // Class loading itself failed (partial dynamic-feature install, broken static init).
    Log.w("Goose", "Back stack restore failed loading a screen class; starting fresh", e)
    null
}

/**
 * The stack host — the analogue of NavigableCircuitContent. Renders [backStack] with a NavDisplay
 * wired for Goose screens:
 *
 * - Screens resolve through the app graph's ScreenRegistry (contributed by feature modules).
 * - Entries get a ViewModelStore + saveable-state decorator, so Mavericks VMs retain across
 *   config changes and clear on pop.
 * - Screens with the [dev.goose.runtime.Overlay] facet — [dev.goose.runtime.OverlayScreen]s,
 *   or screens whose [dev.goose.runtime.Presentation] implements it — render in a dialog over
 *   the previous entry (DialogSceneStrategy).
 * - A [SharedTransitionLayout] wraps the display; screens opt in via Modifier.sharedScreenElement.
 * - Pass [parent] when nesting (a flow hosted inside another stack's entry): unhandled root pops
 *   bubble up the navigator tree. [onRootBack] fires when a back event goes entirely unhandled.
 * - [defaultTransitions] animates every screen that doesn't declare its own [ScreenTransitions]
 *   (e.g. [dev.goose.runtime.SlideScreenTransitions] for conventional side-to-side stack motion,
 *   previewed by the predictive back gesture); screens implementing the interface still win.
 *   Stack ROOTS are the exception: a root changing at the top is a stack change (a tab switch,
 *   or resetRoot), so a change landing ON a root crossfades and neither the default nor the
 *   root's own [ScreenTransitions] is consulted. A stack change landing on a stack with screens
 *   pushed above its root animates with that top screen's transitions instead.
 */
@Composable
fun NavigableGooseContent(
    backStack: MutableList<NavKey>,
    modifier: Modifier = Modifier,
    parent: Navigator? = null,
    onRootBack: (() -> Unit)? = null,
    defaultTransitions: ScreenTransitions? = null,
) {
    val resultRouter = goose().resultRouter
    // Stable per-stack-instance identity: scopes result routing to this stack, so equal screen
    // classes awaited in different stacks or activities never cross-deliver. Saveable, so
    // pending awaiters survive recreation (restore ignores the input key, so the restored stack
    // being a new instance is fine); keyed on the stack so SWAPPING a different stack into this
    // slot mid-composition gets a fresh tag instead of inheriting the old stack's requests.
    val stackTag = rememberSaveable(backStack) { UUID.randomUUID().toString() }
    val navigator = remember(backStack, parent) { Nav3Navigator(backStack, resultRouter, parent, stackTag) }
    GooseNavDisplay(
        displayStack = backStack,
        navigator = navigator,
        isStackRoot = { key -> key === backStack.firstOrNull() },
        modifier = modifier,
        onRootBack = onRootBack,
        defaultTransitions = defaultTransitions,
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
    defaultTransitions: ScreenTransitions? = null,
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
                    // The key is the per-push record (unique even for equal screens), which is
                    // what NavEntry identity, saveable state, and ViewModel stores scope to;
                    // everything below renders the unwrapped screen.
                    val screen = key.asScreen()
                    val metadata = entryMetadata(screen, isStackRoot(key), defaultTransitions)
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
