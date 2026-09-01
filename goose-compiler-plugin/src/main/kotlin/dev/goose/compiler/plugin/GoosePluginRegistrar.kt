@file:OptIn(ExperimentalCompilerApi::class)

package dev.goose.compiler.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

/**
 * Goose's Kotlin compiler plugin. Screens implement [java.io.Serializable] (the Mavericks args
 * contract), which makes every `object` screen trip Kotlin's "Serializable object must implement
 * 'readResolve'" warning — and, without a `readResolve`, lose singleton identity when Java
 * deserialization rebuilds it after process death. This plugin generates the canonical
 * `private fun readResolve(): Any = TheObject` on every `object` that is a subtype of
 * `java.io.Serializable` and doesn't declare its own, so screen singletons stay singletons and
 * feature code never writes the boilerplate.
 *
 * Wiring: `kotlinCompilerPluginClasspath("dev.goose:goose-compiler-plugin:<version>")`.
 */
class GoosePluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = "dev.goose.compiler-plugin"
    override val supportsK2: Boolean get() = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        FirExtensionRegistrarAdapter.registerExtension(GooseFirExtensions())
        IrGenerationExtension.registerExtension(ReadResolveBodyGenerator())
    }
}

internal class GooseFirExtensions : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::ReadResolveDeclarationGenerator
    }
}
