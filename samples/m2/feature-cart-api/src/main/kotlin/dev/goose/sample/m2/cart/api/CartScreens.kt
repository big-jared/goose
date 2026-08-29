package dev.goose.sample.m2.cart.api

import dev.goose.runtime.PopResult
import dev.goose.runtime.Screen
import dev.goose.runtime.ScreenWithResult
import kotlinx.serialization.Serializable

@Serializable
data object CartScreen : Screen

/**
 * The checkout flow — a nested wizard owned by the cart feature. Any feature can launch it via
 * this :api type (the catalog's "Buy now" does exactly that, cross-module) and await the result.
 */
@Serializable
data class CheckoutScreen(val itemId: String? = null) : ScreenWithResult<CheckoutResult>

@Serializable
data class CheckoutResult(val shippingAddress: String) : PopResult
