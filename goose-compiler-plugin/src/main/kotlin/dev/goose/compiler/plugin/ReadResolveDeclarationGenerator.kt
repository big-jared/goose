package dev.goose.compiler.plugin

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.resolve.lookupSuperTypes
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Declares `private fun readResolve(): Any` on every source `object` whose supertype closure
 * contains [java.io.Serializable], unless the object already declares one. The frontend then
 * sees a complete Serializable singleton (no "must implement 'readResolve'" warning), and
 * [ReadResolveBodyGenerator] fills in the body returning the object instance.
 */
class ReadResolveDeclarationGenerator(session: FirSession) : FirDeclarationGenerationExtension(session) {

    object Key : GeneratedDeclarationKey() {
        override fun toString() = "GooseReadResolve"
    }

    override fun getCallableNamesForClass(
        classSymbol: FirClassSymbol<*>,
        context: MemberGenerationContext,
    ): Set<Name> {
        if (classSymbol !is FirRegularClassSymbol || classSymbol.classKind != ClassKind.OBJECT) {
            return emptySet()
        }
        // Raw declaration access on purpose: this runs while the class's member scope is being
        // built, so the scope-based accessors would recurse back into this extension.
        @OptIn(DirectDeclarationsAccess::class)
        val declaresOwn = classSymbol.declarationSymbols
            .filterIsInstance<FirNamedFunctionSymbol>()
            .any { it.name == READ_RESOLVE && it.valueParameterSymbols.isEmpty() }
        if (declaresOwn) return emptySet()
        val serializable = lookupSuperTypes(
            classSymbol,
            lookupInterfaces = true,
            deep = true,
            useSiteSession = session,
        ).any { it.classId == JAVA_IO_SERIALIZABLE }
        if (!serializable) return emptySet()
        return setOf(READ_RESOLVE)
    }

    override fun generateFunctions(
        callableId: CallableId,
        context: MemberGenerationContext?,
    ): List<FirNamedFunctionSymbol> {
        val owner = context?.owner ?: return emptyList()
        if (callableId.callableName != READ_RESOLVE) return emptyList()
        val function = createMemberFunction(
            owner,
            Key,
            READ_RESOLVE,
            session.builtinTypes.anyType.coneType,
        ) {
            visibility = Visibilities.Private
        }
        return listOf(function.symbol)
    }

    companion object {
        val READ_RESOLVE: Name = Name.identifier("readResolve")
        private val JAVA_IO_SERIALIZABLE: ClassId = ClassId.topLevel(FqName("java.io.Serializable"))
    }
}
