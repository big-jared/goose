package dev.goose.gaggle.catalog.impl

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import dev.goose.gaggle.catalog.api.ReviewPosted
import dev.goose.gaggle.catalog.api.WriteReviewScreen
import dev.goose.runtime.GooseUi
import dev.goose.runtime.LocalNavigator

/**
 * The rating distribution, custom-drawn: one Canvas bar per star row, growing from zero when
 * the chart first appears (an Animatable driven by LaunchedEffect — no drawable resources,
 * no chart library).
 */
@Composable
internal fun RatingBars(summary: RatingSummary, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(summary.count) { progress.animateTo(1f, tween(600)) }
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val maxCount = summary.starCounts.max().coerceAtLeast(1)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (star in 5 downTo 1) {
            val fraction = summary.starCounts[star - 1] / maxCount.toFloat()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$star★", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(28.dp))
                Canvas(
                    Modifier
                        .weight(1f)
                        .height(10.dp),
                ) {
                    val radius = CornerRadius(size.height / 2)
                    drawRoundRect(trackColor, cornerRadius = radius)
                    drawRoundRect(
                        barColor,
                        topLeft = Offset.Zero,
                        size = Size(size.width * fraction * progress.value, size.height),
                        cornerRadius = radius,
                    )
                }
            }
        }
    }
}

/**
 * Demonstrates: a lightweight form screen with NO ViewModel — rememberSaveable carries the
 * draft across rotation, and the answer leaves as a typed result (pop). Button-only input:
 * tap a star, tap a phrase, post.
 */
@GooseUi(WriteReviewScreen::class)
@Composable
fun WriteReviewUi(screen: WriteReviewScreen, modifier: Modifier, repository: CatalogRepository) {
    val navigator = LocalNavigator.current
    val product = repository.productById(screen.productId)
    var rating by rememberSaveable { mutableIntStateOf(0) }
    var phrase by rememberSaveable { mutableStateOf("") }
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Review ${product.name}", style = MaterialTheme.typography.headlineSmall)
        Row {
            for (star in 1..5) {
                TextButton(onClick = { rating = star }) {
                    Text(
                        if (star <= rating) "★" else "☆",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Text("How was it?")
        REVIEW_PHRASES.forEach { candidate ->
            OutlinedButton(onClick = { phrase = candidate }) {
                Text(if (phrase == candidate) "✓ $candidate" else candidate)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { navigator.pop() }) { Text("Cancel") }
            Button(
                enabled = rating > 0 && phrase.isNotEmpty(),
                onClick = { navigator.pop(ReviewPosted(rating, phrase)) },
            ) { Text("Post review") }
        }
    }
}

private val REVIEW_PHRASES = listOf(
    "My gosling approves",
    "Honk-worthy quality",
    "Feathers were ruffled",
)
