package dev.goose.metro

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Retains the graph so it matches the ViewModels it feeds, and guarantees exactly-once disposal
 * when the owning entry is removed.
 */
internal class RetainedGraphHolder : ViewModel() {
    var graph: Any? = null
    var onRelease: ((Any) -> Unit)? = null
    private var released = false

    override fun onCleared() {
        val g = graph ?: return
        if (released) return
        released = true
        onRelease?.invoke(g)
        (g as? AutoCloseable)?.close()
    }
}

/**
 * Creates [create]'s graph once per OWNING NAV ENTRY and retains it: the graph survives
 * configuration changes alongside the ViewModels it injects into, and is released when the
 * entry pops (process death rebuilds it fresh, same as the graph's own dependencies).
 *
 * This is THE way to create a child graph for a [GooseScope]. A plain `remember { }` would
 * rebuild the graph on rotation while retained ViewModels keep references into the OLD graph,
 * splitting session-scoped dependencies in two. Retaining the graph with the entry keeps one
 * session per flow, no matter how many times the device rotates:
 * ```
 * val factory = gooseGraph<CheckoutGraph.Factory>()
 * val checkoutGraph = rememberRetainedGraph { factory.createCheckoutGraph() }
 * GooseScope(checkoutGraph) { ... }
 * ```
 * [key] distinguishes multiple retained graphs inside one entry (each disposed independently);
 * the default suits the common one-flow-one-graph case.
 *
 * DISPOSAL CONTRACT, deterministic and exactly-once. The graph is released when its retaining
 * entry leaves the world for real: the entry popped (any path: `pop`, system back, a legacy
 * `popBackStack()`), `resetRoot` clearing the stack, or the enclosing flow/host entry popping
 * (nested stores clear with their owner). It is NOT released on configuration changes (that is
 * the point of retaining it) and not on process death (the process dies; the relaunched process
 * creates a fresh graph). On release, [onRelease] runs first, then [AutoCloseable.close] if the
 * graph implements it. Pass [onRelease] for generated or externally supplied graphs that own
 * scopes, listeners, or subscriptions but implement no closing interface — disposal must never
 * depend on the graph type's implementation details:
 * ```
 * val graph = rememberRetainedGraph(onRelease = { (it as CheckoutGraph).session.close() }) {
 *     factory.createCheckoutGraph()
 * }
 * ```
 */
@Composable
fun rememberRetainedGraph(
    key: String = "goose:retainedGraph",
    onRelease: ((Any) -> Unit)? = null,
    create: () -> Any,
): Any {
    val holder = viewModel<RetainedGraphHolder>(key = key)
    holder.onRelease = onRelease
    return holder.graph ?: create().also { holder.graph = it }
}
