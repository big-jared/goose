package dev.goose.gaggle.cart.impl

import androidx.compose.runtime.mutableStateListOf
import dev.goose.gaggle.auth.api.LoggedInScope
import dev.goose.gaggle.cart.api.CartMutator
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

data class CartItem(val productId: String, val name: String)

/**
 * Demonstrates: a SESSION-scoped dependency. One cart per login, shared by every screen in the
 * logged-in graph (the catalog adds to it through the CartMutator contract), gone at logout.
 */
@SingleIn(LoggedInScope::class)
@ContributesBinding(LoggedInScope::class)
@Inject
class SessionCart : CartMutator {
    val items = mutableStateListOf<CartItem>()
    var lastOrder: String? = null

    override fun add(productId: String, name: String) {
        items += CartItem(productId, name)
    }

    fun remove(item: CartItem) {
        items.remove(item)
    }

    fun placeOrder(address: String): Int {
        val count = items.size
        lastOrder = "$count item(s) to $address"
        items.clear()
        return count
    }
}
