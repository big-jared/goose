package dev.goose.gaggle.legacy

import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import dev.goose.fragment.FragmentNavigationRequest
import dev.goose.fragment.FragmentNavigator
import dev.goose.fragment.FragmentNavigatorOwner
import dev.goose.fragment.FragmentScreenNavigation
import dev.goose.fragment.GooseFragmentAccessors
import dev.goose.fragment.GooseFragmentBinder
import dev.goose.fragment.GooseFragmentNavigation
import dev.goose.fragment.GooseScopeOwner
import dev.goose.fragment.ScreenFragment
import dev.goose.fragment.ScreenFragmentBinder
import dev.goose.fragment.fragmentScreenEntry
import dev.goose.gaggle.R
import dev.goose.gaggle.auth.api.SupportFlowScreen
import dev.goose.metro.GooseGraphHolder
import dev.goose.metro.GooseRuntimeAccessors
import dev.goose.metro.GooseScopeAccessors
import dev.goose.metro.retainedGraph
import dev.goose.runtime.GooseUi
import dev.goose.runtime.Navigator
import dev.goose.runtime.Screen
import dev.goose.runtime.ScreenEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * Demonstrates: a child dependency scope crossing a FragmentManager boundary. The support flow
 * is a LEGACY fragment owning a child FragmentManager stack AND a SupportScope graph
 * (GooseScopeOwner); the compose screens it hosts resolve the session and the agent from that
 * graph, and the graph is retained across rotation and disposed when the flow fragment is
 * removed.
 *
 * Also the home of the fragment-interop annotations: `@GooseFragmentBinder` maps
 * SupportFaqScreen to its legacy fragment, and `@GooseFragmentNavigation` shows
 * SupportHoursScreen as a legacy DialogFragment (see the bottom of this file).
 */
abstract class SupportScope private constructor()

@SingleIn(SupportScope::class)
@Inject
class SupportSession {
    val ticketId: String = "T-42"
}

/**
 * The canned support agent: pure, deterministic keyword routing, no I/O — which is exactly
 * what makes the chat unit-testable (see SupportAgentTest). A real app would put its service
 * client behind the same shape.
 */
@SingleIn(SupportScope::class)
@Inject
class SupportAgent {

    fun greeting(ticketId: String): String =
        "Welcome to pond support! This is Agent Goose on ticket $ticketId. How can I help?"

    fun replyTo(message: String): String = when {
        message.contains("honk", ignoreCase = true) ->
            "HONK received, loud and clear. A specialist goose is on it."
        message.contains("order", ignoreCase = true) ->
            "Your order is paddling through the pond. Expected: two sunrises."
        message.contains("thank", ignoreCase = true) ->
            "Happy to help! Honk anytime."
        else ->
            "Let me flap that up the chain and get back to you."
    }
}

@ContributesTo(SupportScope::class)
interface SupportSessionAccessor {
    val session: SupportSession
}

@GraphExtension(SupportScope::class)
interface SupportGraph : GooseScopeAccessors {
    @GraphExtension.Factory
    @ContributesTo(AppScope::class)
    interface Factory {
        fun createSupportGraph(): SupportGraph
    }
}

@Serializable
data object SupportChatScreen : Screen

/**
 * A LEGACY fragment the migrated chat screen navigates to by typed screen: the
 * `@GooseFragmentBinder` below tells the FragmentNavigator which fragment implements it, so
 * `navigator.goTo(SupportFaqScreen(...))` pushes SupportFaqFragment with a Bundle built from
 * the screen's typed fields. When the FAQ migrates, delete the binder and register a composable
 * for the SAME screen; the chat VM never notices.
 */
@Serializable
data class SupportFaqScreen(val topic: String) : Screen

/**
 * Shown as a legacy DialogFragment, not a transaction: the `@GooseFragmentNavigation` adapter
 * below overrides HOW this screen appears on the fragment host, while the VM still just calls
 * `navigator.goTo(SupportHoursScreen)`.
 */
@Serializable
data object SupportHoursScreen : Screen

/**
 * The status strip UNDER the chat — not navigation: SupportFlowFragment embeds this goose
 * screen as a nested fragment in its own container (see onViewCreated), with no back stack
 * entry. It still resolves SupportScope dependencies, because ScreenFragment finds the nearest
 * GooseScopeOwner up the parent-fragment chain.
 */
