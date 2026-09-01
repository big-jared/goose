package dev.goose.compiler.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrDeclarationContainer
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.util.parentAsClass

/**
 * Backs the `readResolve` declarations from [ReadResolveDeclarationGenerator] with
 * `return TheObject` — Java deserialization discards the freshly built instance and hands
 * callers the singleton.
 */
class ReadResolveBodyGenerator : IrGenerationExtension {

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.files.forEach { fillBodies(it, pluginContext) }
    }

    private fun fillBodies(container: IrDeclarationContainer, context: IrPluginContext) {
        for (declaration in container.declarations) {
            if (declaration is IrSimpleFunction) {
                val origin = declaration.origin as? IrDeclarationOrigin.GeneratedByPlugin
                if (origin?.pluginKey == ReadResolveDeclarationGenerator.Key) {
                    declaration.body = DeclarationIrBuilder(context, declaration.symbol).irBlockBody {
                        +irReturn(irGetObject(declaration.parentAsClass.symbol))
                    }
                }
            }
            if (declaration is IrDeclarationContainer) fillBodies(declaration, context)
        }
    }
}
