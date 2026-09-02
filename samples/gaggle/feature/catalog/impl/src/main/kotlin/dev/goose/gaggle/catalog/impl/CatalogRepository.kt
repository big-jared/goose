package dev.goose.gaggle.catalog.impl

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.delay

data class Product(
    val id: String,
    val name: String,
    val emoji: String,
    val priceCents: Int,
    val description: String,
) {
    /** Whole-dollar display ("$29") — the pond economy has no cents. */
    val price: String get() = "$${priceCents / 100}"
}

/**
 * Demonstrates: an ordinary app-scoped repository. The "deal of the day" fails on its first
 * load, deterministically, so the catalog screen shows Mavericks' Fail + retry for real.
 */
@SingleIn(AppScope::class)
@Inject
class CatalogRepository {

    private val products = listOf(
        Product(
            "pond-1", "Premium pond pellets", "🌾", 400,
            "Small-batch pellets milled from marsh grain. The flock's daily driver.",
        ),
        Product(
            "pond-2", "Floating nest platform", "🛖", 2900,
            "A stable, self-leveling platform that rides out wakes and rowdy ducklings.",
        ),
        Product(
            "pond-3", "Winter down jacket", "🧥", 5900,
            "For the goose who already has down but wants more. Wind-tested at the north shore.",
        ),
        Product(
            "pond-4", "Honk amplifier", "📢", 1200,
            "Up to 12 decibels of extra honk. Neighbors will know.",
        ),
    )

    private val dealProduct = Product(
        "deal-1", "Golden egg incubator", "🥚", 9900,
        "Keeps one very special egg at exactly the right temperature. Today only.",
    )

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
            ?: Product(id, "Special order #$id", "📦", 0, "A mystery box from beyond the pond.")

    fun related(id: String): List<Product> = products.filter { it.id != id }.take(2)
}
