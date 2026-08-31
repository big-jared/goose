package dev.goose.daggerinterop

import javax.inject.Inject
import javax.inject.Singleton

/**
 * The "existing app" half of this sample: plain Dagger, exactly as a mature codebase has it.
 * Nothing here knows goose or Metro exists.
 */
class GreetingRepository @Inject constructor() {
    val greeting: String = "Honk from Dagger"
}

@Singleton
@dagger.Component
interface LegacyComponent {
    val greetingRepository: GreetingRepository
}
