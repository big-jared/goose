package dev.goose.gaggle.catalog.api

import dev.goose.runtime.Screen
import kotlinx.serialization.Serializable

@Serializable
data object CatalogScreen : Screen

/** Product detail. Related products push MORE ProductScreens: stacking the same screen type. */
@Serializable
data class ProductScreen(val productId: String) : Screen

/**
 * Shared-element key, declared in :api so the list and detail screens (and any other feature)
 * can animate the same element without depending on each other's impls.
 */
data class ProductImageKey(val productId: String)
