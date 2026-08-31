package dev.goose.gaggle

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import dev.goose.gaggle.auth.api.GaggleTabs
import dev.goose.gaggle.auth.api.LoginScreen
import dev.goose.gaggle.auth.api.ProfileScreen
import dev.goose.gaggle.auth.api.SessionManager
import dev.goose.gaggle.auth.api.SessionManagerAccessor
import dev.goose.gaggle.cart.api.CartScreen
import dev.goose.gaggle.catalog.api.CatalogScreen
import dev.goose.gaggle.catalog.api.ProductScreen
import dev.goose.metro.GooseCompositionLocals
import dev.goose.metro.GooseGraphHolder
import dev.goose.metro.GooseScope
import dev.goose.nav3.GooseTabNavigator
import dev.goose.nav3.NavigableGooseContent
import dev.goose.nav3.TabSpec
import dev.goose.nav3.TabbedGooseContent
import dev.goose.nav3.rememberGooseBackStack
import dev.goose.nav3.rememberTabNavigator
import dev.goose.runtime.StackKey

/**
 * The shell. Demonstrates: the login gate switching between an app-scoped stack and the
 * logged-in GooseScope; tabs with independent persisted stacks; and deep links as plain
 * navigator calls — a cold-start link parks in SessionManager until login, a warm link
 * (onNewIntent) jumps tabs immediately.
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as GooseGraphHolder).gooseGraph
        val sessionManager = (graph as SessionManagerAccessor).sessionManager
        handleDeepLink(intent, sessionManager)
        setContent {
            MaterialTheme {
                GooseCompositionLocals(graph) {
                    Surface(Modifier.fillMaxSize()) {
                        val session = sessionManager.current
                        if (session == null) {
                            NavigableGooseContent(rememberGooseBackStack(LoginScreen))
                        } else {
                            GooseScope(session) {
                                LoggedInShell(sessionManager)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val graph = (application as GooseGraphHolder).gooseGraph
        handleDeepLink(intent, (graph as SessionManagerAccessor).sessionManager)
    }

    /** gaggle://product/{id} — parsed here, honored by the shell (after login if needed). */
    fun handleDeepLink(intent: Intent?, sessionManager: SessionManager) {
        val data = intent?.data ?: return
        if (data.scheme == "gaggle" && data.host == "product") {
            sessionManager.pendingProductId = data.lastPathSegment
        }
    }
}

@Composable
private fun LoggedInShell(sessionManager: SessionManager) {
    val tabs = rememberTabNavigator(
        tabs = listOf(
            TabSpec(GaggleTabs.Shop, CatalogScreen),
            TabSpec(GaggleTabs.Cart, CartScreen),
            TabSpec(GaggleTabs.Profile, ProfileScreen),
        ),
    )
    // A pending deep link becomes an atomic tab-switch-and-push, whenever it arrives.
    LaunchedEffect(sessionManager.pendingProductId) {
        val productId = sessionManager.pendingProductId ?: return@LaunchedEffect
        sessionManager.pendingProductId = null
        tabs.switchTo(GaggleTabs.Shop).goTo(ProductScreen(productId))
    }
    Column(Modifier.fillMaxSize()) {
        TabbedGooseContent(tabs, Modifier.weight(1f))
        TabBar(tabs)
    }
}

@Composable
private fun TabBar(tabs: GooseTabNavigator) {
    Row(Modifier.fillMaxWidth()) {
        TabButton(tabs, GaggleTabs.Shop, "Shop")
        TabButton(tabs, GaggleTabs.Cart, "Cart")
        TabButton(tabs, GaggleTabs.Profile, "Profile")
    }
}

@Composable
private fun TabButton(tabs: GooseTabNavigator, key: StackKey, label: String) {
    TextButton(onClick = { tabs.selectTab(key) }) {
        Text(if (tabs.currentStack == key) "• $label" else label)
    }
}
