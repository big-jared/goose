package dev.goose.sample.m2.cart.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.airbnb.mvrx.compose.collectAsState
import dev.goose.mavericks.MavericksVmCreator
import dev.goose.mavericks.screenViewModel
import dev.goose.nav3.ScreenNavDisplay
import dev.goose.nav3.rememberGooseBackStack
import dev.goose.runtime.LocalNavigator
import dev.goose.runtime.PopResult
import dev.goose.runtime.Screen
import dev.goose.runtime.ScreenEntry
import dev.goose.runtime.ScreenWithResult
import dev.goose.sample.m2.cart.api.CartScreen
import dev.goose.sample.m2.cart.api.CheckoutResult
import dev.goose.sample.m2.cart.api.CheckoutScreen
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/** Internal wizard steps — private to the cart feature, never exposed in :api. */
@Serializable
internal data object ShippingStepScreen : Screen

@Serializable
internal data class ConfirmStepScreen(val address: String) : Screen

@ContributesIntoMap(AppScope::class)
@ClassKey(CartScreen::class)
@Inject
class CartEntry : ScreenEntry {
    @Composable
    override fun Content(screen: Screen, modifier: Modifier) {
        val viewModel = screenViewModel<CartViewModel, CartState>(screen)
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
            state.lastCheckoutAddress?.let { Text("Last order shipped to: $it") }
        }
    }
}

/**
 * A nested flow: this entry (on the tab's stack) hosts its own back stack and NavDisplay. The
 * child navigator's parent is the tab navigator, so root pops bubble out and the wizard's final
 * step can answer the awaiting caller by popping the parent with a [CheckoutResult].
 */
@ContributesIntoMap(AppScope::class)
@ClassKey(CheckoutScreen::class)
@Inject
class CheckoutEntry : ScreenEntry {
    @Composable
    override fun Content(screen: Screen, modifier: Modifier) {
        val parentNavigator = LocalNavigator.current
        val childStack = rememberGooseBackStack(ShippingStepScreen)
        Column(modifier) {
            Text(
                "Checkout",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp),
            )
            ScreenNavDisplay(
                backStack = childStack,
                modifier = Modifier.fillMaxSize(),
                parent = parentNavigator,
            )
        }
    }
}

@ContributesIntoMap(AppScope::class)
@ClassKey(ShippingStepScreen::class)
@Inject
class ShippingStepEntry : ScreenEntry {
    @Composable
    override fun Content(screen: Screen, modifier: Modifier) {
        val navigator = LocalNavigator.current
        var address by rememberSaveable { mutableStateOf("") }
        Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Step 1: Shipping", style = MaterialTheme.typography.titleLarge)
            Text("Address: ${address.ifEmpty { "—" }}")
            OutlinedButton(onClick = { address = "1 Goose Way, Pondside" }) { Text("Use home address") }
            Button(
                onClick = { navigator.goTo(ConfirmStepScreen(address)) },
                enabled = address.isNotBlank(),
            ) { Text("Next") }
        }
    }
}

@ContributesIntoMap(AppScope::class)
@ClassKey(ConfirmStepScreen::class)
@Inject
class ConfirmStepEntry : ScreenEntry {
    @Composable
    override fun Content(screen: Screen, modifier: Modifier) {
        screen as ConfirmStepScreen
        val navigator = LocalNavigator.current
        Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Step 2: Confirm", style = MaterialTheme.typography.titleLarge)
            Text("Ship to: ${screen.address}")
            Button(onClick = {
                // Answer the caller awaiting CheckoutScreen: pop the PARENT stack (which removes
                // the whole nested flow) with the typed result.
                navigator.parent?.pop(CheckoutResult(screen.address))
            }) { Text("Confirm order") }
            OutlinedButton(onClick = { navigator.pop() }) { Text("Back") }
        }
    }
}

@ContributesTo(AppScope::class)
interface CartModule {
    companion object {
        @Provides
        @IntoMap
        @ClassKey(CartViewModel::class)
        fun cartVmCreator(factory: CartViewModel.Factory): MavericksVmCreator =
            MavericksVmCreator { state, _, navigator -> factory.create(state as CartState, navigator) }

        @Provides
        @IntoSet
        fun cartSerializers(): SerializersModule = SerializersModule {
            polymorphic(NavKey::class) {
                subclass(CartScreen::class)
                subclass(CheckoutScreen::class)
                subclass(ShippingStepScreen::class)
                subclass(ConfirmStepScreen::class)
            }
        }
    }
}
