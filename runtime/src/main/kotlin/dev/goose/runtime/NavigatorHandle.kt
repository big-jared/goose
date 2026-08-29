package dev.goose.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * The [Navigator] actually injected into retained presenters.
 *
 * Presenters outlive compositions and activity recreations, but concrete navigators wrap
 * composition-scoped state (a remembered back stack list, a FragmentManager). This handle is the
 * stable object a presenter can hold forever: the host rebinds the live delegate on every
 * (re)composition, and calls made during a recreation gap are queued and replayed on rebind.
 */
class NavigatorHandle : Navigator {
    private val delegate = MutableStateFlow<Navigator?>(null)
    private val queued = ArrayDeque<(Navigator) -> Unit>()

    fun bind(navigator: Navigator) {
        delegate.value = navigator
        while (true) {
            val op = synchronized(queued) { queued.removeFirstOrNull() } ?: break
            op(navigator)
        }
    }

    fun unbind(navigator: Navigator) {
        delegate.compareAndSet(navigator, null)
    }

    override val parent: Navigator? get() = delegate.value?.parent
    override val backStack: List<Screen> get() = delegate.value?.backStack ?: emptyList()

    override fun goTo(screen: Screen) {
        val current = delegate.value ?: run {
            synchronized(queued) { queued.addLast { it.goTo(screen) } }
            return
        }
        current.goTo(screen)
    }

    override fun pop(result: PopResult?): Boolean {
        val current = delegate.value ?: run {
            synchronized(queued) { queued.addLast { it.pop(result) } }
            return true
        }
        return current.pop(result)
    }

    override fun resetRoot(screen: Screen) {
        val current = delegate.value ?: run {
            synchronized(queued) { queued.addLast { it.resetRoot(screen) } }
            return
        }
        current.resetRoot(screen)
    }

    override suspend fun <R : PopResult> goToForResult(screen: ScreenWithResult<R>): R? =
        delegate.filterNotNull().first().goToForResult(screen)
}
