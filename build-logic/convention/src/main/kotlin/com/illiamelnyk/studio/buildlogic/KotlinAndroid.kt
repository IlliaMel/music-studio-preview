package com.illiamelnyk.studio.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

object StudioSdk {
    const val COMPILE = 37
    const val TARGET = 37

    /** Android 8.0: adaptive icons and notification channels without version checks. */
    const val MIN = 26
}

private val JAVA_VERSION = JavaVersion.VERSION_17

/** Shared by application and library modules: compile SDK and Java version. */
internal fun CommonExtension.configureAndroidCommon() {
    compileSdk = StudioSdk.COMPILE
    compileOptions.sourceCompatibility = JAVA_VERSION
    compileOptions.targetCompatibility = JAVA_VERSION
}

/** AGP 9 ships Kotlin built in, so the compile tasks are configured directly. */
internal fun Project.configureKotlin() {
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}
