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

// Gemini-review: intentional — W0-1 skeleton 階段以 `subprojects {}` 集中 apply
// ktfmt 是有意為之。改成 convention plugin 屬於 §11 DEVPLAN 後續 follow-up
// （Codex round 1 與 Gemini 都建議，已記在 PR description 為 W3 後重構項）。
// 目前 declarative 寫法 cc-safe（plugin id + extensions.configure 純 config-time），
// 暫不阻塞 Configuration Cache。下游若新增需要 cross-project access 的設定，
// 就是時候抽 build-logic include build 了。
subprojects {
    // Gemini-review: intentional — 必須走 `rootProject.libs`。subprojects {} 是
    // root project scope（lazy 配置 child projects），`libs` extension 只在 root
    // project 註冊，subproject 不會繼承。直接寫 `libs.plugins.ktfmt` 會在 verify
    // 跑出「Extension with name 'libs' does not exist」（已實驗過，commit 58be14c
    // 觸發、bcd789a 修回）。
    apply(plugin = rootProject.libs.plugins.ktfmt.get().pluginId)

    extensions.configure<com.ncorti.ktfmt.gradle.KtfmtExtension> { kotlinLangStyle() }

    // 全 repo unit test 走 JUnit Platform（依賴在各 module 加 junit-jupiter-*）。
    tasks.withType<Test>().configureEach { useJUnitPlatform() }

    // 集中 Kotlin compiler 設定，避免 8 個 module 重複 kotlinOptions block。
    // 走新的 compilerOptions DSL（Kotlin 2.0 已 deprecate kotlinOptions）。
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
}
