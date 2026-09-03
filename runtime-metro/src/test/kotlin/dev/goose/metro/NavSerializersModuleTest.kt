package dev.goose.metro

import androidx.navigation3.runtime.NavKey
import dev.goose.runtime.Screen
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.subclass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

@Serializable
private data class CatalogScreen(val id: String) : Screen

/** A screen whose serial name no reflective class lookup can resolve. */
@Serializable
@SerialName("gift.note.entry")
private data class CustomNamedScreen(val note: String) : Screen

/**
 * The serializers-module contract behind back-stack persistence: screens serialize reflectively
 * by class name with no registration (nested classes included, via progressive dollar
 * substitution), an unknown serial name resolves to no deserializer instead of crashing, and a
 * custom @SerialName both requires and honors an explicit [screenSerializers] registration.
 */
@OptIn(ExperimentalSerializationApi::class)
class NavSerializersModuleTest {

    @Serializable
    data class NestedScreen(val id: String) : Screen

    private val bareModule = Goose.Builder().build().navSerializersModule
    private val bareJson = Json { serializersModule = bareModule }

    private fun Json.roundTripAsScreen(screen: Screen): Screen {
        val serializer = PolymorphicSerializer(Screen::class)
        return decodeFromString(serializer, encodeToString(serializer, screen))
    }

    @Test
    fun `a screen round-trips reflectively with no registration`() {
        val screen = CatalogScreen("c1")

        assertEquals(screen, bareJson.roundTripAsScreen(screen))
    }

    @Test
    fun `a nested screen's dotted serial name resolves to its dollar binary class`() {
        val screen = NestedScreen("n1")

        assertEquals(screen, bareJson.roundTripAsScreen(screen))
    }

    @Test
    fun `screens also round-trip under the NavKey base the back stack uses`() {
        val screen = CatalogScreen("c1")
        val serializer = PolymorphicSerializer(NavKey::class)

        assertEquals(screen, bareJson.decodeFromString(serializer, bareJson.encodeToString(serializer, screen)))
    }

    @Test
    fun `an unknown serial name yields no deserializer`() {
        assertNull(bareModule.getPolymorphic(Screen::class, serializedClassName = "dev.goose.gone.Missing"))
        assertNull(bareModule.getPolymorphic(NavKey::class, serializedClassName = "dev.goose.gone.Missing"))
    }

    @Test
    fun `a custom SerialName does not decode without explicit registration`() {
        assertNull(bareModule.getPolymorphic(NavKey::class, serializedClassName = "gift.note.entry"))
        assertThrows(SerializationException::class.java) {
            bareJson.roundTripAsScreen(CustomNamedScreen("hi"))
        }
    }

    @Test
    fun `an explicitly registered custom SerialName round-trips`() {
        val module = Goose.Builder()
            .addSerializers(screenSerializers { subclass(CustomNamedScreen::class) })
            .build()
            .navSerializersModule
        val json = Json { serializersModule = module }
        val serializer = PolymorphicSerializer(NavKey::class)
        val screen = CustomNamedScreen("hi")

        assertNotNull(module.getPolymorphic(NavKey::class, serializedClassName = "gift.note.entry"))
        assertEquals(screen, json.decodeFromString(serializer, json.encodeToString(serializer, screen)))
    }

    @Test
    fun `explicit modules pass through the set overload too`() {
        val module = Goose.Builder()
            .addSerializers(setOf(screenSerializers { subclass(CustomNamedScreen::class) }))
            .build()
            .navSerializersModule

        assertNotNull(module.getPolymorphic(NavKey::class, serializedClassName = "gift.note.entry"))
    }
}
