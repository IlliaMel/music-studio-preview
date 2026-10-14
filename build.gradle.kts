// Plugins are declared here with `apply false` so their classes live in one classpath
// shared by every module and by the convention plugins in build-logic.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
