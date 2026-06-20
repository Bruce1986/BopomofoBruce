// Top-level build file. Plugins declared here with `apply false` so child
// modules can opt in via `alias(libs.plugins.<id>)` without re-resolving them.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktfmt) apply true
}

// Apply ktfmt to every subproject so `./gradlew ktfmtCheck` covers the repo.
subprojects {
    apply(plugin = rootProject.libs.plugins.ktfmt.get().pluginId)

    extensions.configure<com.ncorti.ktfmt.gradle.KtfmtExtension> {
        kotlinLangStyle()
    }
}
