package dev.goose.nav3

import androidx.navigation3.runtime.NavKey
import dev.goose.runtime.Screen
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * PER-PUSH identity, separate from screen equality: every push wraps the screen in a record
 * with a unique id, so two pushes of EQUAL screen values are two distinct NavEntry keys — with
 * independent entry state, saveable state, and screen-scoped ViewModels — while the screen's
 * own equality and serialized payload stay exactly the screen's data. The record serializes
 * with the back stack, so the association survives recreation and process death.
 *
 * Internal by design: every boundary that hands a screen to user code (registry lookup,
 * ViewModel args, results, [dev.goose.runtime.Navigator.backStack]) unwraps first.
 */
@Serializable
internal data class PushedScreen(
    val pushId: String,
    val screen: Screen,
) : NavKey

internal fun Screen.pushed(): PushedScreen = PushedScreen(UUID.randomUUID().toString(), this)

/** Tolerates raw screens too, for hosts that seed or mutate a stack directly. */
internal fun NavKey.asScreen(): Screen = when (this) {
    is PushedScreen -> screen
    is Screen -> this
    else -> error("Non-Screen NavKey on a Goose back stack: $this")
}

@ContributesTo(AppScope::class)
interface PushRecordSerializers {
    companion object {
        @Provides
        @IntoSet
        fun pushRecordSerializers(): SerializersModule = SerializersModule {
            polymorphic(NavKey::class) { subclass(PushedScreen::class) }
        }
    }
}
