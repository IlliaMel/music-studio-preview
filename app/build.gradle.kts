plugins {
    alias(libs.plugins.studio.android.application)
    // Navigation keys are @Serializable so the back stacks survive process death.
    alias(libs.plugins.kotlin.serialization)
}

val configuredApiUrl = providers.gradleProperty("studio.apiBaseUrl").orElse("")
val configuredAuthMode = providers.gradleProperty("studio.authMode").orElse("firebase")
fun buildString(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    buildFeatures { buildConfig = true }
    namespace = "com.illiamelnyk.studio"

    defaultConfig {
        applicationId = "com.illiamelnyk.studio"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
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
