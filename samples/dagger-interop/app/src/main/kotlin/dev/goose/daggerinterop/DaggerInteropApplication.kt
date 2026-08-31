package dev.goose.daggerinterop

import android.app.Application
import com.airbnb.mvrx.Mavericks
import dev.goose.metro.GooseGraphHolder
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Includes
import dev.zacsweers.metro.createGraphFactory

/**
 * The adoption seam: the Metro graph INCLUDES the existing Dagger component, so every public
 * accessor on it (here, [GreetingRepository]) becomes an ordinary binding goose screens and
 * ViewModels can inject. The Dagger side keeps building with Dagger's own compiler; nothing
 * migrates until you want it to.
 */
@DependencyGraph(AppScope::class)
interface InteropGraph {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Includes legacy: LegacyComponent): InteropGraph
    }
}

class DaggerInteropApplication : Application(), GooseGraphHolder {
    override val gooseGraph: Any by lazy {
        createGraphFactory<InteropGraph.Factory>().create(DaggerLegacyComponent.create())
    }

    override fun onCreate() {
        super.onCreate()
        Mavericks.initialize(this)
    }
}
