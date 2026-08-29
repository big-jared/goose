package dev.goose.sample.m3

import dev.goose.runtime.NavigatorHandle
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * The main flow's navigator handle, app-scoped so RETAINED legacy ViewModels can hold it across
 * activity recreation. MainActivity binds its (activity-scoped) FragmentNavigator into this
 * handle each onCreate and unbinds on destroy — the same rebinding pattern screenViewModel gives
 * migrated screens, applied to the legacy half.
 */
@ContributesTo(AppScope::class)
interface MainNavModule {
    val mainNavigatorHandle: NavigatorHandle

    companion object {
        @Provides
        @SingleIn(AppScope::class)
        fun provideMainNavigatorHandle(): NavigatorHandle = NavigatorHandle()
    }
}
