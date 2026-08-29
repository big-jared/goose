package dev.goose.sample.m1.home.impl

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.delay

@SingleIn(AppScope::class)
@Inject
class UserRepository {
    suspend fun loadUsers(): List<String> {
        delay(400)
        return listOf("grace", "ada", "margaret", "katherine", "hedy")
    }
}
