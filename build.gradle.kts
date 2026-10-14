import com.diffplug.spotless.LineEnding

// Plugins are declared here with `apply false` so their classes live in one classpath
// shared by every module and by the convention plugins in build-logic.
plugins {
    // Gives the root project a `check` task, which Spotless hooks `spotlessCheck` into.
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.spotless)
}

// Formatting is checked once for the whole repository, build-logic included. ktlint reads
// its settings from .editorconfig, the same file the IDE formatter uses.
spotless {
    // .gitattributes stores every text file with LF, so there is nothing to read from git.
    lineEndings = LineEnding.UNIX
    val generated = listOf("**/build/**", "**/.gradle/**", "**/.kotlin/**")
    val ktlintVersion = libs.versions.ktlint.get()

    kotlin {
        target("**/*.kt")
        targetExclude(generated)
        ktlint(ktlintVersion).customRuleSets(listOf(libs.compose.rules.ktlint.get().toString()))
    }
    kotlinGradle {
        target("**/*.kts")
        targetExclude(generated)
        ktlint(ktlintVersion)
    }
}