@Serializable
data object SupportStatusPanelScreen : Screen

data class ChatMessage(val fromUser: Boolean, val text: String)

data class SupportChatState(
    val messages: List<ChatMessage> = emptyList(),
    val agentTyping: Boolean = false,
) : com.airbnb.mvrx.MavericksState {
    /** Every user message is a honk at heart. Pinned by the hardening tests. */
    val honksSent: Int get() = messages.count { it.fromUser }
}

/**
 * Demonstrates: the screen-scoped ViewModel contract ON THE FRAGMENT HOST, with SCOPED
 * dependencies (agent + session from the SupportScope graph, across the FM boundary). This
 * VM's screen rides a FragmentManager back stack (inside SupportFlowFragment's child FM), yet
 * retention across rotation and clearing on pop behave exactly as on a Nav3 host; the
 * hardening tests pin both — the chat transcript IS the retained state.
 */
@dev.zacsweers.metro.AssistedInject
class SupportChatViewModel(
    @dev.zacsweers.metro.Assisted initialState: SupportChatState,
    @dev.zacsweers.metro.Assisted private val navigator: Navigator,
    private val agent: SupportAgent,
    private val session: SupportSession,
) : com.airbnb.mvrx.MavericksViewModel<SupportChatState>(initialState) {

    init {
        setState {
            if (messages.isEmpty()) {
                copy(messages = listOf(ChatMessage(fromUser = false, text = agent.greeting(session.ticketId))))
            } else {
                this
            }
        }
    }

    fun send(text: String) {
        setState { copy(messages = messages + ChatMessage(fromUser = true, text = text), agentTyping = true) }
        viewModelScope.launch {
            delay(300)
            setState {
                copy(
                    messages = messages + ChatMessage(fromUser = false, text = agent.replyTo(text)),
                    agentTyping = false,
                )
            }
        }
    }

    fun sendHonk() = send("HONK!")

    // Both destinations are legacy (a fragment, a dialog); this VM cannot tell. The binder and
    // the navigation adapter at the bottom of this file decide what actually happens.
    fun openFaq() = navigator.goTo(SupportFaqScreen(topic = "honk etiquette"))

    fun openHours() = navigator.goTo(SupportHoursScreen)

    @dev.zacsweers.metro.AssistedFactory
    fun interface Factory {
        fun create(initialState: SupportChatState, navigator: Navigator): SupportChatViewModel
    }

    // No Mavericks factory companion: goose-compiler-plugin generates the nested GooseFactory.
}

