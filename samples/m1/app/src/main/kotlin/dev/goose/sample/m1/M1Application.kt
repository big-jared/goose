package dev.goose.sample.m1

import android.app.Application
import com.airbnb.mvrx.Mavericks
import dev.goose.metro.GooseGraphHolder
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph

/**
 * The whole app graph. Every binding — screen entries, VM creators, serializers, repositories —
 * arrives by Metro contribution from the modules on the classpath; nothing is declared here.
 */
@DependencyGraph(AppScope::class)
interface M1Graph

class M1Application : Application(), GooseGraphHolder {
    override val gooseGraph: Any by lazy { createGraph<M1Graph>() }

    override fun onCreate() {
        super.onCreate()
        Mavericks.initialize(this)
    }
}
