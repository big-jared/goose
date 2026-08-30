package dev.goose.nav3

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import androidx.savedstate.serialization.encodeToSavedState
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.goose.metro.GooseRuntimeAccessors
import dev.goose.runtime.Screen
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@Serializable
data class RestoreTestScreen(val id: String) : Screen

/** Simulates a screen whose class no longer exists in this release. */
@Serializable
@SerialName("dev.goose.gone.RemovedInThisRelease")
data class RemovedLikeScreen(val id: String) : Screen

/**
 * The release-over-release restoration contract: a saved stack decodes when its classes still
 * exist (including nested ones), and degrades to "restart fresh" (null) instead of crashing when
 * one does not.
 */
@RunWith(AndroidJUnit4::class)
class BackStackRestoreTest {

    @Serializable
    data class NestedScreen(val id: String) : Screen

    private val configuration = SavedStateConfiguration {
        serializersModule = GooseRuntimeAccessors.provideNavSerializersModule(emptySet())
    }

    private fun roundTrip(vararg screens: Screen): NavBackStack<NavKey>? {
        val saved = encodeToSavedState(
            navBackStackSerializer,
            NavBackStack(*screens.toList().toTypedArray<NavKey>()),
            configuration,
        )
        return decodeBackStackOrNull(saved, configuration)
    }

    @Test
    fun savedStackRestores_withNoExplicitRegistration() {
        val restored = roundTrip(RestoreTestScreen("a"), RestoreTestScreen("b"))
        assertEquals(listOf(RestoreTestScreen("a"), RestoreTestScreen("b")), restored?.toList())
    }

    @Test
    fun nestedScreenClassRestores() {
        // Default serial names are dot-separated; JVM binary names of nested classes use '$'.
        val restored = roundTrip(RestoreTestScreen("a"), NestedScreen("n"))
        assertEquals(listOf(RestoreTestScreen("a"), NestedScreen("n")), restored?.toList())
    }

    @Test
    fun unknownScreenClass_restartsFreshInsteadOfCrashing() {
        val restored = roundTrip(RestoreTestScreen("a"), RemovedLikeScreen("gone"))
        assertNull(restored)
    }
}
