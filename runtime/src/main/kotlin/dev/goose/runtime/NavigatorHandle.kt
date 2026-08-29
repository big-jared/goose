package dev.goose.runtime

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * The [Navigator] actually injected into retained presenters.
 *
 * Presenters outlive compositions and activity recreations, but concrete navigators wrap
 * composition-scoped state (a remembered back stack list, a FragmentManager). This handle is the
 * stable object a presenter can hold forever: the host rebinds the live delegate on every
 * (re)composition, and calls made during a recreation gap are queued and replayed on rebind.
 *
 * Thread-safety and dispatch: bind/queue share one lock, so a call can never be stranded between
 * a bind and its queue drain. All navigation mutations execute on the main thread — calls from a
 * background context (e.g. Mavericks' state-store thread inside `withState { }`) are posted to
 * the main looper. Consequently [pop] returns its real result only when called on the main thread
 * while bound; a queued or posted pop reports `true`, meaning "accepted for dispatch".
 */
class NavigatorHandle : Navigator {
    private val lock = Any()
    private var delegateField: Navigator? = null
    private val queued = ArrayDeque<(Navigator) -> Unit>()
    private val delegateFlow = MutableStateFlow<Navigator?>(null)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun bind(navigator: Navigator) {
        val replay: List<(Navigator) -> Unit>
        synchronized(lock) {
            delegateField = navigator
            replay = queued.toList()
            queued.clear()
        }
        delegateFlow.value = navigator
        if (replay.isNotEmpty()) {
            runOnMain { replay.forEach { op -> op(navigator) } }
        }
    }

    fun unbind(navigator: Navigator) {
        synchronized(lock) {
            if (delegateField === navigator) delegateField = null
        }
        delegateFlow.compareAndSet(navigator, null)
    }

    override val parent: Navigator? get() = delegateFlow.value?.parent
    override val backStack: List<Screen> get() = delegateFlow.value?.backStack ?: emptyList()

    override fun goTo(screen: Screen) = submit { it.goTo(screen) }

    override fun pop(result: PopResult?): Boolean {
        synchronized(lock) { delegateField }?.let { delegate ->
            if (Looper.myLooper() == Looper.getMainLooper()) return delegate.pop(result)
        }
        submit { it.pop(result) }
        return true
    }

    override fun resetRoot(screen: Screen) = submit { it.resetRoot(screen) }

    override suspend fun <R : PopResult> goToForResult(screen: ScreenWithResult<R>): R? =
        withContext(Dispatchers.Main.immediate) {
            delegateFlow.filterNotNull().first().goToForResult(screen)
        }

    /** Runs [op] on the main thread against the bound delegate, or queues it until bind. */
    private fun submit(op: (Navigator) -> Unit) {
        val delegate = synchronized(lock) {
            delegateField ?: run {
                queued.addLast(op)
                return
            }
        }
        runOnMain { op(delegate) }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }
}
