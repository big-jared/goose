package dev.goose.gaggle.catalog.api

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.window.DialogProperties
import dev.goose.runtime.OverlayScreen
import dev.goose.runtime.Screen
import dev.goose.runtime.ScreenTransitions
import kotlinx.serialization.Serializable

@Serializable
data object CatalogScreen : Screen {
    private fun readResolve(): Any = CatalogScreen
}

/**
 * Product detail. Related products push MORE ProductScreens: stacking the same screen type.
 *
 * The transitions are a gentle fade + scale rather than the default slide: the hero content
 * (emoji + title) travels via shared elements, and a screen-level slide would fight the
 * shared bounds animation. The container stays still; the content does the moving.
 */
@Serializable
data class ProductScreen(val productId: String) : Screen, ScreenTransitions {
    override fun enterTransition() =
        (fadeIn(tween(220)) + scaleIn(initialScale = 0.94f, animationSpec = tween(220)))
            .togetherWith(fadeOut(tween(220)))

    override fun exitTransition() =
        fadeIn(tween(220))
            .togetherWith(fadeOut(tween(220)) + scaleOut(targetScale = 0.94f, animationSpec = tween(220)))
}

/**
 * A quick-look dialog: a full OverlayScreen with custom window properties — edge-to-edge width
 * (the content decides its own size) instead of the platform default dialog width.
 */
@Serializable
data class ProductPeekScreen(val productId: String) : OverlayScreen {
    override fun dialogProperties() = DialogProperties(usePlatformDefaultWidth = false)
}

/**
 * Shared-element keys, declared in :api so the list and detail screens (and any other feature)
 * can animate the same element without depending on each other's impls. Two keys per product:
 * the emoji hero and the title travel independently during the transition.
 */
data class ProductImageKey(val productId: String)

data class ProductTitleKey(val productId: String)
