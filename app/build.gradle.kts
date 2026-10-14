plugins {
    alias(libs.plugins.studio.android.application)
}

android {
    namespace = "com.illiamelnyk.studio"

    defaultConfig {
        applicationId = "com.illiamelnyk.studio"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
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
}
