plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
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

// Room Gradle Plugin（Room 2.6.0+ 官方）取代手動 ksp arg：
// - 自動配置 KSP（無需 room.schemaLocation 參數）
// - 自動把 schema 目錄配給 androidTest 任務（MigrationTestHelper 直接吃）
// - Inputs/Outputs 追蹤與 Build Cache 正確
// W2-A (`feat/w2-decoder-jni`) 真正定義 entity 後，schema JSON 會 emit 到
// decoder/schemas/<version>.json 並提交版控。
room { schemaDirectory("$projectDir/schemas") }

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
