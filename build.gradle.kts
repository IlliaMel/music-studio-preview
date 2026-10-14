import com.diffplug.spotless.LineEnding

// Plugins are declared here with `apply false` so their classes live in one classpath
// shared by every module and by the convention plugins in build-logic.
plugins {
    // Gives the root project a `check` task, which Spotless hooks `spotlessCheck` into.
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.spotless)
}

// Formatting is checked once for the whole repository, build-logic included. ktlint reads
// its settings from .editorconfig, the same file the IDE formatter uses.
spotless {
    // .gitattributes stores every text file with LF, so there is nothing to read from git.
    lineEndings = LineEnding.UNIX
    val ktlintVersion = libs.versions.ktlint.get()

    // Only source folders are walked. A `**` pattern over the repository also descends into build
    // folders, and the build tasks running next to spotlessCheck rewrite them at the same time.
    kotlin {
        target(
            fileTree("app/src") { include("**/*.kt") },
            fileTree("build-logic/convention/src") { include("**/*.kt") },
        )
        ktlint(ktlintVersion).customRuleSets(listOf(libs.compose.rules.ktlint.get().toString()))
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts", "build-logic/*.gradle.kts", "build-logic/convention/*.gradle.kts")
        ktlint(ktlintVersion)
    }
}
