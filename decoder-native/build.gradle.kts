plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.bopomofobruce.decoder.nativ"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            // libchewing JNI is only shipped on the ABIs we ship in :app.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        externalNativeBuild {
            cmake {
                // AGP passes CMAKE_ANDROID_ARCH_ABI / CMAKE_SYSTEM_NAME=Android
                // automatically per abiFilters entry; Corrosion (see
                // cmake/CMakeLists.txt) reads those to pick the matching Rust
                // target triple.
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("cmake/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":common"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.mockk)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

// Downloads + sha256-verifies the libchewing dictionary data into
// src/main/assets/chewing/ (see scripts/fetch_chewing_data.sh + ADR-0006).
// Wired ahead of asset merging so `assembleDebug` / unit & instrumented
// tests are reproducible from a clean checkout without a manual step.
val fetchChewingData by tasks.registering(Exec::class) {
    description = "Downloads the prebuilt libchewing dictionary data (word.dat/tsi.dat)."
    workingDir = projectDir
    commandLine("scripts/fetch_chewing_data.sh")
    inputs.file("scripts/fetch_chewing_data.sh")
    outputs.dir("src/main/assets/chewing")
}

tasks.matching { it.name.startsWith("merge") && it.name.contains("Assets") }.configureEach {
    dependsOn(fetchChewingData)
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(fetchChewingData) }
