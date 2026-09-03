package dev.goose.gaggle.cart.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.airbnb.mvrx.MavericksState
import com.airbnb.mvrx.MavericksViewModel
import com.airbnb.mvrx.PersistState
import com.airbnb.mvrx.compose.collectAsState
import dev.goose.gaggle.cart.api.CheckoutResult
import dev.goose.gaggle.cart.api.PickAddressScreen
import dev.goose.gaggle.cart.api.PickedAddress
import dev.goose.gaggle.auth.api.LoggedInScope
import dev.goose.gaggle.cart.api.CheckoutScreen
import dev.goose.mavericks.flowViewModel
import dev.goose.metro.GooseScope
import dev.goose.metro.rememberRetainedGraph
import dev.goose.nav3.NavigableGooseContent
import dev.goose.nav3.rememberGooseBackStack
import dev.goose.runtime.FlowViewModelScope
import dev.goose.runtime.GooseUi
import dev.goose.runtime.LocalNavigator
import dev.goose.runtime.Navigator
import dev.goose.runtime.Screen
import dev.goose.runtime.rememberSlideScreenTransitions
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

// Wizard steps are internal to the flow by module structure (public for Metro merging).
@Serializable
data object ShippingStepScreen : Screen

@Serializable
data object GiftNoteStepScreen : Screen

@Serializable
data object ConfirmStepScreen : Screen

data class CheckoutFlowState(
    /** Survives process death mid-wizard via Mavericks' own machinery. */
    @PersistState val address: String = "",
) : MavericksState

/**
 * Demonstrates: a flow-shared ViewModel — one instance for every wizard step — and the
 * goToForResult contract: the await runs in the RETAINED viewModelScope, so it survives the
 * shipping step leaving composition while the picker is up (and survives rotation mid-pick).
 */
class CheckoutFlowViewModel(initialState: CheckoutFlowState) :
    MavericksViewModel<CheckoutFlowState>(initialState) {

    fun chooseAddress(navigator: Navigator) {
        viewModelScope.launch {
            val picked = navigator.goToForResult(PickAddressScreen) ?: return@launch
            setState { copy(address = picked.line) }
        }
    }
}

/**
 * Demonstrates: a nested flow with its own back stack, a FlowViewModelScope shared by the
 * steps, and a nested CheckoutScope graph (retained across rotation, disposed with the flow)
 * INSIDE the logged-in scope: the registry chain is checkout -> session -> app.
 */
@GooseUi(CheckoutScreen::class, scope = LoggedInScope::class)
@Composable
fun CheckoutUi(modifier: Modifier, graphFactory: CheckoutGraph.Factory) {
    val parent = LocalNavigator.current
    val checkoutGraph = rememberRetainedGraph { graphFactory.createCheckoutGraph() }
    GooseScope(checkoutGraph) {
        FlowViewModelScope {
            val steps = rememberGooseBackStack(ShippingStepScreen)
            Column(modifier) {
                Text(
                    "Checkout",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(16.dp),
                )
                NavigableGooseContent(
                    steps,
                    Modifier.fillMaxSize(),
                    parent = parent,
                    defaultTransitions = rememberSlideScreenTransitions(),
                )
            }
        }
    }
}

/** Demonstrates: a wizard step awaiting a TYPED result from a picker screen. */
@GooseUi(ShippingStepScreen::class, scope = LoggedInScope::class)
@Composable
fun ShippingStepUi(modifier: Modifier) {
    val navigator = LocalNavigator.current
    val flowViewModel = flowViewModel<CheckoutFlowViewModel, CheckoutFlowState>()
    val state by flowViewModel.collectAsState()
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Step 1: Shipping", style = MaterialTheme.typography.titleLarge)
        Text("Ship to: ${state.address.ifEmpty { "not chosen" }}")
        OutlinedButton(onClick = { flowViewModel.chooseAddress(navigator) }) { Text("Choose address") }
        Button(
            onClick = { navigator.goTo(GiftNoteStepScreen) },
            enabled = state.address.isNotBlank(),
        ) { Text("Next: gift note") }
    }
}

/** Demonstrates: the picker pattern — a screen that IS a question, answering with pop(result). */
@GooseUi(PickAddressScreen::class, scope = LoggedInScope::class)
@Composable
fun PickAddressUi(modifier: Modifier) {
    val navigator = LocalNavigator.current
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Pick an address", style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = { navigator.pop(PickedAddress("1 Goose Way, Pondside")) }) {
            Text("1 Goose Way, Pondside")
        }
        OutlinedButton(onClick = { navigator.pop(PickedAddress("42 Feather Lane, Lakeview")) }) {
            Text("42 Feather Lane, Lakeview")
        }
    }
}

/** Demonstrates: a checkout-scoped dependency — one gift note per pass through the wizard. */
@GooseUi(GiftNoteStepScreen::class, scope = CheckoutScope::class)
@Composable
fun GiftNoteStepUi(modifier: Modifier, session: CheckoutSession) {
    val navigator = LocalNavigator.current
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Step 2: Gift note", style = MaterialTheme.typography.titleLarge)
        Text("Note: ${session.giftNote.ifEmpty { "none" }}")
        OutlinedButton(onClick = { session.giftNote = "Happy hatching!" }) { Text("Write gift note") }
        Button(onClick = { navigator.goTo(ConfirmStepScreen) }) { Text("Next: confirm") }
    }
}

/**
 * Demonstrates: the final step answering the ORIGINAL caller by popping the parent stack, and
 * a checkout-scoped screen reaching UP the registry chain for a session dependency (the cart
 * lives in LoggedInScope; this screen resolves checkout -> session).
 */
@GooseUi(ConfirmStepScreen::class, scope = CheckoutScope::class)
@Composable
fun ConfirmStepUi(modifier: Modifier, session: CheckoutSession, cart: SessionCart) {
    val navigator = LocalNavigator.current
    val flowViewModel = flowViewModel<CheckoutFlowViewModel, CheckoutFlowState>()
    val state by flowViewModel.collectAsState()
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Step 3: Confirm", style = MaterialTheme.typography.titleLarge)
        Text("Ship to: ${state.address}")
        Text("Gift note: ${session.giftNote.ifEmpty { "none" }}")
        Text("Items: ${cart.items.sumOf { it.qty }} · Order total: $${cart.totalCents / 100}")
        Button(onClick = {
            navigator.parent?.pop(CheckoutResult(state.address, itemCount = cart.items.sumOf { it.qty }))
        }) { Text("Place order") }
    }
}
