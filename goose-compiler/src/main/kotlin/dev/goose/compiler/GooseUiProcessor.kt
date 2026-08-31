package dev.goose.compiler

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.FunctionKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Nullability
import com.google.devtools.ksp.validate

private const val GOOSE_UI = "dev.goose.runtime.GooseUi"
private const val MODIFIER = "androidx.compose.ui.Modifier"
private const val COMPOSABLE = "androidx.compose.runtime.Composable"
private const val MAVERICKS_VM = "com.airbnb.mvrx.MavericksViewModel"
private const val NAVIGATOR = "dev.goose.runtime.Navigator"
private const val ASSISTED_FACTORY = "dev.zacsweers.metro.AssistedFactory"
private const val QUALIFIER = "dev.zacsweers.metro.Qualifier"
private const val SERIALIZABLE = "kotlinx.serialization.Serializable"
private const val APP_SCOPE = "dev.zacsweers.metro.AppScope"

/** Internal names in the generated ScreenEntry lambda, reserved so user params can't shadow them. */
private val RESERVED_PARAM_NAMES = setOf("gooseScreen", "gooseModifier")

/**
 * Turns `@GooseUi(SomeScreen::class)` composable functions into the full Goose registration:
 * a Metro-contributed module whose @Provides function returns the ScreenEntry adapter, keyed by
 * the screen class. Parameters are wired by type:
 * - the screen class -> the screen being rendered
 * - [Modifier] -> the host's modifier
 * - a MavericksViewModel with a nested @AssistedFactory `(State, Navigator) -> VM` -> a generated
 *   `screenViewModel` call (the factory itself is injected from the graph)
 * - that ViewModel's state class (exact type) -> a generated `collectAsState().value`
 * - anything else -> an injected provider parameter, resolved from the graph at compile time,
 *   with Metro qualifier annotations copied over
 *
 * Supported grammar: public or internal, top-level, non-suspend, non-generic, non-extension
 * `@Composable` functions taking a `@Serializable` screen. Anything else is a compile error
 * with a message naming the rule. Same-name functions are detected within a module; two Gradle
 * modules sharing one package could still generate colliding class names, so keep feature
 * packages distinct per module (the namespace every Android library module already declares).
 */
class GooseUiProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    /** (package, module name) pairs generated so far, to fail fast on name collisions. */
    private val generatedModules = mutableSetOf<Pair<String, String>>()

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

    private fun validateShape(function: KSFunctionDeclaration): Boolean {
        fun err(message: String): Boolean {
            logger.error("@GooseUi: $message", function)
            return false
        }
        if (function.functionKind != FunctionKind.TOP_LEVEL) {
            return err("must be a top-level function (member and local functions are not supported)")
        }
        if (Modifier.PRIVATE in function.modifiers) {
            return err("must not be private (the generated registration lives in a separate file)")
        }
        if (function.extensionReceiver != null) {
            return err("must not be an extension function")
        }
        if (function.typeParameters.isNotEmpty()) {
            return err("must not be generic")
        }
        if (Modifier.SUSPEND in function.modifiers) {
            return err("must not be suspend")
        }
        val composable = function.annotations.any {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == COMPOSABLE
        }
        if (!composable) {
            return err("must be @Composable")
        }
        return true
    }

    private fun generate(function: KSFunctionDeclaration) {
        if (!validateShape(function)) return
        val annotation = function.annotations.first { it.annotationType.resolve().declaration.qualifiedName?.asString() == GOOSE_UI }
        val screenType = annotation.classArgument("screen", index = 0) ?: run {
            logger.error("@GooseUi requires a screen class argument", function)
            return
        }
        val screenFqn = screenType.declaration.qualifiedName?.asString() ?: run {
            logger.error("@GooseUi screen class has no qualified name", function)
            return
        }
        val scopeFqn = annotation.classArgument("scope", index = 1)
            ?.declaration?.qualifiedName?.asString()
            .let { if (it == null || it == "kotlin.Unit") APP_SCOPE else it }
        // Fail here, not on the first state save: back-stack persistence needs the serializer.
        val screenSerializable = screenType.declaration.annotations.any {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == SERIALIZABLE
        }
        if (!screenSerializable) {
            logger.error(
                "@GooseUi screen ${screenType.declaration.simpleName.asString()} must be " +
                    "@Serializable (back stacks persist screens across process death)",
                function,
            )
            return
        }

        val packageName = function.packageName.asString()
        val functionName = function.simpleName.asString()
        val moduleName = "${functionName}GooseModule"
        if (!generatedModules.add(packageName to moduleName)) {
            logger.error(
                "@GooseUi: two annotated functions named '$functionName' in package '$packageName'. " +
                    "Generated registrations are named after the function; rename one.",
                function,
            )
            return
        }

        data class Injected(val name: String, val type: String, val qualifiers: String)
        data class VmParam(val name: String, val vmFqn: String, val stateFqn: String, val factoryFqn: String, val createName: String)

        val paramNames = function.parameters.mapNotNull { it.name?.asString() }
        for (reserved in RESERVED_PARAM_NAMES) {
            if (reserved in paramNames) {
                logger.error("@GooseUi: parameter name '$reserved' is reserved for generated code", function)
                return
            }
        }

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
            val factory = findGooseFactory(decl, stateFqn, vmFqn, param) ?: return
            if (factory.first.isEmpty()) {
                logger.error(
                    "@GooseUi cannot wire '$name: ${decl.simpleName.asString()}': no nested " +
                        "@AssistedFactory with a `(initialState, navigator)` create function. For a " +
                        "screen-scoped ViewModel, add one (see the goose README). For a flow-shared " +
                        "ViewModel, call flowViewModel() inside the function instead of taking it as " +
                        "a parameter.",
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
                    callArgs += "$name = gooseScreen as $screenFqn"
                }
                typeFqn == MODIFIER -> {
                    hasModifierParam = true
                    callArgs += "$name = gooseModifier"
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
                    injected += Injected(name, type.render(), param.renderQualifiers(function) ?: return)
                    callArgs += "$name = $name"
                }
            }
        }

        val usedNames = paramNames.toMutableSet()
        val factoryParamNames = vmParams.associate { vm ->
            var candidate = "${vm.name}Factory"
            while (!usedNames.add(candidate)) candidate += "_"
            vm.name to candidate
        }
        for (vm in vmParams) {
            injected += Injected(factoryParamNames.getValue(vm.name), vm.factoryFqn, "")
        }

        val providerParams = injected.joinToString(",\n            ") { "${it.qualifiers}${it.name}: ${it.type}" }
        val lambdaParams = buildString {
            append(if (hasScreenParam || vmParams.isNotEmpty()) "gooseScreen" else "_")
            append(", ")
            append(if (hasModifierParam) "gooseModifier" else "_")
        }
        val imports = buildString {
            if (vmParams.isNotEmpty()) {
                append("\nimport dev.goose.mavericks.screenViewModel")
                if (callArgs.any { ".collectAsState()" in it }) append("\nimport com.airbnb.mvrx.compose.collectAsState")
            }
        }
        val vmDeclarations = vmParams.joinToString("") { vm ->
            "\n            val ${vm.name} = screenViewModel(gooseScreen, ${vm.vmFqn}::class.java, ${vm.stateFqn}::class.java, ${factoryParamNames.getValue(vm.name)}::${vm.createName})"
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
                |@dev.zacsweers.metro.ContributesTo($scopeFqn::class)
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
     * Finds the ViewModel's goose assisted factory among nested classifiers annotated
     * `@AssistedFactory`: an abstract function of shape `(stateType, Navigator) -> vmType`
     * (declared or inherited; other non-abstract members are fine). Returns (factory FQN,
     * create-function name), ("" to "") when the VM has none (flow-shared VMs deliberately
     * don't), or null after logging when more than one matches.
     */
    private fun findGooseFactory(
        vm: KSClassDeclaration,
        stateFqn: String,
        vmFqn: String,
        errorSite: KSValueParameter,
    ): Pair<String, String>? {
        val matches = mutableListOf<Pair<String, String>>()
        for (nested in vm.declarations.filterIsInstance<KSClassDeclaration>()) {
            val isAssistedFactory = nested.annotations.any {
                it.annotationType.resolve().declaration.qualifiedName?.asString() == ASSISTED_FACTORY
            }
            if (!isAssistedFactory) continue
            val factoryFqn = nested.qualifiedName?.asString() ?: continue
            for (create in nested.getAllFunctions().filter { it.isAbstract }) {
                val params = create.parameters
                if (params.size != 2) continue
                if (params[0].type.resolve().declaration.qualifiedName?.asString() != stateFqn) continue
                if (params[1].type.resolve().declaration.qualifiedName?.asString() != NAVIGATOR) continue
                if (create.returnType?.resolve()?.declaration?.qualifiedName?.asString() != vmFqn) continue
                matches += factoryFqn to create.simpleName.asString()
            }
        }
        return when {
            matches.isEmpty() -> "" to ""
            matches.size == 1 -> matches.single()
            else -> {
                logger.error(
                    "@GooseUi: $vmFqn has ${matches.size} @AssistedFactory create functions matching " +
                        "(state, navigator); keep exactly one",
                    errorSite,
                )
                null
            }
        }
    }

    /**
     * Renders the Metro qualifier annotations on an injected parameter (`@Named("x") ` etc.) so
     * they carry over to the generated provider parameter. Returns "" when there are none, null
     * after logging when a qualifier has arguments the generator cannot render.
     */
    private fun KSValueParameter.renderQualifiers(function: KSFunctionDeclaration): String? {
        val rendered = StringBuilder()
        for (ann in annotations) {
            val annDecl = ann.annotationType.resolve().declaration
            val isQualifier = annDecl.annotations.any {
                it.annotationType.resolve().declaration.qualifiedName?.asString() == QUALIFIER
            }
            if (!isQualifier) continue
            val fqn = annDecl.qualifiedName?.asString() ?: continue
            val args = ann.arguments.mapNotNull { arg ->
                val value = arg.value ?: return@mapNotNull null
                val renderedValue = renderAnnotationValue(value) ?: run {
                    logger.error(
                        "@GooseUi: cannot render qualifier @${annDecl.simpleName.asString()} argument " +
                            "'${arg.name?.asString()}' (${value::class.simpleName}); use a hand-written " +
                            "@Provides registration for this screen",
                        function,
                    )
                    return null
                }
                arg.name?.asString()?.let { "$it = $renderedValue" } ?: renderedValue
            }
            rendered.append("@$fqn")
            if (args.isNotEmpty()) rendered.append(args.joinToString(", ", "(", ")"))
            rendered.append(" ")
        }
        return rendered.toString()
    }

    private fun renderAnnotationValue(value: Any): String? = when (value) {
        is String -> "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        is Char -> "'$value'"
        is Boolean, is Int, is Short, is Byte, is Double -> value.toString()
        is Long -> "${value}L"
        is Float -> "${value}f"
        is KSType -> value.declaration.qualifiedName?.asString()?.let { "$it::class" }
        is KSDeclaration -> value.qualifiedName?.asString()
        is List<*> -> value.map { it?.let(::renderAnnotationValue) ?: return null }
            .joinToString(", ", "[", "]")
        else -> null
    }

    private fun KSAnnotation.classArgument(name: String, index: Int): KSType? =
        (arguments.firstOrNull { it.name?.asString() == name }
            ?: arguments.getOrNull(index)?.takeIf { it.name == null })?.value as? KSType

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
