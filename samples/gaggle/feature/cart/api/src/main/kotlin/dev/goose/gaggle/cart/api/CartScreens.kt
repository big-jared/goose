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
data object CartScreen : Screen {
    private fun readResolve(): Any = CartScreen
}

/** How other features (the catalog's product page) put things in this session's cart. */
interface CartMutator {
    fun add(productId: String, name: String)
}

/** The checkout wizard: slides up modally (ScreenTransitions), answers with a typed result. */
@Serializable
data object CheckoutScreen : ScreenWithResult<CheckoutResult>, ScreenTransitions {
    override fun enterTransition() = slideInVertically { it } togetherWith fadeOut()
    override fun exitTransition() = fadeIn() togetherWith slideOutVertically { it }

    private fun readResolve(): Any = CheckoutScreen
}

@Serializable
data class CheckoutResult(val shippingAddress: String, val itemCount: Int) : PopResult

/** A picker screen: a question with a typed answer. */
@Serializable
data object PickAddressScreen : ScreenWithResult<PickedAddress> {
    private fun readResolve(): Any = PickAddressScreen
}

@Serializable
data class PickedAddress(val line: String) : PopResult

/** A confirmation dialog: an OverlayScreen answering goToForResult. */
@Serializable
data class RemoveItemScreen(val name: String) : OverlayScreen, ScreenWithResult<RemoveConfirmed>

@Serializable
data class RemoveConfirmed(val remove: Boolean) : PopResult
