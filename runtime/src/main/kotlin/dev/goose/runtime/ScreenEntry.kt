package dev.goose.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * The single unit a feature contributes per screen: how to render it (and, inside, how to obtain
 * its presenter). Contributed into a map keyed by the screen's class via Metro's
 * `@ContributesIntoMap` + `@ClassKey`.
 */
fun interface ScreenEntry {
    @Composable
    fun Content(screen: Screen, modifier: Modifier)
}

/**
 * The navigator owning the stack this screen sits on. Set by hosts ([ScreenNavDisplay],
 * ScreenFragment); read by presenter helpers so screens never plumb navigators manually.
 */
val LocalNavigator = staticCompositionLocalOf<Navigator> {
    error("No Navigator provided. Screens must be hosted by a Goose host (ScreenNavDisplay or ScreenFragment).")
}
