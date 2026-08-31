package dev.goose.gaggle.catalog.impl

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.delay

data class Product(val id: String, val name: String, val emoji: String, val price: String)

/**
 * Demonstrates: an ordinary app-scoped repository. The "deal of the day" fails on its first
 * load, deterministically, so the catalog screen shows Mavericks' Fail + retry for real.
 */
@SingleIn(AppScope::class)
@Inject
class CatalogRepository {

    private val products = listOf(
        Product("pond-1", "Premium pond pellets", "🌾", "$4"),
        Product("pond-2", "Floating nest platform", "🛖", "$29"),
        Product("pond-3", "Winter down jacket", "🧥", "$59"),
        Product("pond-4", "Honk amplifier", "📢", "$12"),
    )

    private val dealProduct = Product("deal-1", "Golden egg incubator", "🥚", "$99 (deal!)")

    private var dealAttempts = 0

    suspend fun loadProducts(): List<Product> {
        delay(30)
        return products
    }

    suspend fun loadDeal(): Product {
        delay(30)
        if (dealAttempts++ == 0) error("The pond network flaked. Try again.")
        return dealProduct
    }

    fun productById(id: String): Product =
        (products + dealProduct).firstOrNull { it.id == id }
            ?: Product(id, "Special order #$id", "📦", "$?")

    fun related(id: String): List<Product> = products.filter { it.id != id }.take(2)
}
