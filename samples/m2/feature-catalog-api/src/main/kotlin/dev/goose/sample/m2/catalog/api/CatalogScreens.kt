package dev.goose.sample.m2.catalog.api

import dev.goose.runtime.Screen
import kotlinx.serialization.Serializable

@Serializable
data object CatalogScreen : Screen

@Serializable
data class ItemDetailScreen(val itemId: String) : Screen

/**
 * Shared-element keys live in the :api module so the grid and the detail can animate the same
 * element even if they ever live in different feature modules — sharing a key never requires an
 * impl→impl dependency.
 */
object CatalogSharedKeys {
    fun itemSwatch(itemId: String): String = "catalog:swatch:$itemId"
}
