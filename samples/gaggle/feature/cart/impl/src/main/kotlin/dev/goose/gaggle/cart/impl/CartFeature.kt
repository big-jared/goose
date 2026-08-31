package dev.goose.gaggle.cart.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.goose.gaggle.auth.api.GaggleTabs
import dev.goose.gaggle.auth.api.LoggedInScope
import dev.goose.gaggle.cart.api.CartScreen
import dev.goose.gaggle.cart.api.CheckoutScreen
import dev.goose.gaggle.cart.api.RemoveItemScreen
import dev.goose.gaggle.auth.api.OrderHistoryScreen
import dev.goose.runtime.GooseUi
import dev.goose.runtime.LocalNavigator
import dev.goose.runtime.Navigator
import dev.goose.runtime.StateHolder
import dev.goose.runtime.TabNavigator
import dev.goose.runtime.rememberStateHolder
import kotlinx.coroutines.launch

/**
 * Demonstrates: awaiting results from a RETAINED presenter — the contract goToForResult
 * requires. A composition scope dies when the pushed screen replaces this one, cancelling the
 * await; the StateHolder's holderScope is retained with the entry, so the checkout's answer
 * finds its caller even though the cart screen left composition (and even across rotation).
 */
class CartHolder(
    private val navigator: Navigator,
    private val cart: SessionCart,
) : StateHolder<Unit>(Unit) {

    fun askRemove(item: CartItem) {
        holderScope.launch {
            val answer = navigator.goToForResult(RemoveItemScreen(item.name))
            if (answer?.remove == true) cart.remove(item)
        }
    }

    fun checkout() {
        holderScope.launch {
            val result = navigator.goToForResult(CheckoutScreen) ?: return@launch
            cart.placeOrder(result.shippingAddress)
        }
    }
}

/**
 * Demonstrates: a scope-registered screen driving three result flows — a dialog answering a
 * question, a wizard answering with a typed result, and a cross-tab jump (TabNavigator.goTo)
 * to a legacy screen afterwards. Session state lives in the cart; await lifetimes live in the
 * retained CartHolder.
 */
@GooseUi(CartScreen::class, scope = LoggedInScope::class)
@Composable
fun CartUi(modifier: Modifier, cart: SessionCart) {
    val navigator = LocalNavigator.current
    val holder = rememberStateHolder { nav -> CartHolder(nav, cart) }
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Cart", style = MaterialTheme.typography.headlineMedium)
        if (cart.items.isEmpty() && cart.lastOrder == null) Text("Nothing here yet. Honk at the shop.")
        cart.items.forEach { item ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.name)
                TextButton(onClick = { holder.askRemove(item) }) { Text("Remove") }
            }
        }
        if (cart.items.isNotEmpty()) {
            Button(onClick = holder::checkout) { Text("Checkout (${cart.items.size})") }
        }
        cart.lastOrder?.let { order ->
            Text("Order placed: $order")
            OutlinedButton(onClick = {
                (navigator as? TabNavigator)?.goTo(GaggleTabs.Profile, OrderHistoryScreen(orderCount = 1))
            }) { Text("View order history") }
        }
    }
}

/** Demonstrates: an OverlayScreen dialog that answers a goToForResult with a typed result. */
@GooseUi(RemoveItemScreen::class, scope = LoggedInScope::class)
@Composable
fun RemoveItemUi(screen: RemoveItemScreen, modifier: Modifier) {
    val navigator = LocalNavigator.current
    Card(Modifier.fillMaxWidth(0.92f)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Remove ${screen.name}?", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { navigator.pop() }) { Text("Keep it") }
                Button(onClick = {
                    navigator.pop(dev.goose.gaggle.cart.api.RemoveConfirmed(remove = true))
                }) { Text("Remove it") }
            }
        }
    }
}
