package dev.goose.nav3

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import dev.goose.runtime.Overlay
import dev.goose.runtime.OverlayScreen
import dev.goose.runtime.Presentation
import dev.goose.runtime.PresentedScreen
import dev.goose.runtime.Screen
import dev.goose.runtime.ScreenTransitions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

private data object PlainTestScreen : Screen

private class OverlayTestScreen(private val properties: DialogProperties) : OverlayScreen {
    override fun dialogProperties(): DialogProperties = properties
}

/** A screen that declares its own motion — the "screen wins over the host default" case. */
private class SelfAnimatedScreen(
    private val enter: ContentTransform?,
    private val exit: ContentTransform?,
) : Screen, ScreenTransitions {
    override fun enterTransition(): ContentTransform? = enter

    override fun exitTransition(): ContentTransform? = exit
}

private class FixedTransitions(
    private val enter: ContentTransform?,
    private val exit: ContentTransform?,
) : ScreenTransitions {
    override fun enterTransition(): ContentTransform? = enter

    override fun exitTransition(): ContentTransform? = exit
}

/** An app-defined presentation vocabulary object carrying the motion facet. */
private class AnimatedPresentation(
    private val enter: ContentTransform?,
    private val exit: ContentTransform?,
) : Presentation, ScreenTransitions {
    override fun enterTransition(): ContentTransform? = enter

    override fun exitTransition(): ContentTransform? = exit
}

/** An app-defined presentation carrying the dialog facet. */
private class OverlayPresentation(private val properties: DialogProperties) : Presentation, Overlay {
    override fun dialogProperties(): DialogProperties = properties
}

private class PresentedTestScreen(override val presentation: Presentation) : PresentedScreen

/** Declares its own motion AND a presentation — the "screen beats its presentation" case. */
private class SelfAnimatedPresentedScreen(
    private val enter: ContentTransform?,
    override val presentation: Presentation,
) : PresentedScreen, ScreenTransitions {
    override fun enterTransition(): ContentTransform? = enter
}

/**
 * Pins every branch of [entryMetadata], the pure metadata core of the entryProvider:
 *
 * - Roots always crossfade at [ROOT_FADE_MS] on all three NavDisplay spec keys, ignoring both
 *   the host default and dialog rendering (root overlays degrade to full-screen).
 * - Non-root overlays carry DialogSceneStrategy metadata with the screen's own properties.
 * - Non-root motion resolves screen-declared transitions first, then the host default, then
 *   nothing; a null per-direction transition omits that key, but the predictive key is always
 *   present once any transitions apply, falling back to a plain crossfade during the drag.
 *
 * The metadata map's string keys are NavDisplay/DialogSceneStrategy implementation details, so
 * the tests discover them from the framework builders instead of hardcoding them.
 */
class EntryMetadataTest {

    private val transitionKey = NavDisplay.transitionSpec { null }.keys.single()
    private val popKey = NavDisplay.popTransitionSpec { null }.keys.single()
    private val predictiveKey = NavDisplay.predictivePopTransitionSpec { null }.keys.single()
    private val dialogKey = DialogSceneStrategy.dialog().keys.single()

    // BackEventCompat.EDGE_LEFT; the value only flows through to the screen's own function.
    private val edgeLeft = 0

    /**
     * NavDisplay spec values are receiver lambdas over an AnimatedContentTransitionScope that
     * entryMetadata's specs never read; a proxy scope (screaming if touched) lets the tests
     * invoke them outside a real transition and observe the produced transform.
     */
    private val scope: Any = Proxy.newProxyInstance(
        AnimatedContentTransitionScope::class.java.classLoader,
        arrayOf(AnimatedContentTransitionScope::class.java),
    ) { _, method, _ -> error("entryMetadata specs must not read the transition scope: ${method.name}") }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any>.transformAt(key: String): ContentTransform? =
        (getValue(key) as Function1<Any?, ContentTransform?>).invoke(scope)

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any>.predictiveTransformAt(key: String, edge: Int): ContentTransform? =
        (getValue(key) as Function2<Any?, Int, ContentTransform?>).invoke(scope, edge)

    @Test
    fun rootCarriesTheCrossfadeOnAllThreeSpecKeys() {
        val metadata = entryMetadata(PlainTestScreen, isRoot = true, defaultTransitions = null)

        assertEquals(setOf(transitionKey, popKey, predictiveKey), metadata.keys)
        for (transform in listOf(
            metadata.transformAt(transitionKey),
            metadata.transformAt(popKey),
            metadata.predictiveTransformAt(predictiveKey, edgeLeft),
        )) {
            assertNotNull(transform)
            assertEquals(fadeIn(tween(ROOT_FADE_MS)), transform?.targetContentEnter)
            assertEquals(fadeOut(tween(ROOT_FADE_MS)), transform?.initialContentExit)
        }
    }

    @Test
    fun rootOverlayDegradesToFullScreen_noDialogKey() {
        val overlay = OverlayTestScreen(DialogProperties())

        val metadata = entryMetadata(overlay, isRoot = true, defaultTransitions = null)

        assertFalse(dialogKey in metadata)
        assertEquals(setOf(transitionKey, popKey, predictiveKey), metadata.keys)
    }

