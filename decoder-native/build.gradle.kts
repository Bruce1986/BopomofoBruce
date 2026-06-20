plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.bopomofobruce.decoder.nativ"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            // libchewing JNI is only shipped on the ABIs we ship in :app.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        // CMake wiring is intentionally deferred to W1-A. Leaving the block
        // out (rather than adding a placeholder CMakeLists.txt) keeps the
        // skeleton green without inventing native sources that don't exist
        // yet. W1-A will add `externalNativeBuild { cmake { ... } }` here.
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            kotlin.srcDirs("src/test/kotlin")
        }
    }
}

dependencies {
    implementation(project(":common"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit4)
}
