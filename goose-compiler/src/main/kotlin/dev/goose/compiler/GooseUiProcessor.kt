package dev.goose.compiler

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Nullability
import com.google.devtools.ksp.validate

private const val GOOSE_UI = "dev.goose.runtime.GooseUi"
private const val MODIFIER = "androidx.compose.ui.Modifier"
private const val MAVERICKS_VM = "com.airbnb.mvrx.MavericksViewModel"
private const val NAVIGATOR = "dev.goose.runtime.Navigator"

/**
 * Turns `@GooseUi(SomeScreen::class)` composable functions into the full Goose registration:
 * a Metro-contributed module whose @Provides function returns the ScreenEntry adapter, keyed by
 * the screen class. Parameters are wired by type:
 * - the screen class -> the screen being rendered
 * - [Modifier] -> the host's modifier
 * - a MavericksViewModel with a nested assisted factory `(State, Navigator) -> VM` -> a generated
 *   `screenViewModel` call (the factory itself is injected from the graph)
 * - that ViewModel's state class -> a generated `collectAsState().value`
 * - anything else -> an injected provider parameter, resolved from the graph at compile time
 */
class GooseUiProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(GOOSE_UI)
        val deferred = mutableListOf<KSAnnotated>()
        for (symbol in symbols) {
            if (!symbol.validate()) {
                deferred += symbol
                continue
            }
            val function = symbol as? KSFunctionDeclaration ?: run {
                logger.error("@GooseUi is only valid on functions", symbol)
                continue
            }
            generate(function)
        }
        return deferred
    }

    private fun generate(function: KSFunctionDeclaration) {
        val annotation = function.annotations.first { it.annotationType.resolve().declaration.qualifiedName?.asString() == GOOSE_UI }
        val screenType = annotation.screenArgument() ?: run {
            logger.error("@GooseUi requires a screen class argument", function)
            return
        }
        val screenFqn = screenType.declaration.qualifiedName?.asString() ?: run {
            logger.error("@GooseUi screen class has no qualified name", function)
            return
        }
        val packageName = function.packageName.asString()
        val functionName = function.simpleName.asString()
        val moduleName = "${functionName}GooseModule"

        data class Injected(val name: String, val type: String)
        data class VmParam(val name: String, val vmFqn: String, val stateFqn: String, val factoryFqn: String, val createName: String)

        var hasScreenParam = false
        var hasModifierParam = false
        val injected = mutableListOf<Injected>()
        val vmParams = mutableListOf<VmParam>()

        // First pass: find ViewModel parameters, so state parameters can be matched to them.
        for (param in function.parameters) {
            val name = param.name?.asString() ?: continue
            val type = param.type.resolve()
            val decl = type.declaration as? KSClassDeclaration ?: continue
            val vmSuper = decl.getAllSuperTypes()
                .firstOrNull { it.declaration.qualifiedName?.asString() == MAVERICKS_VM }
                ?: continue
            val vmFqn = decl.qualifiedName?.asString() ?: continue
            val stateFqn = vmSuper.arguments.firstOrNull()?.type?.resolve()
                ?.declaration?.qualifiedName?.asString() ?: run {
                logger.error("Cannot resolve the state type of $vmFqn", param)
                return
            }
            val factory = decl.findGooseFactory(stateFqn, vmFqn) ?: run {
                logger.error(
                    "@GooseUi cannot wire '$name: ${decl.simpleName.asString()}': no nested assisted " +
                        "factory with a `(initialState, navigator)` create function. For a screen-scoped " +
                        "ViewModel, add one (see the goose README). For a flow-shared ViewModel, call " +
                        "flowViewModel() inside the function instead of taking it as a parameter.",
                    param,
                )
                return
            }
            vmParams += VmParam(name, vmFqn, stateFqn, factory.first, factory.second)
        }

        val callArgs = mutableListOf<String>()
        for (param in function.parameters) {
            val name = param.name?.asString() ?: run {
                logger.error("@GooseUi function parameters must be named", function)
                return
            }
            val type = param.type.resolve()
            val typeFqn = type.declaration.qualifiedName?.asString()
            val stateOwners = vmParams.filter { it.stateFqn == typeFqn }
            when {
                typeFqn == screenFqn -> {
                    hasScreenParam = true
                    callArgs += "$name = screen as $screenFqn"
                }
                typeFqn == MODIFIER -> {
                    hasModifierParam = true
                    callArgs += "$name = modifier"
                }
                vmParams.any { it.name == name } -> callArgs += "$name = $name"
                stateOwners.size == 1 -> callArgs += "$name = ${stateOwners.single().name}.collectAsState().value"
                stateOwners.size > 1 -> {
                    logger.error(
                        "State parameter '$name' is ambiguous: ${stateOwners.size} ViewModel parameters share this state type",
                        param,
                    )
                    return
                }
                else -> {
                    injected += Injected(name, type.render())
                    callArgs += "$name = $name"
                }
            }
        }

        for (vm in vmParams) {
            injected += Injected("${vm.name}Factory", vm.factoryFqn)
        }

        val providerParams = injected.joinToString(",\n            ") { "${it.name}: ${it.type}" }
        val lambdaParams = buildString {
            append(if (hasScreenParam || vmParams.isNotEmpty()) "screen" else "_")
            append(", ")
            append(if (hasModifierParam) "modifier" else "_")
        }
        val imports = buildString {
            if (vmParams.isNotEmpty()) {
                append("\nimport dev.goose.mavericks.screenViewModel")
                if (callArgs.any { ".collectAsState()" in it }) append("\nimport com.airbnb.mvrx.compose.collectAsState")
            }
        }
        val vmDeclarations = vmParams.joinToString("") { vm ->
            "\n            val ${vm.name} = screenViewModel(screen, ${vm.vmFqn}::class.java, ${vm.stateFqn}::class.java, ${vm.name}Factory::${vm.createName})"
        }

        val file = codeGenerator.createNewFile(
            dependencies = Dependencies(aggregating = false, function.containingFile!!),
            packageName = packageName,
            fileName = moduleName,
        )
        file.bufferedWriter().use { writer ->
            writer.write(
                """
                |// Generated by goose-compiler from @GooseUi on $functionName. Do not edit.
                |@file:Suppress("UNCHECKED_CAST", "UNUSED_ANONYMOUS_PARAMETER")
                |package $packageName
                |$imports
                |
                |@dev.zacsweers.metro.ContributesTo(dev.zacsweers.metro.AppScope::class)
                |public interface $moduleName {
                |    public companion object {
                |        @dev.zacsweers.metro.Provides
                |        @dev.zacsweers.metro.IntoMap
                |        @dev.zacsweers.metro.ClassKey($screenFqn::class)
                |        public fun provide$functionName(
                |            $providerParams
                |        ): dev.goose.runtime.ScreenEntry = dev.goose.runtime.ScreenEntry { $lambdaParams ->$vmDeclarations
                |            $functionName(
                |                ${callArgs.joinToString(",\n                ")},
                |            )
                |        }
                |    }
                |}
                |""".trimMargin()
            )
        }
    }

    /**
     * Finds the ViewModel's goose assisted factory: a nested classifier with exactly one abstract
     * function of shape `(stateType, Navigator) -> vmType`. Returns (factory FQN, create-function
     * name), or null when the VM has none (flow-shared VMs deliberately don't).
     */
    private fun KSClassDeclaration.findGooseFactory(stateFqn: String, vmFqn: String): Pair<String, String>? {
        for (nested in declarations.filterIsInstance<KSClassDeclaration>()) {
            val factoryFqn = nested.qualifiedName?.asString() ?: continue
            val create = nested.getDeclaredFunctions().singleOrNull { it.isAbstract } ?: continue
            val params = create.parameters
            if (params.size != 2) continue
            if (params[0].type.resolve().declaration.qualifiedName?.asString() != stateFqn) continue
            if (params[1].type.resolve().declaration.qualifiedName?.asString() != NAVIGATOR) continue
            if (create.returnType?.resolve()?.declaration?.qualifiedName?.asString() != vmFqn) continue
            return factoryFqn to create.simpleName.asString()
        }
        return null
    }

    private fun KSAnnotation.screenArgument(): KSType? =
        arguments.firstOrNull { it.name?.asString() == "screen" || it.name == null }?.value as? KSType

    /** Renders a type with qualified names, generics, and nullability. */
    private fun KSType.render(): String {
        val base = declaration.qualifiedName?.asString() ?: declaration.simpleName.asString()
        val args = if (arguments.isEmpty()) "" else arguments.joinToString(", ", "<", ">") { arg ->
            arg.type?.resolve()?.render() ?: "*"
        }
        val nullable = if (nullability == Nullability.NULLABLE) "?" else ""
        return "$base$args$nullable"
    }
}

class GooseUiProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        GooseUiProcessor(environment.codeGenerator, environment.logger)
}
