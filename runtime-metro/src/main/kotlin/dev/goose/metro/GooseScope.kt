package dev.goose.metro

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import dev.goose.runtime.ScreenEntry
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provider
import kotlin.reflect.KClass

/**
 * The goose surface of a child dependency graph. A graph extension interface extends this, and
 * every screen contributed to the extension's scope lands in [scopedScreenEntries]:
 * ```
 * @GraphExtension(CheckoutScope::class)
 * interface CheckoutGraph : GooseScopeAccessors {
 *     @GraphExtension.Factory
 *     @ContributesTo(AppScope::class)
 *     interface Factory { fun createCheckoutGraph(): CheckoutGraph }
 * }
 * ```
 * Feature modules register screens into the scope with `@GooseUi(GiftNoteScreen::class,
 * scope = CheckoutScope::class)` (or the hand-written contribution forms); their injected
 * parameters resolve from the CHILD graph, so session-scoped dependencies are ordinary
 * constructor injection.
 */
interface GooseScopeAccessors {
    @Multibinds(allowEmpty = true)
    val scopedScreenEntries: Map<KClass<*>, Provider<ScreenEntry>>
}

/**
 * The nearest active screen registry. Defaults to the app graph's root registry; [GooseScope]
 * overrides it for a subtree that owns a child graph.
 */
val LocalScreenRegistry = staticCompositionLocalOf<ScreenRegistry?> { null }

/**
 * Activates a child graph's screens for a subtree — the scope analogue of
 * [GooseCompositionLocals]. Wrap the content that owns the graph (typically a flow host, around
 * or inside its `FlowViewModelScope`):
 * ```
 * val checkoutGraph = remember { gooseGraph<CheckoutGraph.Factory>().createCheckoutGraph() }
 * GooseScope(checkoutGraph) {
 *     NavigableGooseContent(childStack, parent = parentNavigator)
 * }
 * ```
 * Inside, screens registered to the child scope resolve through the child graph; everything
 * registered at the parent (AppScope screens, or an outer GooseScope's) keeps working through
 * registry chaining. The child registry lives exactly as long as this composition: leaving the
 * subtree drops the registry and every child-scoped entry it cached, so nothing outlives the
 * graph. Composition-scoped also means NOT retained across recreation — a child graph is
 * dependencies, not state; state that must survive belongs in ViewModels, same as always.
 *
 * Works on both hosts: composition-based scoping doesn't care whether this subtree renders in a
 * Nav3 entry or inside a fragment-hosted screen mid-migration.
 */
@Composable
fun GooseScope(scopeGraph: Any, content: @Composable () -> Unit) {
    val accessors = scopeGraph as? GooseScopeAccessors
        ?: error(
            "${scopeGraph::class.qualifiedName} is not a GooseScopeAccessors. " +
                "Have your @GraphExtension interface extend GooseScopeAccessors."
        )
    val parentRegistry = LocalScreenRegistry.current
        ?: gooseGraph<GooseRuntimeAccessors>().screenRegistry
    val registry = remember(scopeGraph) {
        ScreenRegistry(accessors.scopedScreenEntries, parent = parentRegistry)
    }
    CompositionLocalProvider(LocalScreenRegistry provides registry, content = content)
}
