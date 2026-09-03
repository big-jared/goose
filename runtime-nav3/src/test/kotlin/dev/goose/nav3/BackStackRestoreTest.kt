package dev.goose.nav3

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import androidx.savedstate.serialization.encodeToSavedState
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.goose.metro.Goose
import dev.goose.runtime.Screen
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.plus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
 *
 * Two stack shapes round-trip here. The PRODUCTION shape is what rememberGooseBackStack persists:
 * every screen wrapped in a [PushedScreen] record, decoded with the app module plus
 * [PushRecordSerializers]. The RAW shape (bare screens as NavKeys) stays supported for hosts that
 * seed or mutate a stack directly, per [asScreen]'s tolerance.
 */
@RunWith(AndroidJUnit4::class)
class BackStackRestoreTest {

    @Serializable
    data class NestedScreen(val id: String) : Screen

    /** The exact module composition rememberGooseBackStack builds its Saver with. */
    private val configuration = SavedStateConfiguration {
        serializersModule = Goose.Builder().build().navSerializersModule +
            PushRecordSerializers.pushRecordSerializers()
    }

    private fun roundTrip(vararg keys: NavKey): NavBackStack<NavKey>? {
        val saved = encodeToSavedState(
            navBackStackSerializer,
            NavBackStack(*keys),
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

    @Test
    fun pushedRecords_roundTripToEqualRecordsAndScreens() {
        val original = listOf(RestoreTestScreen("a").pushed(), NestedScreen("n").pushed())

        val restored = roundTrip(*original.toTypedArray<NavKey>())

        // Whole records survive — same pushIds — so entry identity outlives process death.
        assertEquals(original, restored?.toList())
        assertEquals(listOf(RestoreTestScreen("a"), NestedScreen("n")), restored?.map { it.asScreen() })
    }

    @Test
    fun equalScreensPushedTwice_stayDistinctEntriesAfterRestore() {
        val restored = roundTrip(RestoreTestScreen("dup").pushed(), RestoreTestScreen("dup").pushed())

        val keys = restored?.toList().orEmpty()
        assertEquals(2, keys.size)
        // The screens are equal values, but the push records must not collapse: each keeps its
        // own NavEntry identity (saveable state, ViewModel store) across recreation.
        assertNotEquals(keys[0], keys[1])
        assertEquals(RestoreTestScreen("dup"), keys[0].asScreen())
        assertEquals(RestoreTestScreen("dup"), keys[1].asScreen())
    }

    @Test
    fun pushedRecordAroundARemovedClass_stillRestartsFreshInsteadOfCrashing() {
        val restored = roundTrip(RestoreTestScreen("a").pushed(), RemovedLikeScreen("gone").pushed())
        assertNull(restored)
    }
}
