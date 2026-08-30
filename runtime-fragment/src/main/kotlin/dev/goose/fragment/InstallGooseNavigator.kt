package dev.goose.fragment

import androidx.activity.addCallback
import androidx.annotation.IdRes
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.goose.metro.GooseGraphHolder
import dev.goose.metro.GooseRuntimeAccessors
import dev.goose.runtime.Navigator
import dev.goose.runtime.NavigatorHandle

/**
 * Retains one [NavigatorHandle] per activity, in the activity's own ViewModelStore: it survives
 * rotation with the activity's retained state and dies when the activity finishes for real.
 */
internal class ActivityNavigatorHandleHolder : ViewModel() {
    val handle = NavigatorHandle()
}

/**
 * One-line setup for an activity whose FragmentManager still owns a back stack during migration.
 * Call from onCreate, after setContentView:
 * ```
 * class MainActivity : FragmentActivity(), FragmentNavigatorOwner {
 *     override lateinit var gooseNavigator: Navigator
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         setContentView(R.layout.activity_main)          // your existing layout
 *         gooseNavigator = installGooseNavigator(R.id.fragment_container)
 *     }
 * }
 * ```
 * What it does: builds a [FragmentNavigator] over this activity's FragmentManager and
 * [containerId], binds it into a per-activity retained [NavigatorHandle] (the stable object
 * ViewModels hold across rotation), and routes back presses through it so awaited results
 * resolve on system back. Rebinding on every onCreate is the point: the handle outlives the
 * activity, the navigator doesn't.
 *
 * Multiple activities: call this in each activity that owns a fragment stack. Every activity
 * gets its own independent handle and navigator; separate activities are separate navigation
 * roots, stacked by Android itself.
 */
fun FragmentActivity.installGooseNavigator(@IdRes containerId: Int): NavigatorHandle {
    val graph = (application as GooseGraphHolder).gooseGraph
    val handle = ViewModelProvider(this)[ActivityNavigatorHandleHolder::class.java].handle
    val navigator = FragmentNavigator(
        fragmentManager = supportFragmentManager,
        containerId = containerId,
        binders = (graph as GooseFragmentAccessors).fragmentBinders,
        resultRouter = (graph as GooseRuntimeAccessors).resultRouter,
    )
    handle.bind(navigator)
    onBackPressedDispatcher.addCallback(this) {
        if (supportFragmentManager.backStackEntryCount > 0) handle.pop() else finish()
    }
    return handle
}
