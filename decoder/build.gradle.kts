plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.bopomofobruce.decoder"
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

// Room schema 輸出：避免 KSP 編譯警告，並追蹤 schema 演進。
// W2-A (`feat/w2-decoder-jni`) 真正定義 entity 後，schema JSON 會 emit 到
// decoder/schemas/<version>.json 並提交版控（migration test 會用）。
// （ksp 是 Project-level extension，必須放在 top-level，不是 defaultConfig 內。）
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(project(":common"))
    implementation(project(":decoder-native"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
