package dev.goose.fragment

import androidx.activity.addCallback
import androidx.annotation.IdRes
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.goose.metro.GooseGraphHolder
import dev.goose.metro.asGoose
import dev.goose.runtime.Navigator
import dev.goose.runtime.NavigatorHandle
import java.util.UUID
import kotlin.reflect.KClass

/**
 * Retains one [NavigatorHandle] per activity, in the activity's own ViewModelStore: it survives
 * rotation with the activity's retained state and dies when the activity finishes for real.
 */
internal class ActivityNavigatorHandleHolder : ViewModel() {
    val handle = NavigatorHandle()

    /** Rotation-stable identity for this activity's stack, scoping its result routing. */
    val stackTag: String = UUID.randomUUID().toString()
    var installed = false
}

/**
 * One-line setup for an activity whose FragmentManager still owns a back stack during migration.
 * Call from onCreate, after setContentView:
 * ```
 * class MainActivity : FragmentActivity() {
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         setContentView(R.layout.activity_main)          // your existing layout
 *         installGooseNavigator(R.id.fragment_container)
 *     }
 * }
 * ```
 * Afterwards the navigator is available anywhere as `activity.gooseNavigator` (and to migrated
 * screens automatically). Implement [FragmentNavigatorOwner] only if you need custom routing.
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
fun FragmentActivity.installGooseNavigator(
    @IdRes containerId: Int,
    /** The stack's owner; pass a child fragment's childFragmentManager for nested ownership. */
    fragmentManager: FragmentManager = supportFragmentManager,
    /**
     * Host-wide transaction policy for screens without a per-screen contributed override; call
     * `request.performDefaultTransaction()` for the ones you don't customize. Null keeps the
     * built-in replace+addToBackStack for everything.
     */
    defaultNavigation: FragmentScreenNavigation? = null,
    /**
     * The fragment class hosting MIGRATED screens, replacing the default ScreenFragment. Your
     * class extends your own base and renders with `gooseScreenView()`; it is instantiated
     * through the FragmentManager's FragmentFactory, same as on recreation:
     * ```
     * installGooseNavigator(R.id.container, screenHost = GaggleScreenFragment::class)
     * ```
     */
    screenHost: KClass<out Fragment> = ScreenFragment::class,
    /**
     * The DialogFragment hosting migrated screens with the [dev.goose.runtime.Overlay] facet
     * (an `OverlayScreen`, or a screen whose Presentation carries the facet), replacing the
     * default [ScreenDialogFragment] — the dialog analogue of [screenHost].
     */
    dialogHost: KClass<out DialogFragment> = ScreenDialogFragment::class,
): NavigatorHandle {
    val graph = (application as GooseGraphHolder).gooseGraph
    val holder = ViewModelProvider(this)[ActivityNavigatorHandleHolder::class.java]
    holder.installed = true
    val handle = holder.handle
    // A graph without GooseFragmentAccessors (a bare hand-built Goose, say) just has no
    // legacy binders or overrides — migrated screens still host in ScreenFragments fine.
    val fragmentAccessors = graph as? GooseFragmentAccessors
    val navigator = FragmentNavigator(
        fragmentManager = fragmentManager,
        containerId = containerId,
        binders = fragmentAccessors?.fragmentBinders ?: emptyMap(),
        resultRouter = graph.asGoose().resultRouter,
        navigationOverrides = fragmentAccessors?.fragmentNavigationOverrides ?: emptyMap(),
        presentationNavigations = fragmentAccessors?.presentationNavigations ?: emptyMap(),
        stackTag = holder.stackTag,
        defaultNavigation = defaultNavigation,
        screenHost = screenHost,
        dialogHost = dialogHost,
    )
    handle.bind(navigator)
    onBackPressedDispatcher.addCallback(this) {
        if (fragmentManager.backStackEntryCount > 0) handle.pop() else finish()
    }
    return handle
}

/**
 * The activity's installed navigator. Legacy code (fragment click listeners, hand-wired
 * Mavericks companion factories) navigates through this:
 * ```
 * requireActivity().gooseNavigator.goTo(ProfileScreen(user.id))
 * ```
 */
val FragmentActivity.gooseNavigator: Navigator
    get() {
        (this as? FragmentNavigatorOwner)?.let { return it.gooseNavigator }
        val holder = ViewModelProvider(this)[ActivityNavigatorHandleHolder::class.java]
        check(holder.installed) {
            "installGooseNavigator(containerId) was not called in ${this::class.simpleName}'s onCreate."
        }
        return holder.handle
    }
