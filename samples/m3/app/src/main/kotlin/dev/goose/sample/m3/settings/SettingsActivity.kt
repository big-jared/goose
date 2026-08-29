package dev.goose.sample.m3.settings

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.navigation3.runtime.NavKey
import dev.goose.fragment.FragmentScreen
import dev.goose.metro.GooseGraphHolder
import dev.goose.metro.LocalGooseGraph
import dev.goose.nav3.ScreenNavDisplay
import dev.goose.nav3.rememberGooseBackStack
import dev.goose.runtime.LocalNavigator
import dev.goose.runtime.Screen
import dev.goose.runtime.ScreenEntry
import dev.goose.sample.m3.SettingsHomeScreen
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * The CONVERTED flow: this activity's stack is Nav3-owned (ScreenNavDisplay). The one screen not
 * yet migrated — [AboutFragment] — rides along as a [FragmentScreen] (direction 2 of interop).
 */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as GooseGraphHolder).gooseGraph
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalGooseGraph provides graph) {
                    Surface(Modifier.fillMaxSize()) {
                        val backStack = rememberGooseBackStack(SettingsHomeScreen)
                        ScreenNavDisplay(backStack, Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@ContributesIntoMap(AppScope::class)
@ClassKey(SettingsHomeScreen::class)
@Inject
class SettingsHomeEntry : ScreenEntry {
    @Composable
    override fun Content(screen: Screen, modifier: Modifier) {
        val navigator = LocalNavigator.current
        Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Settings (converted, Nav3 stack)", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = { navigator.goTo(FragmentScreen.of<AboutFragment>()) }) {
                Text("About (legacy fragment on Nav3 stack)")
            }
        }
    }
}

/** Not migrated yet — hosted on the Nav3 stack via FragmentScreen + AndroidFragment. */
class AboutFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(Color.WHITE)
        addView(TextView(context).apply {
            text = "Legacy About fragment\nriding on a Nav3 back stack"
            textSize = 20f
            gravity = Gravity.CENTER
        })
    }
}

@ContributesTo(AppScope::class)
interface M3SerializersModule {
    companion object {
        @Provides
        @IntoSet
        fun m3Serializers(): SerializersModule = SerializersModule {
            polymorphic(NavKey::class) {
                subclass(SettingsHomeScreen::class)
            }
        }
    }
}
