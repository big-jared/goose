package dev.goose.sample.m1.profile.api

import dev.goose.runtime.PopResult
import dev.goose.runtime.ScreenWithResult
import kotlinx.serialization.Serializable

/** Opens a user's profile; answers with [ProfileResult] when the visitor is done. */
@Serializable
data class ProfileScreen(val userId: String) : ScreenWithResult<ProfileResult>

@Serializable
data class ProfileResult(val followed: Boolean) : PopResult
