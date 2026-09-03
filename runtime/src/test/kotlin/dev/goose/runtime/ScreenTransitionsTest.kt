package dev.goose.runtime

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** A screen customizing only its pop, to observe what the predictive-back default falls back to. */
private class PopOnlyTransitions : ScreenTransitions {
    val sentinel: ContentTransform = fadeIn() togetherWith fadeOut()

    override fun exitTransition(): ContentTransform = sentinel
}

/**
 * The slide-transition direction contract: [rememberSlideScreenTransitions]' non-composable core
 * picks the LTR object or its RTL mirror by layout direction, and the offset helpers keep the
 * platform-conventional geometry — the entering screen starts a full width toward the push origin
 * (right under LTR, left under RTL) while the screen beneath drifts a third of the width the
 * opposite way. Also pins the [ScreenTransitions] defaults: everything null except
 * [ScreenTransitions.predictivePopTransition], which previews [ScreenTransitions.exitTransition].
 */
class ScreenTransitionsTest {

    private val width = 300

    @Test
    fun slideScreenTransitionsForLtrReturnsTheLtrObject() {
        assertSame(SlideScreenTransitions, slideScreenTransitionsFor(LayoutDirection.Ltr))
    }

    @Test
    fun slideScreenTransitionsForRtlReturnsTheMirroredObject() {
        assertSame(RtlSlideScreenTransitions, slideScreenTransitionsFor(LayoutDirection.Rtl))
    }

    @Test
    fun enterOffsetIsAFullWidthTowardThePushOrigin() {
        assertEquals(width, slideEnterOffset(LayoutDirection.Ltr, width))
        assertEquals(-width, slideEnterOffset(LayoutDirection.Rtl, width))
    }

    @Test
    fun driftOffsetIsAThirdOfTheWidthOppositeThePushOrigin() {
        assertEquals(-width / 3, slideDriftOffset(LayoutDirection.Ltr, width))
        assertEquals(width / 3, slideDriftOffset(LayoutDirection.Rtl, width))
    }

    @Test
    fun driftIsAlwaysOppositeInSignAndAThirdOfEnter() {
        for (direction in LayoutDirection.entries) {
            val enter = slideEnterOffset(direction, width)
            val drift = slideDriftOffset(direction, width)
            assertEquals("direction=$direction", -enter / 3, drift)
        }
    }

    @Test
    fun slideObjectsAlwaysSupplyBothTransitions() {
        assertNotNull(SlideScreenTransitions.enterTransition())
        assertNotNull(SlideScreenTransitions.exitTransition())
        assertNotNull(RtlSlideScreenTransitions.enterTransition())
        assertNotNull(RtlSlideScreenTransitions.exitTransition())
    }

    @Test
    fun predictivePopDefaultsToTheExitTransition() {
        val transitions = PopOnlyTransitions()

        // A screen customizing its pop previews the same motion under the back gesture.
        assertSame(transitions.sentinel, transitions.predictivePopTransition(swipeEdge = 0))
        assertSame(transitions.sentinel, transitions.predictivePopTransition(swipeEdge = 1))
    }

    @Test
    fun predictivePopIsNullWhenTheExitTransitionIsNull() {
        val defaults = object : ScreenTransitions {}

        assertNull(defaults.exitTransition())
        assertNull(defaults.predictivePopTransition(swipeEdge = 0))
    }
}
