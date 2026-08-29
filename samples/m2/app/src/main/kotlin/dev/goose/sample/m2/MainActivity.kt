package dev.goose.sample.m2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import dev.goose.metro.GooseGraphHolder
import dev.goose.metro.LocalGooseGraph
import dev.goose.nav3.ScreenTabNavDisplay
import dev.goose.nav3.TabSpec
import dev.goose.nav3.rememberTabNavigator
import dev.goose.runtime.StackKey
import dev.goose.sample.m2.cart.api.CartScreen
import dev.goose.sample.m2.catalog.api.CatalogScreen

/** The app wires tab roots to stack keys — the only thing it knows about its features. */
val CatalogTab = StackKey("catalog")
val CartTab = StackKey("cart")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as GooseGraphHolder).gooseGraph
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalGooseGraph provides graph) {
                    val tabNavigator = rememberTabNavigator(
                        tabs = listOf(
                            TabSpec(CatalogTab, CatalogScreen),
                            TabSpec(CartTab, CartScreen),
                        ),
                    )
                    Scaffold(
                        bottomBar = {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = tabNavigator.currentTab == CatalogTab,
                                    onClick = { tabNavigator.selectTab(CatalogTab) },
                                    icon = { Text("🛍") },
                                    label = { Text("Catalog") },
                                )
                                NavigationBarItem(
                                    selected = tabNavigator.currentTab == CartTab,
                                    onClick = { tabNavigator.selectTab(CartTab) },
                                    icon = { Text("🛒") },
                                    label = { Text("Cart") },
                                )
                            }
                        },
                    ) { padding ->
                        ScreenTabNavDisplay(
                            tabNavigator = tabNavigator,
                            modifier = Modifier.fillMaxSize().padding(padding),
                            onRootBack = { finish() },
                        )
                    }
                }
            }
        }
    }
}
