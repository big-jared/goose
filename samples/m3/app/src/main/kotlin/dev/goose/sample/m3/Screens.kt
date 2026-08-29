package dev.goose.sample.m3

import dev.goose.runtime.PopResult
import dev.goose.runtime.Screen
import dev.goose.runtime.ScreenWithResult
import kotlinx.serialization.Serializable

/** Still implemented by a LEGACY fragment; compose callers can't tell. */
@Serializable
data class DetailScreen(val id: String) : ScreenWithResult<DetailResult>

@Serializable
data class DetailResult(val message: String) : PopResult

/** Already MIGRATED to a compose ScreenEntry; fragment callers can't tell. */
@Serializable
data class ProfileScreen(val userId: String) : ScreenWithResult<ProfileResult>

@Serializable
data class ProfileResult(val counterAtClose: Int) : PopResult

/** Root of the fully-converted settings flow (Nav3-owned stack). */
@Serializable
data object SettingsHomeScreen : Screen
