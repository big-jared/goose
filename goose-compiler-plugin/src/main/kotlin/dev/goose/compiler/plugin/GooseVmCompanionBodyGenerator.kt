package dev.goose.compiler.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irDelegatingConstructorCall
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationContainer
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.impl.IrInstanceInitializerCallImpl
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.parentAsClass

/**
 * Backs the companions from [GooseVmCompanionGenerator] with the only body they need: a
 * constructor delegating to `GeneratedGooseVmFactory<VM, S>()`. The factory behavior itself
 * lives in that runtime superclass, so no methods are generated here.
 */
class GooseVmCompanionBodyGenerator : IrGenerationExtension {

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        moduleFragment.files.forEach { fillBodies(it, pluginContext) }
    }

    private fun fillBodies(container: IrDeclarationContainer, context: IrPluginContext) {
        for (declaration in container.declarations) {
            if (declaration is IrConstructor) {
                val origin = declaration.origin as? IrDeclarationOrigin.GeneratedByPlugin
                if (origin?.pluginKey == GooseVmCompanionGenerator.Key) {
                    fillConstructor(declaration, context)
                }
            }
            if (declaration is IrDeclarationContainer) fillBodies(declaration, context)
        }
    }

    private fun fillConstructor(constructor: IrConstructor, context: IrPluginContext) {
        val companion = constructor.parentAsClass
        val superType = companion.superTypes.first() as IrSimpleType
        val superConstructor = superType.classOrNull!!.owner.constructors.first()
        constructor.body = DeclarationIrBuilder(context, constructor.symbol).irBlockBody {
            +irDelegatingConstructorCall(superConstructor).apply {
                superType.arguments.forEachIndexed { index, argument ->
                    typeArguments[index] = (argument as IrTypeProjection).type
                }
            }
            +IrInstanceInitializerCallImpl(
                startOffset,
                endOffset,
                companion.symbol,
                context.irBuiltIns.unitType,
            )
        }
    }
}
