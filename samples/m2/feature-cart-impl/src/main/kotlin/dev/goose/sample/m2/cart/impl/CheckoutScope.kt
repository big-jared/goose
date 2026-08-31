package dev.goose.sample.m2.cart.impl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.goose.metro.GooseScopeAccessors
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * The checkout SESSION scope: one child graph per entry into the checkout flow. Dependencies
 * scoped here (like [CheckoutSession]) live exactly as long as one pass through the wizard —
 * created when CheckoutUi builds the graph, released when the flow's composition leaves.
 */
abstract class CheckoutScope private constructor()

/**
 * A session-scoped dependency: the same instance for every screen inside one checkout, a FRESH
 * instance for the next checkout. Compare with the flow ViewModel (retained state, survives
 * recreation) — this is a dependency, not state.
 */
@SingleIn(CheckoutScope::class)
@Inject
class CheckoutSession {
    val sessionId: Int = nextId++
    var giftNote: String by mutableStateOf("")

    private companion object {
        private var nextId = 1
    }
}

/**
 * The child graph. Extending [GooseScopeAccessors] is what lets `GooseScope` read the screens
 * contributed to [CheckoutScope]; the factory is contributed to the app graph so any host can
 * create a session.
 */
@GraphExtension(CheckoutScope::class)
interface CheckoutGraph : GooseScopeAccessors {

    @GraphExtension.Factory
    @ContributesTo(AppScope::class)
    interface Factory {
        fun createCheckoutGraph(): CheckoutGraph
    }
}
