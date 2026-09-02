package dev.goose.gaggle

import dev.goose.gaggle.cart.impl.SessionCart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The cart's line/quantity/total arithmetic, pure and framework-free. */
class SessionCartTest {

    private val cart = SessionCart()

    @Test
    fun `adding the same product twice bumps quantity on one line`() {
        cart.add("pond-1", "Pellets", 400)
        cart.add("pond-1", "Pellets", 400)
        assertEquals(1, cart.items.size)
        assertEquals(2, cart.items.single().qty)
        assertEquals(2, cart.quantityOf("pond-1"))
        assertEquals(800, cart.totalCents)
    }

    @Test
    fun `distinct products get their own lines and sum into the total`() {
        cart.add("pond-1", "Pellets", 400)
        cart.add("pond-2", "Platform", 2900)
        cart.add("pond-1", "Pellets", 400)
        assertEquals(2, cart.items.size)
        assertEquals(3700, cart.totalCents)
        assertEquals(0, cart.quantityOf("pond-9"))
    }

    @Test
    fun `remove drops the whole line whatever its quantity`() {
        cart.add("pond-1", "Pellets", 400)
        cart.add("pond-1", "Pellets", 400)
        cart.remove(cart.items.single())
        assertEquals(0, cart.items.size)
        assertEquals(0, cart.quantityOf("pond-1"))
    }

    @Test
    fun `placeOrder counts units, records the order, and clears the cart`() {
        cart.add("pond-1", "Pellets", 400)
        cart.add("pond-1", "Pellets", 400)
        cart.add("pond-2", "Platform", 2900)
        val count = cart.placeOrder("1 Goose Way")
        assertEquals(3, count)
        assertEquals("3 item(s) to 1 Goose Way", cart.lastOrder)
        assertEquals(0, cart.items.size)
    }

    @Test
    fun `fresh cart is empty with no order`() {
        assertEquals(0, cart.totalCents)
        assertNull(cart.lastOrder)
    }
}
