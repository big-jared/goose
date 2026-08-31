package dev.goose.sample.m4

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
interface M4Graph {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Includes legacy: LegacyComponent): M4Graph
    }
}

class M4Application : Application(), GooseGraphHolder {
    override val gooseGraph: Any by lazy {
        createGraphFactory<M4Graph.Factory>().create(DaggerLegacyComponent.create())
    }

    override fun onCreate() {
        super.onCreate()
        Mavericks.initialize(this)
    }
}
