package dev.goose.fragment

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.goose.runtime.ResultRouter
import dev.goose.runtime.Screen
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import android.os.Looper

@Serializable
private data class OverriddenScreen(val id: String) : Screen

@Serializable
private data class PlainScreen(val id: String) : Screen

/**
 * The host-aware interop contract: per-screen overrides beat the host-wide default policy,
 * the default policy sees everything else and can delegate to the built-in transaction, and
 * goose's ScreenFragments are created through the host FragmentManager's FragmentFactory.
 */
@RunWith(AndroidJUnit4::class)
class HostAwareNavigationTest {

    private fun activity(): FragmentActivity =
        Robolectric.buildActivity(FragmentActivity::class.java).setup().get()

    @Test
    fun perScreenOverrideBeatsHostDefault() {
        val activity = activity()
        val hits = mutableListOf<String>()
        val navigator = FragmentNavigator(
            fragmentManager = activity.supportFragmentManager,
            containerId = android.R.id.content,
            binders = emptyMap(),
            resultRouter = ResultRouter(),
            navigationOverrides = mapOf(
                OverriddenScreen::class to FragmentScreenNavigation { hits += "override:${(it.screen as OverriddenScreen).id}" },
            ),
            stackTag = "test",
            defaultNavigation = FragmentScreenNavigation { hits += "host:${(it.screen as PlainScreen).id}" },
        )
        navigator.goTo(OverriddenScreen("a"))
        navigator.goTo(PlainScreen("b"))
        assertEquals(listOf("override:a", "host:b"), hits)
    }

    @Test
    fun hostDefaultCanDelegateToBuiltInTransaction() {
        val activity = activity()
        val fm = activity.supportFragmentManager
        val bound = Fragment()
        val navigator = FragmentNavigator(
            fragmentManager = fm,
            containerId = android.R.id.content,
            binders = mapOf(PlainScreen::class to ScreenFragmentBinder { bound }),
            resultRouter = ResultRouter(),
            stackTag = "test",
            // A policy that customizes nothing and delegates everything: must behave exactly
            // like having no policy, including back-stack entry naming for result delivery.
            defaultNavigation = FragmentScreenNavigation { it.performDefaultTransaction() },
        )
        navigator.goTo(PlainScreen("x"))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, fm.backStackEntryCount)
        assertTrue(fm.getBackStackEntryAt(0).name!!.startsWith(PlainScreen::class.java.name))
        assertTrue(fm.fragments.last() === bound)
    }

    @Test
    fun screenFragmentsGoThroughTheHostsFragmentFactory() {
        val activity = activity()
        val fm = activity.supportFragmentManager
        val instantiated = mutableListOf<String>()
        fm.fragmentFactory = object : FragmentFactory() {
            override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
                instantiated += className
                return super.instantiate(classLoader, className)
            }
        }
        var fragment: Fragment? = null
        val navigator = FragmentNavigator(
            fragmentManager = fm,
            containerId = android.R.id.content,
            binders = emptyMap(),
            resultRouter = ResultRouter(),
            navigationOverrides = mapOf(
                PlainScreen::class to FragmentScreenNavigation { fragment = it.createFragment() },
            ),
            stackTag = "test",
        )
        navigator.goTo(PlainScreen("x"))
        assertEquals(listOf(ScreenFragment::class.java.name), instantiated)
        assertTrue(fragment is ScreenFragment)
        // Arguments carry the screen, same as the direct construction path.
        assertEquals(PlainScreen("x"), ScreenBundler.fromBundle(fragment!!.requireArguments()))
    }
}
