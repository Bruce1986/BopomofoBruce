// :common 是純 Kotlin JVM module（沒有 Android API 依賴）：
// - W0-2 定義介面契約（KeyData / Candidate / InputState / ZhuyinDecoder /
//   KeyboardTheme / ImeContextProvider 等）必須與 Android framework 解耦，
//   才能讓 :keyboards / :theme / :settings 可以用 Fake 做 unit test 與
//   Compose preview，不必啟動 emulator
// - 純 JVM 跳過 AGP（Manifest 合併、資源處理、R/BuildConfig 生成），編譯更快
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // W0-2 凍結的介面目前沒有 Flow / suspend 型別；等真正 API 出現再加 coroutines-core。
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
