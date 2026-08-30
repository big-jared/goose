package dev.goose.sample.m2.cart.api

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import dev.goose.runtime.PopResult
import dev.goose.runtime.Screen
import dev.goose.runtime.ScreenTransitions
import dev.goose.runtime.ScreenWithResult
import kotlinx.serialization.Serializable

@Serializable
data object CartScreen : Screen

/**
 * The checkout flow — a nested wizard owned by the cart feature. Any feature can launch it via
 * this :api type (the catalog's "Buy now" does exactly that, cross-module) and await the result.
 */
@Serializable
data class CheckoutScreen(val itemId: String? = null) : ScreenWithResult<CheckoutResult>, ScreenTransitions {
    // The wizard presents modally: slides up over the cart, slides back down when it pops.
    override fun enterTransition() = slideInVertically { it } togetherWith fadeOut()

    override fun exitTransition() = fadeIn() togetherWith slideOutVertically { it }
}

@Serializable
data class CheckoutResult(val shippingAddress: String) : PopResult
