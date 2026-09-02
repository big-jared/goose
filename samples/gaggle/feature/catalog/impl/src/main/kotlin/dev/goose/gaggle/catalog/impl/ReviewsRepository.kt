package dev.goose.gaggle.catalog.impl

import androidx.compose.runtime.mutableStateMapOf
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

data class Review(val author: String, val rating: Int, val text: String)

/** Aggregates for one product; [starCounts] is indexed 0..4 for 1..5 stars. */
data class RatingSummary(val count: Int, val average: Double, val starCounts: List<Int>) {
    /** "4.3" — one decimal, dot-separated regardless of locale. */
    val averageLabel: String get() = ((average * 10).toInt() / 10.0).toString()
}

/**
 * Demonstrates: observable app-scoped state read by one screen and written through a typed
 * result from another. Backed by snapshot state, so the product page's summary and list
 * recompose the moment a review posts.
 */
@SingleIn(AppScope::class)
@Inject
class ReviewsRepository {

    private val reviewsByProduct = mutableStateMapOf<String, List<Review>>().apply {
        put(
            "pond-1",
            listOf(
                Review("Branta", 5, "My gosling approves"),
                Review("Wingston", 5, "Honk-worthy quality"),
                Review("Pip", 4, "Crunchy, but the bag is hard to open with a beak"),
                Review("Gary", 2, "Feathers were ruffled"),
            ),
        )
        put(
            "pond-2",
            listOf(
                Review("Branta", 5, "Slept through a regatta"),
                Review("Maud", 4, "Sturdy. Ducklings keep boarding it though"),
            ),
        )
        put(
            "pond-3",
            listOf(Review("Wingston", 5, "Double down is the way")),
        )
        put(
            "pond-4",
            listOf(
                Review("Gary", 5, "The whole pond hears me now"),
                Review("Maud", 1, "The whole pond hears Gary now"),
            ),
        )
        put(
            "deal-1",
            listOf(Review("Pip", 5, "The egg has never been happier")),
        )
    }

    fun reviews(productId: String): List<Review> = reviewsByProduct[productId].orEmpty()

    fun add(productId: String, rating: Int, text: String, author: String = "You") {
        require(rating in 1..5) { "Rating must be 1..5 stars, got $rating" }
        require(text.isNotBlank()) { "Say something, even just HONK" }
        reviewsByProduct[productId] = listOf(Review(author, rating, text)) + reviews(productId)
    }

    fun summary(productId: String): RatingSummary {
        val reviews = reviews(productId)
        val starCounts = (1..5).map { star -> reviews.count { it.rating == star } }
        val average = if (reviews.isEmpty()) 0.0 else reviews.sumOf { it.rating }.toDouble() / reviews.size
        return RatingSummary(reviews.size, average, starCounts)
    }
}
