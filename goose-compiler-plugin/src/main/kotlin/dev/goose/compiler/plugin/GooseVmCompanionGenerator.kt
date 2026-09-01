package dev.goose.compiler.plugin

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.plugin.createConstructor
import org.jetbrains.kotlin.fir.plugin.createNestedClass
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.FirUserTypeRef
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

/**
 * Generates the Mavericks factory migrated ViewModels used to write by hand:
 * ```
 * companion object : MavericksViewModelFactory<VM, S> by gooseVmFactory(VM::class)
 * ```
 * For every concrete, non-generic class that syntactically extends `MavericksViewModel` (or a
 * base named in [extraBases]) and declares neither a companion nor its own `GooseFactory`,
 * this nests `class GooseFactory : GeneratedGooseVmFactory()`. Mavericks finds ANY nested
 * MavericksViewModelFactory implementation reflectively, so a plain nested class works, and
 * the runtime superclass returns null outside a goose scope so Mavericks' own conventions
 * still create non-goose ViewModels.
 *
 * Detection is SYNTACTIC by design: this extension runs before supertype resolution (the same
 * pipeline step that builds companions), where only raw source shapes are available. Direct
 * `MavericksViewModel<...>` supertypes are recognized out of the box; apps whose ViewModels
 * extend an intermediate base register its simple name via the `extraViewModelBases` plugin
 * option. A false-positive match on an unrelated same-named class only costs a tiny inert
 * nested class.
 *
 * Skipped when the module doesn't depend on runtime-mavericks (the superclass is unresolvable).
 */
class GooseVmCompanionGenerator(
    session: FirSession,
    private val extraBases: Set<String>,
) : FirDeclarationGenerationExtension(session) {

    object Key : GeneratedDeclarationKey() {
        override fun toString() = "GooseVmFactory"
    }

    private val baseNames: Set<String> = setOf("MavericksViewModel") + extraBases

    override fun getNestedClassifiersNames(
        classSymbol: FirClassSymbol<*>,
        context: NestedClassGenerationContext,
    ): Set<Name> =
        if (classSymbol.wantsGeneratedFactory()) setOf(GOOSE_FACTORY) else emptySet()

    override fun generateNestedClassLikeDeclaration(
        owner: FirClassSymbol<*>,
        name: Name,
        context: NestedClassGenerationContext,
    ): FirClassLikeSymbol<*>? {
        if (name != GOOSE_FACTORY) return null
        if (owner !is FirRegularClassSymbol || !owner.wantsGeneratedFactory()) return null
        val nested = createNestedClass(owner, GOOSE_FACTORY, Key) {
            superType(GENERATED_FACTORY.constructClassLikeType(emptyArray(), isMarkedNullable = false))
        }
        return nested.symbol
    }

    override fun getCallableNamesForClass(
        classSymbol: FirClassSymbol<*>,
        context: MemberGenerationContext,
    ): Set<Name> {
        val origin = classSymbol.origin as? FirDeclarationOrigin.Plugin ?: return emptySet()
        if (origin.key != Key) return emptySet()
        return setOf(SpecialNames.INIT)
    }

    override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> {
        val owner = context.owner
        val origin = owner.origin as? FirDeclarationOrigin.Plugin ?: return emptyList()
        if (origin.key != Key) return emptyList()
        // Mavericks instantiates factory companions through the shape Kotlin gives them: a
        // PUBLIC one-parameter constructor (the synthetic DefaultConstructorMarker one),
        // invoked as newInstance(null) with no setAccessible. Mirror that shape exactly.
        val constructor = createConstructor(owner, Key, isPrimary = true) {
            visibility = Visibilities.Public
            valueParameter(MARKER, session.builtinTypes.nullableAnyType.coneType)
        }
        return listOf(constructor.symbol)
    }

    /** All checks are raw/syntactic: this can run before any resolution phase. */
    private fun FirClassSymbol<*>.wantsGeneratedFactory(): Boolean {
        if (this !is FirRegularClassSymbol || classKind != ClassKind.CLASS) return false
        if (typeParameterSymbols.isNotEmpty()) return false
        val modality = rawStatus.modality
        if (modality == Modality.ABSTRACT || modality == Modality.SEALED) return false
        if (session.symbolProvider.getClassLikeSymbolByClassId(GENERATED_FACTORY) == null) return false

        // Refs are raw FirUserTypeRefs when queried before supertype resolution and
        // FirResolvedTypeRefs after; the simple name is available either way.
        @OptIn(org.jetbrains.kotlin.fir.symbols.SymbolInternals::class)
        val extendsViewModel = fir.superTypeRefs.any { ref ->
            val simpleName = when (ref) {
                is FirUserTypeRef -> ref.qualifier.lastOrNull()?.name?.asString()
                is FirResolvedTypeRef -> ref.coneType.classId?.shortClassName?.asString()
                else -> null
            }
            simpleName in baseNames
        }
        if (!extendsViewModel) return false

        // Hand-written factories win: any companion, or an own nested GooseFactory, skips us.
        @OptIn(DirectDeclarationsAccess::class)
        val hasHandWritten = declarationSymbols
            .filterIsInstance<FirRegularClassSymbol>()
            .any {
                it.name == SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT || it.name == GOOSE_FACTORY
            }
        return !hasHandWritten
    }

    companion object {
        val GOOSE_FACTORY: Name = Name.identifier("GooseFactory")
        private val MARKER: Name = Name.identifier("marker")
        private val GENERATED_FACTORY =
            ClassId.topLevel(FqName("dev.goose.mavericks.GeneratedGooseVmFactory"))
    }
}
