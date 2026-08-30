package dev.goose.sample.m3

import android.os.Bundle
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import dev.goose.fragment.installGooseNavigator
import dev.goose.sample.m3.legacy.HomeFragment

/**
 * The LEGACY host: a FragmentActivity whose FragmentManager still owns the back stack. Migrated
 * compose screens ride on it via ScreenFragment; nothing above the Navigator interface knows.
 * All the wiring is one installGooseNavigator call; anything needing the navigator afterwards
 * reads activity.gooseNavigator.
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(FrameLayout(this).apply { id = CONTAINER_ID })

        installGooseNavigator(CONTAINER_ID)

        if (savedInstanceState == null) {
            supportFragmentManager.commit { add(CONTAINER_ID, HomeFragment()) }
        }
    }

    companion object {
        private val CONTAINER_ID = R.id.goose_fragment_container
    }
}
