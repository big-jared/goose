package dev.goose.mavericks

import android.app.Application
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/**
 * The context-unwrapping contract behind screenViewModel/flowViewModel hosting checks:
 * [findComponentActivity] walks ContextWrapper chains to the hosting ComponentActivity and
 * returns null for contexts (like the Application) that never reach one.
 */
@RunWith(AndroidJUnit4::class)
class FindComponentActivityTest {

    private val activity: ComponentActivity =
        Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

    @Test
    fun `an activity context resolves to itself`() {
        assertSame(activity, activity.findComponentActivity())
    }

    @Test
    fun `a wrapped context unwraps to the hosting activity`() {
        val doublyWrapped = ContextWrapper(ContextWrapper(activity))

        assertSame(activity, doublyWrapped.findComponentActivity())
    }

    @Test
    fun `a non-activity context resolves to null`() {
        val application = ApplicationProvider.getApplicationContext<Application>()

        assertNull(application.findComponentActivity())
        assertNull(ContextWrapper(application).findComponentActivity())
    }
}
