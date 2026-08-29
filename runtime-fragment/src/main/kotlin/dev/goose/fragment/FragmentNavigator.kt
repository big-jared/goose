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
import dev.goose.runtime.ScreenWithResult
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
 * A [Navigator] over a FragmentManager back stack — the legacy half of a migration. ViewModels
 * navigating through this cannot tell it apart from a Nav3 host, which is the whole point:
 * migrating a screen from fragment-hosted to compose-hosted swaps the host, not the VM.
 *
 * Screens whose fragment is a migrated compose screen need no binder: anything unmapped is hosted
 * in a [ScreenFragment] automatically.
 */
class FragmentNavigator(
    private val fragmentManager: FragmentManager,
    @param:IdRes private val containerId: Int,
    private val binders: Map<KClass<*>, ScreenFragmentBinder>,
    resultRouter: ResultRouter,
    override val parent: Navigator? = null,
) : BaseNavigator(resultRouter) {

    private val mirror = mutableListOf<Screen>()

    override val backStack: List<Screen> get() = mirror.toList()

    override fun goTo(screen: Screen) {
        val fragment = binders[screen::class]?.createFragment(screen)
            ?: ScreenFragment.newInstance(screen)
        fragmentManager.commit {
            setReorderingAllowed(true)
            replace(containerId, fragment)
            addToBackStack(resultRouter.resultKeyOf(screen))
        }
        mirror.add(screen)
    }

    override fun pop(result: PopResult?): Boolean {
        val count = fragmentManager.backStackEntryCount
        if (count > 0) {
            // The back stack entry's name is the popped screen's result key; complete() no-ops
            // when nobody is awaiting it.
            val name = fragmentManager.getBackStackEntryAt(count - 1).name
            fragmentManager.popBackStack()
            mirror.removeLastOrNull()
            if (name != null) resultRouter.complete(name, result)
            return true
        }
        return parent?.pop(result) ?: false
    }

    override fun resetRoot(screen: Screen) {
        fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        mirror.clear()
        goTo(screen)
    }

    /**
     * Call from the host activity's back handling so system back delivers "dismissed without
     * answering" to any caller awaiting the popped screen.
     */
    fun onSystemPop() {
        val popped = mirror.removeLastOrNull() ?: return
        if (popped is ScreenWithResult<*>) {
            resultRouter.complete(resultRouter.resultKeyOf(popped), null)
        }
    }
}
