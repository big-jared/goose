package dev.goose.gaggle

import dev.goose.gaggle.catalog.impl.ReviewsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Review aggregation: averages, the star histogram, ordering, and input validation. */
class ReviewsRepositoryTest {

    private val repository = ReviewsRepository()

    @Test
    fun `seeded summary aggregates count, average, and histogram`() {
        // pond-1 seeds: 5, 5, 4, 2.
        val summary = repository.summary("pond-1")
        assertEquals(4, summary.count)
        assertEquals(4.0, summary.average, 0.0001)
        assertEquals("4.0", summary.averageLabel)
        assertEquals(listOf(0, 1, 0, 1, 2), summary.starCounts)
    }

    @Test
    fun `adding a review updates the summary and leads the list`() {
        repository.add("pond-1", 5, "Honk of approval")
        val summary = repository.summary("pond-1")
        assertEquals(5, summary.count)
        assertEquals(4.2, summary.average, 0.0001)
        assertEquals("4.2", summary.averageLabel)
        val newest = repository.reviews("pond-1").first()
        assertEquals("You", newest.author)
        assertEquals("Honk of approval", newest.text)
    }

    @Test
    fun `unknown product has an empty, zeroed summary`() {
        val summary = repository.summary("nope")
        assertEquals(0, summary.count)
        assertEquals(0.0, summary.average, 0.0)
        assertEquals(listOf(0, 0, 0, 0, 0), summary.starCounts)
    }

    @Test
    fun `ratings outside 1-5 and blank text are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { repository.add("pond-1", 0, "meh") }
        assertThrows(IllegalArgumentException::class.java) { repository.add("pond-1", 6, "wow") }
        assertThrows(IllegalArgumentException::class.java) { repository.add("pond-1", 3, "  ") }
        assertEquals(4, repository.summary("pond-1").count)
    }
}
