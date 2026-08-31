package dev.goose.metro

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Retains the graph so it matches the ViewModels it feeds. Closes AutoCloseable graphs when the
 * owning entry pops.
 */
internal class RetainedGraphHolder : ViewModel() {
    var graph: Any? = null

    override fun onCleared() {
        (graph as? AutoCloseable)?.close()
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
 * [key] distinguishes multiple retained graphs inside one entry; the default suits the common
 * one-flow-one-graph case. If the graph implements [AutoCloseable], it is closed when released.
 */
@Composable
fun rememberRetainedGraph(key: String = "goose:retainedGraph", create: () -> Any): Any {
    val holder = viewModel<RetainedGraphHolder>(key = key)
    return holder.graph ?: create().also { holder.graph = it }
}
