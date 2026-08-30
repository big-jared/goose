package dev.goose.fragment

import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import dev.goose.runtime.BaseNavigator
import dev.goose.runtime.Navigator
import dev.goose.runtime.PopResult
import dev.goose.runtime.ResultRouter
import dev.goose.runtime.Screen
import kotlin.reflect.KClass

/**
 * Maps a [Screen] to the legacy [Fragment] that still implements it. Contributed per screen:
 * ```
 * @ContributesIntoMap(AppScope::class)
 * @ClassKey(DetailScreen::class)
 * @Inject
 * class DetailFragmentBinder : ScreenFragmentBinder {
 *   override fun createFragment(screen: Screen) = DetailFragment.newInstance(screen as DetailScreen)
 * }
 * ```
 */
fun interface ScreenFragmentBinder {
    fun createFragment(screen: Screen): Fragment
}

/**
 * Full-control navigation for one screen on the fragment host, for when the default
 * replace+addToBackStack transaction isn't right: show a DialogFragment, use custom animations,
 * hand off to an existing navigation framework, or start an activity. Contribute keyed by
 * screen class:
 * ```
 * @ContributesIntoMap(AppScope::class, binding = binding<FragmentScreenNavigation>())
 * @ClassKey(HelpScreen::class)
 * @Inject
 * class HelpNavigation : FragmentScreenNavigation {
 *   override fun navigate(request: FragmentNavigationRequest) {
 *     HelpDialogFragment().show(request.fragmentManager, "help")
 *   }
 * }
 * ```
 * If the destination is pushed onto the FragmentManager back stack, use
 * [FragmentNavigationRequest.backStackEntryName] as the `addToBackStack` name so awaited results
 * resolve when it pops. If it bypasses the back stack (a dialog, an activity), answer awaiting
 * callers with [FragmentNavigationRequest.deliverResult].
 */
fun interface FragmentScreenNavigation {
    fun navigate(request: FragmentNavigationRequest)
}

/** Everything a [FragmentScreenNavigation] needs to execute one navigation. */
class FragmentNavigationRequest internal constructor(
    val screen: Screen,
    val fragmentManager: FragmentManager,
    @param:IdRes val containerId: Int,
    /** The back stack entry name results ride on; pass to addToBackStack for awaited screens. */
    val backStackEntryName: String,
    private val resultRouter: ResultRouter,
) {
    /** For destinations that bypass the back stack: answer a caller awaiting this screen. */
    fun deliverResult(result: PopResult?) = resultRouter.complete(backStackEntryName, result)
}

/**
 * A [Navigator] over a FragmentManager back stack — the legacy half of a migration. ViewModels
 * navigating through this cannot tell it apart from a Nav3 host, which is the whole point:
 * migrating a screen from fragment-hosted to compose-hosted swaps the host, not the VM.
 *
 * The FragmentManager is the single source of truth. Result delivery rides the back stack entry
 * NAME (the awaited screen instance's result key) and is performed by a back-stack-changed
 * listener, so it fires no matter who popped: [pop], system back, or legacy code calling
 * `popBackStack()` directly — an awaited screen dismissed by ANY path resumes its caller.
 *
 * Screens whose fragment is a migrated compose screen need no binder: anything unmapped is hosted
 * in a [ScreenFragment] automatically.
 *
 * [backStack] cannot reconstruct [Screen] instances from a restored FragmentManager and is
 * intentionally empty here; mid-migration callers should not introspect the legacy stack.
 */
class FragmentNavigator(
    private val fragmentManager: FragmentManager,
    @param:IdRes private val containerId: Int,
    private val binders: Map<KClass<*>, ScreenFragmentBinder>,
    resultRouter: ResultRouter,
    override val parent: Navigator? = null,
    private val navigationOverrides: Map<KClass<*>, FragmentScreenNavigation> = emptyMap(),
) : BaseNavigator(resultRouter) {

    private var knownEntryNames: List<String?> = currentEntryNames()

    /** Result to attach to the next listener-observed removal of the named entry. */
    private var pendingResult: Pair<String, PopResult?>? = null

    init {
        fragmentManager.addOnBackStackChangedListener {
            val current = currentEntryNames()
            if (current.size < knownEntryNames.size) {
                // Deliver for every removed entry, top-down; complete() no-ops when nobody awaits.
                for (index in knownEntryNames.size - 1 downTo current.size) {
                    val name = knownEntryNames[index] ?: continue
                    val pending = pendingResult
                    if (pending?.first == name) {
                        pendingResult = null
                        resultRouter.complete(name, pending.second)
                    } else {
                        resultRouter.complete(name, null)
                    }
                }
            }
            knownEntryNames = current
        }
    }

    override val backStack: List<Screen> get() = emptyList()

    override fun goTo(screen: Screen) {
        // A contributed per-screen override wins: dialogs, custom transactions, other nav APIs.
        navigationOverrides[screen::class]?.let { override ->
            override.navigate(
                FragmentNavigationRequest(
                    screen = screen,
                    fragmentManager = fragmentManager,
                    containerId = containerId,
                    backStackEntryName = resultKeyFor(screen),
                    resultRouter = resultRouter,
                )
            )
            return
        }
        // Default: the bound legacy fragment (or a ScreenFragment for migrated screens),
        // replaced into the container and pushed onto the back stack.
        val fragment = binders[screen::class]?.createFragment(screen)
            ?: ScreenFragment.newInstance(screen)
        fragmentManager.commit {
            setReorderingAllowed(true)
            replace(containerId, fragment)
            addToBackStack(resultKeyFor(screen))
        }
    }

    override fun pop(result: PopResult?): Boolean {
        val count = fragmentManager.backStackEntryCount
        if (count > 0) {
            val name = fragmentManager.getBackStackEntryAt(count - 1).name
            if (name != null && result != null) pendingResult = name to result
            fragmentManager.popBackStack()
            return true
        }
        return parent?.pop(result) ?: false
    }

    override fun resetRoot(screen: Screen) {
        // The listener delivers null to every awaited entry this clears.
        fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        goTo(screen)
    }

    private fun currentEntryNames(): List<String?> =
        (0 until fragmentManager.backStackEntryCount).map { fragmentManager.getBackStackEntryAt(it).name }
}
