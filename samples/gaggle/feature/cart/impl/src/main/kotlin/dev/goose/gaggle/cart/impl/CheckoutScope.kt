package dev.goose.gaggle.cart.impl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.goose.gaggle.auth.api.LoggedInScope
import dev.goose.metro.GooseScopeAccessors
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Demonstrates: a NESTED scope at composition level AND graph level. One CheckoutSession per
 * pass through the wizard; the checkout's GooseScope sits inside the logged-in GooseScope, so
 * screens here resolve checkout deps first, then session deps, then app deps — the graph
 * extension below is contributed to LoggedInScope, so Metro's graph nesting mirrors the same
 * chain (the confirm step injects the session's SessionCart from a checkout-scoped screen).
 */
abstract class CheckoutScope private constructor()

@SingleIn(CheckoutScope::class)
@Inject
class CheckoutSession {
    var giftNote: String by mutableStateOf("")
}

@ContributesTo(CheckoutScope::class)
interface CheckoutSessionAccessor {
    val session: CheckoutSession
}

@GraphExtension(CheckoutScope::class)
interface CheckoutGraph : GooseScopeAccessors {
    @GraphExtension.Factory
    @ContributesTo(LoggedInScope::class)
    interface Factory {
        fun createCheckoutGraph(): CheckoutGraph
    }
}
