package dev.goose.mavericks

import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.airbnb.mvrx.ActivityViewModelContext
import com.airbnb.mvrx.Mavericks
import com.airbnb.mvrx.ViewModelContext
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/** Stands in for the plugin-generated nested `class GooseFactory : GeneratedGooseVmFactory()`. */
private class StubGeneratedFactory : GeneratedGooseVmFactory()

/**
 * The blanket-generation safety contract of [GeneratedGooseVmFactory]: unlike [gooseVmFactory],
 * creation outside a goose scope returns null (so Mavericks falls back to its own reflective
 * conventions for ViewModels goose never creates), while inside a scope it delegates to the
 * scope's creation lambda with the scope's navigator, exactly like the hand-written companion.
 */
@RunWith(AndroidJUnit4::class)
class GeneratedGooseVmFactoryTest {

    private val factory = StubGeneratedFactory()

    @Before
    fun setUp() {
        Mavericks.initialize(ApplicationProvider.getApplicationContext())
    }

    private fun vmContext(): ViewModelContext {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        return ActivityViewModelContext(activity, args = null)
    }

    @Test
    fun `creation outside a goose scope returns null so Mavericks falls back`() {
        assertNull(factory.create(vmContext(), PinState()))
    }

    @Test
    fun `creation inside a scope delegates to the lambda with the scope's navigator`() {
        val navigator = RecordingNavigator()
        val scope = GooseVmLocator.Scope(navigator) { state, nav ->
            PinViewModel(state as PinState, nav)
        }

        val vm = GooseVmLocator.withScope(scope) { factory.create(vmContext(), PinState()) }

        assertSame(navigator, (vm as PinViewModel).navigator)
    }
}
