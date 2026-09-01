import com.android.build.api.dsl.CommonExtension

// Shared Android configuration for every Goose module. Applied by the other convention plugins,
// never directly. AGP 9's built-in Kotlin is used — no org.jetbrains.kotlin.android here.
extensions.configure<CommonExtension>("android") {
    compileSdk = 37
    compileSdkMinor = 0
    defaultConfig.minSdk = 26
    compileOptions.sourceCompatibility = JavaVersion.VERSION_17
    compileOptions.targetCompatibility = JavaVersion.VERSION_17
}

// Goose's compiler plugin: generates readResolve on Serializable objects (object screens), so
// they stay singletons across Java deserialization and skip the hand-written boilerplate.
configurations.matching { it.name == "kotlinCompilerPluginClasspath" }.all {
    project.dependencies.add(name, project.project(":goose-compiler-plugin"))
}