    @Test
    fun rootIgnoresTheHostDefaultTransitions() {
        val default = FixedTransitions(
            enter = fadeIn(tween(999)) togetherWith fadeOut(tween(999)),
            exit = fadeIn(tween(999)) togetherWith fadeOut(tween(999)),
        )

        val metadata = entryMetadata(PlainTestScreen, isRoot = true, defaultTransitions = default)

        assertEquals(fadeIn(tween(ROOT_FADE_MS)), metadata.transformAt(transitionKey)?.targetContentEnter)
    }

    @Test
    fun nonRootOverlayGetsDialogMetadataWithTheScreensProperties() {
        val properties = DialogProperties(dismissOnBackPress = false)
        val overlay = OverlayTestScreen(properties)

        val metadata = entryMetadata(overlay, isRoot = false, defaultTransitions = null)

        assertSame(properties, metadata[dialogKey])
    }

    @Test
    fun screensOwnTransitionsWinOverTheDefault() {
        val screenEnter = fadeIn(tween(111)) togetherWith fadeOut(tween(111))
        val screenExit = fadeIn(tween(222)) togetherWith fadeOut(tween(222))
        val screen = SelfAnimatedScreen(screenEnter, screenExit)
        val default = FixedTransitions(
            enter = fadeIn(tween(999)) togetherWith fadeOut(tween(999)),
            exit = fadeIn(tween(888)) togetherWith fadeOut(tween(888)),
        )

        val metadata = entryMetadata(screen, isRoot = false, defaultTransitions = default)

        assertSame(screenEnter, metadata.transformAt(transitionKey))
        assertSame(screenExit, metadata.transformAt(popKey))
        // The predictive default previews the screen's own pop, not the host default's.
        assertSame(screenExit, metadata.predictiveTransformAt(predictiveKey, edgeLeft))
    }

    @Test
    fun plainScreenFallsBackToTheDefaultTransitions() {
        val defaultEnter = fadeIn(tween(333)) togetherWith fadeOut(tween(333))
        val defaultExit = fadeIn(tween(444)) togetherWith fadeOut(tween(444))
        val default = FixedTransitions(defaultEnter, defaultExit)

        val metadata = entryMetadata(PlainTestScreen, isRoot = false, defaultTransitions = default)

        assertSame(defaultEnter, metadata.transformAt(transitionKey))
        assertSame(defaultExit, metadata.transformAt(popKey))
    }

    @Test
    fun plainScreenWithNoDefaultHasNoKeysAtAll() {
        val metadata = entryMetadata(PlainTestScreen, isRoot = false, defaultTransitions = null)

        assertEquals(emptyMap<String, Any>(), metadata)
    }

    @Test
    fun presentationTransitionsBeatTheHostDefault() {
        val sheetEnter = fadeIn(tween(111)) togetherWith fadeOut(tween(111))
        val sheetExit = fadeIn(tween(222)) togetherWith fadeOut(tween(222))
        val screen = PresentedTestScreen(AnimatedPresentation(sheetEnter, sheetExit))
        val default = FixedTransitions(
            enter = fadeIn(tween(999)) togetherWith fadeOut(tween(999)),
            exit = fadeIn(tween(888)) togetherWith fadeOut(tween(888)),
        )

        val metadata = entryMetadata(screen, isRoot = false, defaultTransitions = default)

        assertSame(sheetEnter, metadata.transformAt(transitionKey))
        assertSame(sheetExit, metadata.transformAt(popKey))
    }

    @Test
    fun screensOwnTransitionsBeatItsPresentations() {
        val ownEnter = fadeIn(tween(111)) togetherWith fadeOut(tween(111))
        val presentation = AnimatedPresentation(
            enter = fadeIn(tween(999)) togetherWith fadeOut(tween(999)),
            exit = null,
        )

        val metadata = entryMetadata(
            SelfAnimatedPresentedScreen(ownEnter, presentation),
            isRoot = false,
            defaultTransitions = null,
        )

        assertSame(ownEnter, metadata.transformAt(transitionKey))
    }

    @Test
    fun presentationOverlayRendersAsDialogWithItsProperties() {
        val properties = DialogProperties(dismissOnClickOutside = false)
        val screen = PresentedTestScreen(OverlayPresentation(properties))

        val metadata = entryMetadata(screen, isRoot = false, defaultTransitions = null)

        assertSame(properties, metadata[dialogKey])
    }

    @Test
    fun rootPresentationOverlayDegradesToFullScreenToo() {
        val screen = PresentedTestScreen(OverlayPresentation(DialogProperties()))

        val metadata = entryMetadata(screen, isRoot = true, defaultTransitions = null)

        assertFalse(dialogKey in metadata)
    }

    @Test
    fun nullEnterOmitsTheKey_predictiveStaysWithACrossfadeFallback() {
        val screen = SelfAnimatedScreen(enter = null, exit = null)

        val metadata = entryMetadata(screen, isRoot = false, defaultTransitions = null)

        assertFalse(transitionKey in metadata)
        assertFalse(popKey in metadata)
        assertTrue(predictiveKey in metadata)
        // predictivePopTransition defaults to exitTransition (null here): the drag preview
        // degrades to a plain crossfade instead of returning null to NavDisplay.
        val preview = metadata.predictiveTransformAt(predictiveKey, edgeLeft)
        assertEquals(fadeIn(), preview?.targetContentEnter)
        assertEquals(fadeOut(), preview?.initialContentExit)
    }
}
