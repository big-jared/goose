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
 * val factory = gooseGraph<CheckoutGraph.Factory>()
 * val checkoutGraph = rememberRetainedGraph { factory.createCheckoutGraph() }
 * GooseScope(checkoutGraph) {
 *     NavigableGooseContent(childStack, parent = parentNavigator)
 * }
 * ```
 * Create the graph with [rememberRetainedGraph], not a plain `remember`: the graph must survive
 * configuration changes together with the ViewModels it was injected into, or a rotation splits
 * session-scoped dependencies between the old graph (held by retained VMs) and a new one (seen
 * by recomposing screens). Retention ends when the owning entry pops; process death rebuilds
 * the graph fresh, like every other dependency.
 *
 * Inside, screens registered to the child scope resolve through the child graph; everything
 * registered at the parent (AppScope screens, or an outer GooseScope's) keeps working through
 * registry chaining. Leaving the subtree drops the child registry and every child-scoped entry
 * it cached, so nothing outlives the graph.
 *
 * Host boundary: scoping is composition-based, so it works in Nav3 entries and inside a
 * fragment-hosted screen's own compose content alike. It does NOT cross a FragmentManager push:
 * a scope-registered screen pushed as its own fragment (ScreenFragment) builds a fresh
 * composition from the app graph and will not find the child registry. During migration, keep
 * scoped screens inside compose-hosted flows.
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
