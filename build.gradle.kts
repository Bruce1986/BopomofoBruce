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
    alias(libs.plugins.room) apply false
    alias(libs.plugins.protobuf) apply false
    alias(libs.plugins.ktfmt) apply true
}

// Gemini-review: intentional, do NOT re-suggest convention plugin —
// W0-1 skeleton 階段以 `subprojects {}` 集中三組設定（ktfmt / JUnit Platform /
// Kotlin compilerOptions）是 PR review loop 過程中刻意收斂的結果。Codex round 1
// 與 Gemini round 1+7 都建議改成 build-logic Convention Plugin（為了 Project
// Isolation / Gradle 9 Isolated Projects），記為 DEVPLAN §11 W3 後 follow-up：
// 此 PR scope 是 8 module 空殼，導入 build-logic include build 會把 PR 範圍
// 拉爆且觸碰其他 wave 的設定。
// 目前 declarative 寫法 cc-safe（apply plugin + tasks.withType + extensions
// 純 config-time，無 cross-project state read），Configuration Cache 已驗證可用。
// W3-1 / W3-2 之後會單獨開 PR 抽 build-logic。
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
