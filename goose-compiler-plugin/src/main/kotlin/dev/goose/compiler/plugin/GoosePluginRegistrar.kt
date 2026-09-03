@file:OptIn(ExperimentalCompilerApi::class)

package dev.goose.compiler.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

/**
 * Goose's Kotlin compiler plugin. Two generators, both removing per-declaration boilerplate:
 *
 * 1. readResolve: screens implement [java.io.Serializable] (the Mavericks args contract), so
 *    every `object` screen needs `private fun readResolve(): Any = TheObject` to stay a
 *    singleton across Java deserialization. Generated on every `object` implementing
 *    `dev.goose.runtime.Screen` that doesn't declare its own — and only those: the runtime's
 *    Screen-scoped consumer keep rule is what preserves the method through R8, so non-Screen
 *    Serializable objects keep writing readResolve (and a keep rule) by hand.
 * 2. Mavericks factories: every migrated ViewModel needed a
 *    `companion object : MavericksViewModelFactory by gooseVmFactory(...)`. Generated as a
 *    nested `GooseFactory` on MavericksViewModel subclasses without a hand-written factory
 *    (see [GooseVmCompanionGenerator]). ViewModels extending an intermediate base register
 *    its simple name via the `extraViewModelBases` plugin option.
 *
 * Wiring: `kotlinCompilerPluginClasspath("dev.goose:goose-compiler-plugin:<version>")`.
 */
class GoosePluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = "dev.goose.compiler-plugin"
    override val supportsK2: Boolean get() = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val extraBases = configuration.get(GooseCommandLineProcessor.EXTRA_VM_BASES_KEY)
            ?.toSet()
            ?: emptySet()
        FirExtensionRegistrarAdapter.registerExtension(GooseFirExtensions(extraBases))
        IrGenerationExtension.registerExtension(ReadResolveBodyGenerator())
        IrGenerationExtension.registerExtension(GooseVmCompanionBodyGenerator())
    }
}

class GooseCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = "dev.goose.compiler-plugin"
    override val pluginOptions: Collection<AbstractCliOption> = listOf(EXTRA_VM_BASES_OPTION)

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        when (option.optionName) {
            EXTRA_VM_BASES_OPTION.optionName -> configuration.put(
                EXTRA_VM_BASES_KEY,
                value.split(',').map { it.trim() }.filter { it.isNotEmpty() },
            )
        }
    }

    companion object {
        val EXTRA_VM_BASES_OPTION = CliOption(
            optionName = "extraViewModelBases",
            valueDescription = "<SimpleName,SimpleName,...>",
            description = "Simple names of intermediate MavericksViewModel base classes whose " +
                "subclasses should also get a generated Goose factory",
            required = false,
            allowMultipleOccurrences = false,
        )
        val EXTRA_VM_BASES_KEY: CompilerConfigurationKey<List<String>> =
            CompilerConfigurationKey.create("extraViewModelBases")
    }
}

internal class GooseFirExtensions(private val extraBases: Set<String>) : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::ReadResolveDeclarationGenerator
        +FirDeclarationGenerationExtension.Factory { session ->
            GooseVmCompanionGenerator(session, extraBases)
        }
    }
}
