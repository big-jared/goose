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
 *
 * The navigator exposed to VMs is the app-scoped [dev.goose.runtime.NavigatorHandle] — retained
 * Mavericks VMs hold it safely across recreation while this activity rebinds the live
 * [FragmentNavigator] each onCreate.
 */
class MainActivity : FragmentActivity(), FragmentNavigatorOwner {

    private lateinit var fragmentNavigator: FragmentNavigator

    override val gooseNavigator: Navigator
        get() = (application as GooseGraphHolder).gooseGraph
            .let { it as MainNavModule }.mainNavigatorHandle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = FrameLayout(this).apply { id = CONTAINER_ID }
        setContentView(container)

        val graph = (application as GooseGraphHolder).gooseGraph
        fragmentNavigator = FragmentNavigator(
            fragmentManager = supportFragmentManager,
            containerId = CONTAINER_ID,
            binders = (graph as GooseFragmentAccessors).fragmentBinders,
            resultRouter = (graph as GooseRuntimeAccessors).resultRouter,
        )
        (graph as MainNavModule).mainNavigatorHandle.bind(fragmentNavigator)

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

    override fun onDestroy() {
        val graph = (application as GooseGraphHolder).gooseGraph
        (graph as MainNavModule).mainNavigatorHandle.unbind(fragmentNavigator)
        super.onDestroy()
    }

    companion object {
        private val CONTAINER_ID = R.id.goose_fragment_container
    }
}
