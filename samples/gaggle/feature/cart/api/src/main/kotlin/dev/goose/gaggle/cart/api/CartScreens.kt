package dev.goose.gaggle.cart.api

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import dev.goose.runtime.OverlayScreen
import dev.goose.runtime.PopResult
import dev.goose.runtime.Screen
import dev.goose.runtime.ScreenTransitions
import dev.goose.runtime.ScreenWithResult
import kotlinx.serialization.Serializable

@Serializable
data object CartScreen : Screen

/**
 * How other features (the catalog's product page) put things in this session's cart — and read
 * it back: [quantityOf] is backed by snapshot state, so a composable reading it recomposes as
 * the cart changes (the add-to-cart button flips to "In cart" by observing, not by guessing).
 */
interface CartMutator {
    fun add(productId: String, name: String, unitPriceCents: Int)
    fun quantityOf(productId: String): Int
}

/** The checkout wizard: slides up modally (ScreenTransitions), answers with a typed result. */
@Serializable
data object CheckoutScreen : ScreenWithResult<CheckoutResult>, ScreenTransitions {
    override fun enterTransition() = slideInVertically { it } togetherWith fadeOut()
    override fun exitTransition() = fadeIn() togetherWith slideOutVertically { it }
}

@Serializable
data class CheckoutResult(val shippingAddress: String, val itemCount: Int) : PopResult

/** A picker screen: a question with a typed answer. */
@Serializable
data object PickAddressScreen : ScreenWithResult<PickedAddress>

@Serializable
data class PickedAddress(val line: String) : PopResult

/** A confirmation dialog: an OverlayScreen answering goToForResult. */
@Serializable
data class RemoveItemScreen(val name: String) : OverlayScreen, ScreenWithResult<RemoveConfirmed>

@Serializable
data class RemoveConfirmed(val remove: Boolean) : PopResult
