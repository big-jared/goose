package dev.goose.sample.m2.cart.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.airbnb.mvrx.compose.collectAsState
import dev.goose.mavericks.flowViewModel
import dev.goose.mavericks.screenViewModel
import dev.goose.nav3.NavigableGooseContent
import dev.goose.nav3.rememberGooseBackStack
import dev.goose.runtime.FlowViewModelScope
import dev.goose.runtime.LocalNavigator
import dev.goose.runtime.OverlayScreen
import dev.goose.runtime.Screen
import dev.goose.runtime.ScreenEntry
import dev.goose.runtime.ScreenUi
import dev.goose.sample.m2.cart.api.CartScreen
import dev.goose.sample.m2.cart.api.CheckoutResult
import dev.goose.sample.m2.cart.api.CheckoutScreen
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import kotlinx.serialization.Serializable

// Wizard steps stay private to the cart feature by module structure: they live in :impl, which
// no other module may depend on (enforced at configuration time). They must be PUBLIC Kotlin
// declarations, though — Metro merges contributions in the app module, which cannot see
// internal classes from other modules.
@Serializable
data object ShippingStepScreen : Screen

@Serializable
data object ConfirmStepScreen : Screen

/** Rendered as a dialog over the cart (OverlayScreen → DialogSceneStrategy). */
@Serializable
data object CartInfoScreen : OverlayScreen {
    // Window config lives on the screen; SIZE is whatever the content measures (the Card in
    // CartInfoUi sizes itself against the full window once platform width is off).
    override fun dialogProperties() = DialogProperties(usePlatformDefaultWidth = false)
}

@ContributesIntoMap(AppScope::class, binding = binding<ScreenEntry>())
@ClassKey(CartScreen::class)
@Inject
class CartUi(
    private val vmFactory: CartViewModel.Factory,
) : ScreenUi<CartScreen>() {
    @Composable
    override fun Content(screen: CartScreen, modifier: Modifier) {
        val viewModel = screenViewModel<CartViewModel, CartState>(screen, vmFactory::create)
        val navigator = LocalNavigator.current
        val state by viewModel.collectAsState()
        Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Cart", style = MaterialTheme.typography.headlineMedium)
            state.items.forEach { item ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(item)
                    Text("1×")
                }
                HorizontalDivider()
            }
            Button(onClick = viewModel::checkout) { Text("Checkout") }
            OutlinedButton(onClick = { navigator.goTo(CartInfoScreen) }) { Text("Cart info") }
            state.lastCheckoutAddress?.let { Text("Last order shipped to: $it") }
        }
    }
}

@ContributesIntoMap(AppScope::class, binding = binding<ScreenEntry>())
@ClassKey(CartInfoScreen::class)
@Inject
class CartInfoUi : ScreenUi<CartInfoScreen>() {
    @Composable
    override fun Content(screen: CartInfoScreen, modifier: Modifier) {
        val navigator = LocalNavigator.current
        // With usePlatformDefaultWidth off (see CartInfoScreen), the content owns its size.
        Card(Modifier.fillMaxWidth(0.92f)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("About this cart", style = MaterialTheme.typography.titleLarge)
                Text("A dialog screen on the same back stack — push, pop, results all work.")
                Button(onClick = { navigator.pop() }) { Text("Close") }
            }
        }
    }
}

/**
 * A nested flow: this entry (on the tab's stack) hosts its own back stack and NavDisplay, and
 * declares a [FlowViewModelScope] so all wizard steps share a [CheckoutFlowViewModel]. The child
 * navigator's parent is the tab navigator, so root pops bubble out and the final step can answer
 * the awaiting caller by popping the parent with a [CheckoutResult].
 */
@ContributesIntoMap(AppScope::class, binding = binding<ScreenEntry>())
@ClassKey(CheckoutScreen::class)
@Inject
class CheckoutUi : ScreenUi<CheckoutScreen>() {
    @Composable
    override fun Content(screen: CheckoutScreen, modifier: Modifier) {
        val parentNavigator = LocalNavigator.current
        FlowViewModelScope {
            val childStack = rememberGooseBackStack(ShippingStepScreen)
            Column(modifier) {
                Text(
                    "Checkout",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(16.dp),
                )
                screen.itemId?.let {
                    Text("Item: $it", modifier = Modifier.padding(horizontal = 16.dp))
                }
                NavigableGooseContent(
                    backStack = childStack,
                    modifier = Modifier.fillMaxSize(),
                    parent = parentNavigator,
                )
            }
        }
    }
}

@ContributesIntoMap(AppScope::class, binding = binding<ScreenEntry>())
@ClassKey(ShippingStepScreen::class)
@Inject
class ShippingStepUi : ScreenUi<ShippingStepScreen>() {
    @Composable
    override fun Content(screen: ShippingStepScreen, modifier: Modifier) {
        val navigator = LocalNavigator.current
        val flowViewModel = flowViewModel<CheckoutFlowViewModel, CheckoutFlowState>()
        val flowState by flowViewModel.collectAsState()
        Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Step 1: Shipping", style = MaterialTheme.typography.titleLarge)
            Text("Address: ${flowState.address.ifEmpty { "—" }}")
            OutlinedButton(onClick = { flowViewModel.setAddress("1 Goose Way, Pondside") }) {
                Text("Use home address")
            }
            Button(
                onClick = { navigator.goTo(ConfirmStepScreen) },
                enabled = flowState.address.isNotBlank(),
            ) { Text("Next") }
        }
    }
}

@ContributesIntoMap(AppScope::class, binding = binding<ScreenEntry>())
@ClassKey(ConfirmStepScreen::class)
@Inject
class ConfirmStepUi : ScreenUi<ConfirmStepScreen>() {
    @Composable
    override fun Content(screen: ConfirmStepScreen, modifier: Modifier) {
        val navigator = LocalNavigator.current
        // The SAME flow VM the shipping step wrote to — shared via the enclosing FlowViewModelScope.
        val flowViewModel = flowViewModel<CheckoutFlowViewModel, CheckoutFlowState>()
        val flowState by flowViewModel.collectAsState()
        Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Step 2: Confirm", style = MaterialTheme.typography.titleLarge)
            Text("Ship to: ${flowState.address}")
            Button(
                onClick = {
                    // Answer the caller awaiting CheckoutScreen: pop the PARENT stack (which
                    // removes the whole nested flow) with the typed result.
                    navigator.parent?.pop(CheckoutResult(flowState.address))
                },
                enabled = flowState.address.isNotBlank(),
            ) { Text("Confirm order") }
            OutlinedButton(onClick = { navigator.pop() }) { Text("Back") }
        }
    }
}
