import java.net.URI

plugins {
    alias(libs.plugins.studio.android.application)
    // Navigation keys are @Serializable so the back stacks survive process death.
    alias(libs.plugins.kotlin.serialization)
}

val configuredApiUrl = providers.gradleProperty("studio.apiBaseUrl").orElse("")
val configuredAuthMode = providers.gradleProperty("studio.authMode").orElse("firebase")
val allowLanDevelopment = providers.gradleProperty("studio.allowLanDevelopment").orElse("false").map { it == "true" }
val lanHost = if (allowLanDevelopment.get()) {
    val uri = URI(configuredApiUrl.get())
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
            uri.scheme == "http" && configuredAuthMode.get() == "development",
    ) { "LAN development requires an RFC1918 IPv4 HTTP URL and studio.authMode=development" }
    host
} else {
    "localhost"
}
val lanResources = layout.buildDirectory.dir("generated/lanDebugResources")
val generateLanDebugResources = tasks.register("generateLanDebugResources") {
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
tasks.matching { it.name == "preDebugBuild" }.configureEach { dependsOn(generateLanDebugResources) }
fun buildString(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    buildFeatures { buildConfig = true }
    namespace = "com.illiamelnyk.studio"
    sourceSets.getByName("debug").res.directories.add(lanResources.get().asFile.absolutePath)

    defaultConfig {
        applicationId = "com.illiamelnyk.studio"
        versionCode = 1
        versionName = "0.1.0"
        mapOf(
            "FIREBASE_APP_ID" to "studio.firebaseAppId",
            "FIREBASE_API_KEY" to "studio.firebaseApiKey",
            "FIREBASE_PROJECT_ID" to "studio.firebaseProjectId",
            "GOOGLE_WEB_CLIENT_ID" to "studio.googleWebClientId",
        ).forEach { (field, property) ->
            buildConfigField("String", field, buildString(providers.gradleProperty(property).orElse("").get()))
        }
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "API_ALLOW_LAN_DEVELOPMENT", allowLanDevelopment.get().toString())
            buildConfigField(
                "String",
                "API_BASE_URL",
                buildString(
                    configuredApiUrl.get().ifBlank {
                        "http://10.0.2.2:8787"
                    },
                ),
            )
            buildConfigField(
                "String",
                "API_AUTH_MODE",
                buildString(if (configuredApiUrl.get().isBlank()) "development" else configuredAuthMode.get()),
            )
        }
        release {
            buildConfigField("boolean", "API_ALLOW_LAN_DEVELOPMENT", "false")
            buildConfigField("String", "API_BASE_URL", buildString(configuredApiUrl.get()))
            buildConfigField(
                "String",
                "API_AUTH_MODE",
                buildString(if (configuredApiUrl.get().isBlank()) "disabled" else configuredAuthMode.get()),
            )
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the debug key so the demo APK installs without a release keystore.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
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
