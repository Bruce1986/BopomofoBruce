plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.bopomofobruce.keyboards"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // TODO(W2)：這裡語意上「應該」是 api 而不是 implementation——:keyboards 的 public API
    // 確實洩漏了 :common 的型別（Keyboards.zhuyin4x10Portrait 的型別就是 KeyboardDef）。
    // 2026-09-08 裁決：**維持 implementation**，理由有三：
    //   1. 現況可編譯——:keyboards 的唯二消費者 :ime 與 :app 都已各自宣告 :common。
    //   2. repo 內五個模組都是這個慣例，只改這一個反而更不一致；要改就該五個一起改，
    //      當成獨立的一次重構。
    //   3. 失敗模式是**編譯期爆炸**（寫不出 KeyboardDef 這個型別名），不是靜默錯誤，
    //      所以「未來消費者忘了宣告」的成本很低。
    // 另一面：api 會擴大 :common 變更觸發的重新編譯範圍。
    implementation(project(":common"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
