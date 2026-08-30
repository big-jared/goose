package dev.goose.sample.m1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dev.goose.metro.GooseCompositionLocals
import dev.goose.metro.GooseGraphHolder
import dev.goose.nav3.NavigableGooseContent
import dev.goose.nav3.rememberGooseBackStack
import dev.goose.sample.m1.home.api.HomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as GooseGraphHolder).gooseGraph
        setContent {
            MaterialTheme {
                GooseCompositionLocals(graph) {
                    Surface(Modifier.fillMaxSize()) {
                        val backStack = rememberGooseBackStack(HomeScreen)
                        NavigableGooseContent(backStack, Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
