import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.HostTestBuilder
import com.illiamelnyk.studio.buildlogic.StudioSdk
import com.illiamelnyk.studio.buildlogic.configureAndroidCommon
import com.illiamelnyk.studio.buildlogic.configureKotlin
import com.illiamelnyk.studio.buildlogic.lib
import com.illiamelnyk.studio.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * An Android application with Compose: SDK levels, Java 17, the Compose BOM without Material,
 * and Compose UI tests that run on the JVM with Robolectric.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        extensions.configure<ApplicationExtension> {
            configureAndroidCommon(target)
            defaultConfig {
                minSdk = StudioSdk.MIN
                targetSdk = StudioSdk.TARGET
            }
            buildFeatures {
                compose = true
            }
            // Robolectric needs the merged resources and manifest: fonts, strings and the activity
            // that Compose UI tests launch.
            testOptions.unitTests.isIncludeAndroidResources = true
        }
        extensions.configure<ApplicationAndroidComponentsExtension> {
            // Host tests run against debug only: the activity Compose UI tests launch comes from a
            // debug dependency, and release differs in R8 and signing, which host tests don't exercise.
            beforeVariants(selector().withBuildType("release")) { variant ->
                variant.hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.enable = false
            }
        }
        configureKotlin()

        dependencies {
            add("implementation", platform(libs.lib("androidx-compose-bom")))
            add("implementation", libs.lib("androidx-compose-foundation"))
            add("implementation", libs.lib("androidx-compose-ui"))
            add("implementation", libs.lib("androidx-compose-ui-tooling-preview"))
            add("debugImplementation", libs.lib("androidx-compose-ui-tooling"))

            add("testImplementation", libs.lib("androidx-compose-ui-test-junit4"))
            add("testImplementation", libs.lib("robolectric"))
            add("testImplementation", libs.lib("junit"))
            // Declares the empty activity that createComposeRule() launches in the debug manifest.
            add("debugImplementation", libs.lib("androidx-compose-ui-test-manifest"))
        }
    }
}
