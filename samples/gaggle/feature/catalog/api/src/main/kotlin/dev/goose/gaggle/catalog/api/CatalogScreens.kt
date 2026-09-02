package dev.goose.gaggle.catalog.api

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.window.DialogProperties
import dev.goose.runtime.OverlayScreen
import dev.goose.runtime.PopResult
import dev.goose.runtime.Screen
import dev.goose.runtime.ScreenTransitions
import dev.goose.runtime.ScreenWithResult
import kotlinx.serialization.Serializable

@Serializable
data object CatalogScreen : Screen

/**
 * Product detail. Related products push MORE ProductScreens: stacking the same screen type.
 * No ScreenTransitions here: the host's side-to-side default slides the page while the hero
 * content (emoji + title) travels via shared elements over the slide.
 */
@Serializable
data class ProductScreen(val productId: String) : Screen

/**
 * A quick-look dialog: a full OverlayScreen with custom window properties — edge-to-edge width
 * (the content decides its own size) instead of the platform default dialog width.
 */
@Serializable
data class ProductPeekScreen(val productId: String) : OverlayScreen {
    override fun dialogProperties() = DialogProperties(usePlatformDefaultWidth = false)
}

/**
 * The write-a-review form: slides up modally (ScreenTransitions) over the product page and
 * answers its caller with a typed result. The product screen owns the repository write; this
 * screen only asks the question.
 */
@Serializable
data class WriteReviewScreen(val productId: String) : ScreenWithResult<ReviewPosted>, ScreenTransitions {
    override fun enterTransition() = slideInVertically(tween(250)) { it } togetherWith fadeOut(tween(250))
    override fun exitTransition() = fadeIn(tween(250)) togetherWith slideOutVertically(tween(250)) { it }
}

@Serializable
data class ReviewPosted(val rating: Int, val text: String) : PopResult

/**
 * Shared-element keys, declared in :api so the list and detail screens (and any other feature)
 * can animate the same element without depending on each other's impls. Two keys per product:
 * the emoji hero and the title travel independently during the transition.
 */
data class ProductImageKey(val productId: String)

data class ProductTitleKey(val productId: String)
