package dev.goose.sample.m1.home.api

import dev.goose.runtime.Screen
import kotlinx.serialization.Serializable

@Serializable
data object HomeScreen : Screen

/** A pure-compose screen: no Mavericks, presented by a StateHolder (see StatsUi). */
@Serializable
data object TeamStatsScreen : Screen
