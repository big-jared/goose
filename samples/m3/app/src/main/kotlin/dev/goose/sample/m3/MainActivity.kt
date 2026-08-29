package dev.goose.sample.m3

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.addCallback
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import dev.goose.fragment.FragmentNavigator
import dev.goose.fragment.FragmentNavigatorOwner
import dev.goose.fragment.GooseFragmentAccessors
import dev.goose.metro.GooseGraphHolder
import dev.goose.metro.GooseRuntimeAccessors
import dev.goose.runtime.Navigator
import dev.goose.sample.m3.legacy.HomeFragment

/**
 * The LEGACY host: a FragmentActivity whose FragmentManager still owns the back stack. Migrated
 * compose screens ride on it via ScreenFragment; nothing above the Navigator interface knows.
 */
class MainActivity : FragmentActivity(), FragmentNavigatorOwner {

    override lateinit var gooseNavigator: Navigator
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = FrameLayout(this).apply { id = CONTAINER_ID }
        setContentView(container)

        val graph = (application as GooseGraphHolder).gooseGraph
        gooseNavigator = FragmentNavigator(
            fragmentManager = supportFragmentManager,
            containerId = CONTAINER_ID,
            binders = (graph as GooseFragmentAccessors).fragmentBinders,
            resultRouter = (graph as GooseRuntimeAccessors).resultRouter,
        )

        if (savedInstanceState == null) {
            supportFragmentManager.commit { add(CONTAINER_ID, HomeFragment()) }
        }

        onBackPressedDispatcher.addCallback(this) {
            if (supportFragmentManager.backStackEntryCount > 0) {
                gooseNavigator.pop()
            } else {
                finish()
            }
        }
    }

    companion object {
        private val CONTAINER_ID = R.id.goose_fragment_container
    }
}
