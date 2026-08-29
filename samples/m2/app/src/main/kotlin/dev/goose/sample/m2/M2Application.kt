package dev.goose.sample.m2

import android.app.Application
import com.airbnb.mvrx.Mavericks
import dev.goose.metro.GooseGraphHolder
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph

@DependencyGraph(AppScope::class)
interface M2Graph

class M2Application : Application(), GooseGraphHolder {
    override val gooseGraph: Any by lazy { createGraph<M2Graph>() }

    override fun onCreate() {
        super.onCreate()
        Mavericks.initialize(this)
    }
}
