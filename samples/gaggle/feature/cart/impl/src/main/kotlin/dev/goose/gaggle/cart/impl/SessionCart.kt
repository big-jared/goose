package dev.goose.gaggle.cart.impl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.goose.gaggle.auth.api.LoggedInScope
import dev.goose.gaggle.cart.api.CartMutator
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

data class CartItem(val productId: String, val name: String, val unitPriceCents: Int, val qty: Int) {
    val lineTotalCents: Int get() = unitPriceCents * qty
}

/**
 * Demonstrates: a SESSION-scoped dependency. One cart per login, shared by every screen in the
 * logged-in graph (the catalog adds to it through the CartMutator contract), gone at logout.
 * One line per product: adding an already-carted product bumps its quantity.
 */
@SingleIn(LoggedInScope::class)
@ContributesBinding(LoggedInScope::class)
@Inject
class SessionCart : CartMutator {
    val items = mutableStateListOf<CartItem>()
    var lastOrder: String? by mutableStateOf(null)

    val totalCents: Int get() = items.sumOf { it.lineTotalCents }

    override fun add(productId: String, name: String, unitPriceCents: Int) {
        val index = items.indexOfFirst { it.productId == productId }
        if (index >= 0) {
            items[index] = items[index].let { it.copy(qty = it.qty + 1) }
        } else {
            items += CartItem(productId, name, unitPriceCents, qty = 1)
        }
    }

    override fun quantityOf(productId: String): Int =
        items.firstOrNull { it.productId == productId }?.qty ?: 0

    /** Removes the whole line, whatever its quantity — the dialog asked about the product. */
    fun remove(item: CartItem) {
        items.removeAll { it.productId == item.productId }
    }

    fun placeOrder(address: String): Int {
        val count = items.sumOf { it.qty }
        lastOrder = "$count item(s) to $address"
        items.clear()
        return count
    }
}
