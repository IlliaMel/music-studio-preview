import com.android.build.api.variant.BuildConfigField
import java.net.URI

plugins {
    alias(libs.plugins.studio.android.application)
    // Navigation keys are @Serializable so the back stacks survive process death.
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val devApiUrl = providers.gradleProperty("studio.dev.apiBaseUrl").orElse("")
val prodApiUrl = providers.gradleProperty("studio.prod.apiBaseUrl").orElse("")
// Old local-development properties remain scoped to devDebug. Exact flavor properties win.
val devDebugApiUrl = providers.gradleProperty("studio.dev.apiBaseUrl")
    .orElse(providers.gradleProperty("studio.apiBaseUrl")).orElse("")
val devDebugAuthMode = providers.gradleProperty("studio.authMode").orElse("firebase")
val allowLanDevelopment = providers.gradleProperty("studio.allowLanDevelopment").orElse("false").map { it == "true" }
val sharedFirebase = mapOf(
    "FIREBASE_API_KEY" to "studio.firebaseApiKey",
    "FIREBASE_PROJECT_ID" to "studio.firebaseProjectId",
    "GOOGLE_WEB_CLIENT_ID" to "studio.googleWebClientId",
).mapValues { (_, property) -> providers.gradleProperty(property).orElse("").get() }
val firebaseAppIds = listOf("dev", "prod").associateWith { environment ->
    providers.gradleProperty("studio.$environment.firebaseAppId").orElse("").get()
}
val releaseSigning = listOf("storeFile", "storePassword", "keyAlias", "keyPassword").associateWith { name ->
    providers.gradleProperty("studio.release.$name").orElse("").get()
}
val hasReleaseSigning = releaseSigning.values.all { it.isNotBlank() }

val lanHost = if (allowLanDevelopment.get()) {
    val uri = URI(devDebugApiUrl.get())
    val host = uri.host ?: error("LAN development requires an IPv4 API host")
    val parts = host.split('.')
    val octets = parts.map { it.toIntOrNull() ?: -1 }
    require(
        octets.size == 4 && parts.all { Regex("0|[1-9][0-9]{0,2}").matches(it) } &&
            octets.all { it in 0..255 } &&
            (
                octets[0] == 10 || (octets[0] == 172 && octets[1] in 16..31) ||
                    (octets[0] == 192 && octets[1] == 168)
                ) &&
            uri.scheme == "http" && devDebugAuthMode.get() == "development" &&
            uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            (uri.rawPath.isNullOrEmpty() || uri.rawPath == "/"),
    ) { "LAN development requires an RFC1918 IPv4 HTTP dev URL and studio.authMode=development" }
    host
} else {
    "localhost"
}
// Host tests do not depend on the environment, so devDebug runs them all; prodDebug only checks
// its own generated configuration instead of repeating the whole suite.
tasks.withType<Test>().configureEach {
    if (name == "testProdDebugUnitTest") filter.includeTestsMatching("*.FlavorApiConfigurationTest")
}

val lanResources = layout.buildDirectory.dir("generated/devDebugNetworkResources")
val generateDevDebugNetworkResources = tasks.register("generateDevDebugNetworkResources") {
    val hostsForResource = if (allowLanDevelopment.get()) {
        listOf(
            lanHost,
        )
    } else {
        listOf("10.0.2.2", "localhost", "127.0.0.1")
    }
    val outputDirectory = lanResources.get().asFile
    inputs.property("hosts", hostsForResource)
    outputs.dir(outputDirectory)
    doLast {
        val target = outputDirectory.resolve("xml/debug_network_security.xml")
        target.parentFile.mkdirs()
        val domains = hostsForResource.joinToString("\n") { "<domain includeSubdomains=\"false\">$it</domain>" }
        target.writeText(
            """
            <network-security-config>
                <base-config cleartextTrafficPermitted="false" />
                <domain-config cleartextTrafficPermitted="true">
                    $domains
                </domain-config>
            </network-security-config>
            """.trimIndent(),
        )
    }
}
tasks.matching { it.name == "preDevDebugBuild" }.configureEach { dependsOn(generateDevDebugNetworkResources) }
fun buildString(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
    .replace("\n", "\\n").replace("\r", "\\r") + "\""

android {
    buildFeatures { buildConfig = true }
    namespace = "com.illiamelnyk.studio"
    sourceSets.maybeCreate("devDebug").res.directories.add(lanResources.get().asFile.absolutePath)

    defaultConfig {
        applicationId = "com.illiamelnyk.studio"
        versionCode = 1
        versionName = "0.1.0"
        sharedFirebase.forEach { (field, value) -> buildConfigField("String", field, buildString(value)) }
    }
    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("String", "FIREBASE_APP_ID", buildString(firebaseAppIds.getValue("dev")))
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("String", "FIREBASE_APP_ID", buildString(firebaseAppIds.getValue("prod")))
        }
    }
    val configuredReleaseSigning = if (hasReleaseSigning) {
        signingConfigs.create("configuredRelease") {
            storeFile = file(releaseSigning.getValue("storeFile"))
            storePassword = releaseSigning.getValue("storePassword")
            keyAlias = releaseSigning.getValue("keyAlias")
            keyPassword = releaseSigning.getValue("keyPassword")
        }
    } else {
        null
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // No debug-key fallback: CI can assemble unsigned releases without signing credentials.
            signingConfig = configuredReleaseSigning
        }
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        val environment = variant.productFlavors.single { it.first == "environment" }.second
        val localDebug = environment == "dev" && variant.buildType == "debug"
        val configuredUrl = when {
            localDebug -> devDebugApiUrl.get()
            environment == "dev" -> devApiUrl.get()
            else -> prodApiUrl.get()
        }
        val firebaseConfigured = firebaseAppIds.getValue(environment).isNotBlank() &&
            sharedFirebase.values.all { it.isNotBlank() }
        val mode = when {
            localDebug && configuredUrl.isBlank() -> "development"
            localDebug && devDebugAuthMode.get() == "development" -> "development"
            localDebug && devDebugAuthMode.get() != "firebase" -> "disabled"
            configuredUrl.isNotBlank() && firebaseConfigured -> "firebase"
            else -> "disabled"
        }
        val fields = requireNotNull(variant.buildConfigFields)
        fields.put(
            "API_BASE_URL",
            BuildConfigField(
                "String",
                buildString(
                    configuredUrl.ifBlank { if (localDebug) "http://10.0.2.2:8787" else "" },
                ),
                null,
            ),
        )
        fields.put("API_AUTH_MODE", BuildConfigField("String", buildString(mode), null))
        fields.put(
            "API_ALLOW_LAN_DEVELOPMENT",
            BuildConfigField("boolean", (localDebug && allowLanDevelopment.get()).toString(), null),
        )
    }
}

dependencies {
    implementation(libs.koin.androidx.workmanager)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.okhttp.mockwebserver)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.google.id)
    implementation(libs.haze)
    implementation(libs.haze.blur)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.core)

    testImplementation(platform(libs.koin.bom))
    testImplementation(libs.koin.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
