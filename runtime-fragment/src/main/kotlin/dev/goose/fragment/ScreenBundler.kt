package dev.goose.fragment

import android.os.Bundle
import androidx.core.os.bundleOf
import dev.goose.runtime.Screen
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * Round-trips a [Screen] through a fragment arguments Bundle using its own `@Serializable`
 * serializer plus the concrete class name — no polymorphic registration needed for this path.
 */
object ScreenBundler {
    private const val KEY_CLASS = "goose:screenClass"
    private const val KEY_JSON = "goose:screenJson"

    @OptIn(InternalSerializationApi::class)
    fun toBundle(screen: Screen): Bundle {
        @Suppress("UNCHECKED_CAST")
        val serializer = screen.javaClass.kotlin.serializer() as KSerializer<Screen>
        return bundleOf(
            KEY_CLASS to screen.javaClass.name,
            KEY_JSON to Json.encodeToString(serializer, screen),
        )
    }

    @OptIn(InternalSerializationApi::class)
    fun fromBundle(bundle: Bundle): Screen {
        val className = requireNotNull(bundle.getString(KEY_CLASS)) { "Missing screen class" }
        val json = requireNotNull(bundle.getString(KEY_JSON)) { "Missing screen json" }
        val kClass = Class.forName(className).kotlin
        @Suppress("UNCHECKED_CAST")
        val serializer = kClass.serializer() as KSerializer<Screen>
        return Json.decodeFromString(serializer, json)
    }
}
