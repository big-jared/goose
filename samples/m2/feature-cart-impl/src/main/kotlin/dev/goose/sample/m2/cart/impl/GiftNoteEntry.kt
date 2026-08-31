package dev.goose.sample.m2.cart.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.goose.runtime.GooseUi
import dev.goose.runtime.LocalNavigator
import dev.goose.runtime.Screen
import kotlinx.serialization.Serializable

@Serializable
data object GiftNoteScreen : Screen

/**
 * A SESSION-SCOPED screen: registered to [CheckoutScope], not AppScope, so it only resolves
 * inside the checkout's `GooseScope`, and its [CheckoutSession] parameter is injected from the
 * child graph. Same annotation as any other screen, one extra argument.
 */
@GooseUi(GiftNoteScreen::class, scope = CheckoutScope::class)
@Composable
fun GiftNoteUi(modifier: Modifier, session: CheckoutSession) {
    val navigator = LocalNavigator.current
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Gift note", style = MaterialTheme.typography.titleLarge)
        Text("Checkout session #${session.sessionId}")
        Text("Gift note: ${session.giftNote.ifEmpty { "none" }}")
        OutlinedButton(onClick = { session.giftNote = "Happy hatching!" }) {
            Text("Write gift note")
        }
        Button(onClick = { navigator.pop() }) { Text("Back to shipping") }
    }
}
