import com.android.build.api.dsl.ApplicationExtension
import com.illiamelnyk.studio.buildlogic.StudioSdk
import com.illiamelnyk.studio.buildlogic.configureAndroidCommon
import com.illiamelnyk.studio.buildlogic.configureKotlin
import com.illiamelnyk.studio.buildlogic.lib
import com.illiamelnyk.studio.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** An Android application with Compose: SDK levels, Java 17 and the Compose BOM without Material. */
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
        }
        configureKotlin()

        dependencies {
            add("implementation", platform(libs.lib("androidx-compose-bom")))
            add("implementation", libs.lib("androidx-compose-foundation"))
            add("implementation", libs.lib("androidx-compose-ui"))
            add("implementation", libs.lib("androidx-compose-ui-tooling-preview"))
            add("debugImplementation", libs.lib("androidx-compose-ui-tooling"))
        }
    }
}
