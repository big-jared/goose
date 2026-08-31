package dev.goose.fragment

import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import dev.goose.runtime.BaseNavigator
import dev.goose.runtime.Navigator
import dev.goose.runtime.PopResult
import dev.goose.runtime.ResultAwaiter
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
    // The exact caller this request answers, handed down explicitly from goToForResult through
    // goToAwaited. Null for a plain goTo: such a request has NO caller, even when an older
    // same-class request is still awaiting elsewhere — its result belongs to its own request.
    private val awaiter: ResultAwaiter?,
    private val createFragmentImpl: () -> Fragment,
    private val defaultTransactionImpl: () -> Unit,
) {
    /**
     * The fragment goose would show for this screen: the contributed binder's fragment for a
     * legacy screen, or a [ScreenFragment] (created through the FragmentManager's
     * [androidx.fragment.app.FragmentFactory]) for a migrated one. Use it when your adapter
     * runs its own transaction but has no reason to change WHAT is shown.
     */
    fun createFragment(): Fragment = createFragmentImpl()

    /**
     * Runs goose's built-in transaction for this request (replace into [containerId], pushed
     * under [backStackEntryName]). Lets a host-wide [FragmentScreenNavigation] customize only
     * some screens and delegate the rest.
     */
    fun performDefaultTransaction() = defaultTransactionImpl()

    /**
     * For destinations that bypass the back stack: answer the caller awaiting this screen.
     * Correlated exactly (two same-class dialogs answering out of order each resolve their own
     * caller) and effectively one-shot: the first call consumes the awaiter, later calls no-op,
     * so a dismiss callback firing after a result callback delivers nothing extra. No-ops on a
     * request created by plain goTo, which has no caller to answer.
     */
    fun deliverResult(result: PopResult?) {
        awaiter?.complete(result)
    }
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
    private val stackTag: String,
    /**
     * Host-wide transaction policy: receives every navigation that has no per-screen override.
     * Use it when this host's transactions differ from the default replace+addToBackStack
     * (animations, add instead of replace, a different container per destination); call
     * [FragmentNavigationRequest.performDefaultTransaction] for screens you don't customize.
     */
    private val defaultNavigation: FragmentScreenNavigation? = null,
) : BaseNavigator(resultRouter) {

    /** Scoped to this activity's stack (the tag is retained across rotation by the installer). */
    override fun resultKeyFor(screen: Screen): String =
        "${resultRouter.resultKeyOf(screen)}#$stackTag"

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
        requireMainThread()
        navigate(screen, awaiter = null)
    }

    override fun goToAwaited(screen: Screen, awaiter: ResultAwaiter) {
        requireMainThread()
        navigate(screen, awaiter)
    }

    private fun navigate(screen: Screen, awaiter: ResultAwaiter?) {
        // Precedence: per-screen override, then the host-wide policy, then the built-in
        // replace+addToBackStack transaction. Awaited default-transaction screens deliver by
        // entry name on pop; stack removal is LIFO-correct per class, no token needed.
        val navigation = navigationOverrides[screen::class] ?: defaultNavigation
        if (navigation != null) {
            navigation.navigate(
                FragmentNavigationRequest(
                    screen = screen,
                    fragmentManager = fragmentManager,
                    containerId = containerId,
                    backStackEntryName = resultKeyFor(screen),
                    awaiter = awaiter,
                    createFragmentImpl = { createFragmentFor(screen) },
                    defaultTransactionImpl = { performDefaultTransaction(screen) },
                )
            )
            return
        }
        performDefaultTransaction(screen)
    }

    /** The binder's fragment for legacy screens; a factory-created ScreenFragment otherwise. */
    private fun createFragmentFor(screen: Screen): Fragment =
        binders[screen::class]?.createFragment(screen)
            ?: ScreenFragment.newInstance(fragmentManager, screen)

    private fun performDefaultTransaction(screen: Screen) {
        val fragment = createFragmentFor(screen)
        fragmentManager.commit {
            setReorderingAllowed(true)
            replace(containerId, fragment)
            addToBackStack(resultKeyFor(screen))
        }
    }

    override fun pop(result: PopResult?): Boolean {
        requireMainThread()
        // A goTo from this same main-loop turn is still a queued FragmentManager transaction;
        // flush it so this pop sees the stack as ordered, not the stale pre-commit view
        // (goTo(A); pop() must pop A, not report an empty stack). Executing queued transactions
        // early is within FragmentManager's contract (commit only promises "as soon as
        // possible"), including any the legacy app queued itself. Skipped when the FM cannot
        // execute: state already saved, or reentrancy (pop called from inside an executing
        // transaction), where the queue's own ordering still holds.
        if (!fragmentManager.isStateSaved) {
            try {
                fragmentManager.executePendingTransactions()
            } catch (_: IllegalStateException) {
                // Reentrant execution; the queue drains in order without our help.
            }
        }
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
        requireMainThread()
        // The listener delivers null to every awaited entry this clears.
        fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        goTo(screen)
    }

    private fun currentEntryNames(): List<String?> =
        (0 until fragmentManager.backStackEntryCount).map { fragmentManager.getBackStackEntryAt(it).name }
}