@GooseUi(SupportChatScreen::class, scope = SupportScope::class)
@Composable
fun SupportChatUi(
    state: SupportChatState,
    viewModel: SupportChatViewModel,
    modifier: Modifier,
    session: SupportSession,
) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Support chat", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Ticket ${session.ticketId} · Honks sent: ${state.honksSent}",
            style = MaterialTheme.typography.bodySmall,
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.messages.forEach { message -> ChatBubble(message) }
            if (state.agentTyping) {
                Text("Agent Goose is typing…", style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = viewModel::sendHonk) { Text("Send a honk") }
            OutlinedButton(onClick = { viewModel.send("Where is my order?") }) { Text("Ask about order") }
            OutlinedButton(onClick = { viewModel.send("Thanks!") }) { Text("Say thanks") }
            OutlinedButton(onClick = viewModel::openFaq) { Text("Pond FAQ") }
            OutlinedButton(onClick = viewModel::openHours) { Text("Pond hours") }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.fromUser) 16.dp else 4.dp,
                bottomEnd = if (message.fromUser) 4.dp else 16.dp,
            ),
            color = if (message.fromUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ) {
            Text(
                message.text,
                Modifier
                    .widthIn(max = 280.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@GooseUi(SupportStatusPanelScreen::class, scope = SupportScope::class)
@Composable
fun SupportStatusPanelUi(modifier: Modifier, session: SupportSession) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("●", color = MaterialTheme.colorScheme.primary)
            Text(
                "Ticket ${session.ticketId} · Status: Open · Avg reply: 1 min",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@ContributesTo(AppScope::class)
interface SupportEntriesModule {
    companion object {
        @Provides
        @IntoMap
        @ClassKey(SupportFlowScreen::class)
        fun supportFlowEntry(): ScreenEntry =
            fragmentScreenEntry<SupportFlowFragment, SupportFlowScreen>()
    }
}

/**
 * Demonstrates: `@GooseFragmentBinder` — the migrated chat screen navigating to a legacy
 * fragment by typed screen. One annotation is the entire registration; goose-compiler expands
 * it into the Metro map contribution the FragmentNavigator reads.
 */
@GooseFragmentBinder(SupportFaqScreen::class)
class SupportFaqBinder : ScreenFragmentBinder {
    override fun createFragment(screen: Screen): Fragment = SupportFaqFragment().apply {
        arguments = bundleOf(SupportFaqFragment.ARG_TOPIC to (screen as SupportFaqScreen).topic)
    }
}

/** Unmigrated on purpose: programmatic views, Bundle args, and a legacy pop of its own. */
class SupportFaqFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(Color.WHITE)
        addView(TextView(context).apply {
            text = "FAQ: ${requireArguments().getString(ARG_TOPIC)}\n(legacy fragment)"
            textSize = 18f
            gravity = Gravity.CENTER
        })
        addView(Button(context).apply {
            text = "Back to chat"
            // Legacy code popping the FragmentManager directly: goose's back-stack listener
            // observes the pop, so the chat resumes exactly as if the navigator had popped.
            setOnClickListener { parentFragmentManager.popBackStack() }
        })
    }

    companion object {
        const val ARG_TOPIC = "topic"
    }
}

/**
 * Demonstrates: `@GooseFragmentNavigation` — overriding HOW a screen appears on the fragment
 * host. SupportHoursScreen shows as a legacy DialogFragment instead of the default
 * replace+addToBackStack transaction; the VM that navigated cannot tell the difference.
 */
@GooseFragmentNavigation(SupportHoursScreen::class)
class SupportHoursNavigation : FragmentScreenNavigation {
    override fun navigate(request: FragmentNavigationRequest) {
        SupportHoursDialogFragment().show(request.fragmentManager, "support-hours")
    }
}

class SupportHoursDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        AlertDialog.Builder(requireContext())
            .setTitle("Pond hours")
            .setMessage("Agents paddle in from sunrise to sunset.")
            .setPositiveButton("Honk, got it", null)
            .create()
}

class SupportFlowFragment : Fragment(), GooseScopeOwner, FragmentNavigatorOwner {

    override val gooseScopeGraph: Any
        get() = retainedGraph(this) {
            val appGraph = (requireActivity().application as GooseGraphHolder).gooseGraph
            (appGraph as SupportGraph.Factory).createSupportGraph()
        }

    override val gooseNavigator: Navigator by lazy {
        val appGraph = (requireActivity().application as GooseGraphHolder).gooseGraph
        FragmentNavigator(
            fragmentManager = childFragmentManager,
            containerId = R.id.gaggle_support_container,
            binders = (appGraph as GooseFragmentAccessors).fragmentBinders,
            resultRouter = (appGraph as GooseRuntimeAccessors).resultRouter,
            navigationOverrides = (appGraph as GooseFragmentAccessors).fragmentNavigationOverrides,
            stackTag = "support-flow",
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = LinearLayout(requireContext()).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        orientation = LinearLayout.VERTICAL
        addView(
            FragmentContainerView(requireContext()).apply { id = R.id.gaggle_support_container },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        // Fixed height on purpose: a wrap-content ComposeView here re-measures against the
        // weighted chat container above and the two can ping-pong layout passes forever.
        addView(
            FragmentContainerView(requireContext()).apply { id = R.id.gaggle_support_panel },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (44 * resources.displayMetrics.density).toInt(),
            ),
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (childFragmentManager.findFragmentById(R.id.gaggle_support_container) == null) {
            gooseNavigator.goTo(SupportChatScreen)
        }
        // EMBEDDING, not navigating: the status panel is a goose screen attached as a nested
        // fragment in this fragment's own container — no back stack entry, no navigator call,
        // created through this FragmentManager's FragmentFactory. It resolves SupportScope
        // through the GooseScopeOwner walk like any hosted screen.
        if (childFragmentManager.findFragmentById(R.id.gaggle_support_panel) == null) {
            childFragmentManager.commit {
                add(
                    R.id.gaggle_support_panel,
                    ScreenFragment.newInstance(childFragmentManager, SupportStatusPanelScreen),
                )
            }
        }
    }
}
