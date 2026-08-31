package dev.goose.daggerinterop

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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as GooseGraphHolder).gooseGraph
        setContent {
            MaterialTheme {
                GooseCompositionLocals(graph) {
                    Surface(Modifier.fillMaxSize()) {
                        NavigableGooseContent(rememberGooseBackStack(InteropHomeScreen))
                    }
                }
            }
        }
    }
}
