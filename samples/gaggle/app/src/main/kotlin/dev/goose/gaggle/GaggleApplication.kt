package dev.goose.gaggle

import android.app.Application
import com.airbnb.mvrx.Mavericks
import dev.goose.metro.GooseGraphHolder
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph

/** Every binding arrives by Metro contribution from the modules on the classpath. */
@DependencyGraph(AppScope::class)
interface GaggleGraph

class GaggleApplication : Application(), GooseGraphHolder {
    override val gooseGraph: Any by lazy { createGraph<GaggleGraph>() }

    override fun onCreate() {
        super.onCreate()
        Mavericks.initialize(this)
    }
}
