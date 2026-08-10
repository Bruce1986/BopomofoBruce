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

    buildTypes {
        // The TEST-ONLY JNI bridge (cmake/src/bpmf_test_jni.c) is opted into
        // explicitly here, per build type, rather than inferred inside CMake
        // from CMAKE_BUILD_TYPE. AGP derives CMAKE_BUILD_TYPE by matching the
        // variant NAME against debug/release/relwithdebinfo/minsizerel and only
        // falls back to the debuggable flag for names matching none of them
        // (AOSP CreateCxxVariantModel.kt) — so a future debuggable build type
        // named e.g. "staging" would have received CMAKE_BUILD_TYPE=Debug and
        // silently shipped the bridge. Any new build type must make this choice
        // deliberately, and re-verify with
        // `nm -D libbpmf.so | grep nativeTest`. See ADR-0006 / devlog A10.
        debug {
            externalNativeBuild { cmake { arguments += "-DBPMF_BUILD_TEST_BRIDGE=ON" } }
        }
        release {
            externalNativeBuild { cmake { arguments += "-DBPMF_BUILD_TEST_BRIDGE=OFF" } }
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

// Sole owner/producer of the src/main/assets/chewing directory itself
// (mkdir only, no network). Both fetchChewingData and any lint-only
// consumer depend on this, so exactly one task ever declares
// `outputs.dir("src/main/assets/chewing")` — avoiding a Gradle "overlapping
// outputs" validation failure that a second directory-owning task would
// trigger whenever both were scheduled in the same invocation (e.g.
// `./gradlew build`, which runs `lint` and `assembleDebug` together).
// See ADR-0006 / devlog A12.
val ensureChewingDataDir by tasks.registering {
    description = "Creates src/main/assets/chewing (no download) so consumers that only need the directory to exist — e.g. lint's asset model builder — don't require network access."
    val dir = layout.projectDirectory.dir("src/main/assets/chewing")
    outputs.dir(dir)
    doLast { dir.asFile.mkdirs() }
}

// Downloads + sha256-verifies the libchewing dictionary data into
// src/main/assets/chewing/ (see scripts/fetch_chewing_data.sh + ADR-0006).
// Wired ahead of asset merging so `assembleDebug` / instrumented tests are
// reproducible from a clean checkout without a manual step. Declares its
// outputs as the two specific files (not the whole directory, which
// ensureChewingDataDir already owns) so the two tasks don't have
// overlapping declared outputs even though ensureChewingDataDir always
// runs first via the explicit dependsOn below.
val fetchChewingData by tasks.registering(Exec::class) {
    description = "Downloads the prebuilt libchewing dictionary data (word.dat/tsi.dat)."
    dependsOn(ensureChewingDataDir)
    workingDir = projectDir
    commandLine("scripts/fetch_chewing_data.sh")
    inputs.file("scripts/fetch_chewing_data.sh")
    outputs.file("src/main/assets/chewing/word.dat")
    outputs.file("src/main/assets/chewing/tsi.dat")
}

// NOT wired to preBuild: preBuild runs for every variant task graph,
// including plain-JVM `testDebugUnitTest`/`testReleaseUnitTest` (e.g.
// ChewingDataPathTest), which touch no assets and must be runnable offline
// from a clean checkout. See ADR-0006 / devlog A4/A12.
//
// The asset-merge pipeline (merge*Assets, then a later package*Assets step
// that re-reads src/main/assets independently of merge's output) reads
// src/main/assets directly, so Gradle's task validation requires *some*
// declared dependency or it fails the build with an "implicit dependency"
// error. But package*Assets is shared by both real builds
// (assembleDebug/Release) *and* lint's per-variant unit-test/androidTest
// analysis tasks (lintAnalyzeDebugUnitTest/lintAnalyzeDebugAndroidTest —
// AGP wires those to depend on package*Assets unconditionally, for the
// compiled test artifacts' resource classpath). Since it's the same task
// instance either way, we can't make it fetch real data only for "real
// build" callers — so it depends on the lightweight mkdir task, keeping
// *lint's* path fully offline (see devlog A12 dry-run evidence: this is
// exactly the edge that made `:lint` transitively require network before
// this fix).
//
// mustRunAfter(fetchChewingData) is ordering-only: it does NOT pull
// fetchChewingData into a plain `:lint` invocation's task graph, but when a
// real-build task also schedules fetchChewingData in the same invocation
// (see below), this fixes the run order — merge/package never reads a
// half-written directory — and satisfies Gradle's overlapping-outputs
// validation between the two tasks.
tasks.matching { it.name.contains("Assets") }.configureEach {
    dependsOn(ensureChewingDataDir)
    mustRunAfter(fetchChewingData)
}

// lint's model builder (lintAnalyze*/lintReport*/lint*/lint) also reads the
// source set's assets dir directly (not through the asset-merge pipeline),
// so it needs the same treatment as above, for the same reason: it only
// cares that the directory exists, not that it holds the real dictionary
// content.
tasks
    .matching { it.name.contains("Lint", ignoreCase = true) }
    .configureEach {
        dependsOn(ensureChewingDataDir)
        mustRunAfter(fetchChewingData)
    }

// Tasks that actually produce or install a real artifact DO need the real
// dictionary content — wire fetchChewingData in directly here rather than
// via the shared Assets/Lint task-name matching above.
//
// `connected*`/`install*` are listed explicitly and NOT assumed to inherit
// from `assembleDebug`: `--dry-run` on `:decoder-native:connectedAndroidTest`
// showed fetchChewingData absent from its task graph when only the two
// assemble* lifecycle tasks were wired, i.e. the instrumented test would have
// packaged an empty assets dir on a clean checkout (it only passes locally
// because the dictionary happens to already be on disk). Verified after this
// change: lint = absent, testDebugUnitTest = absent, assembleDebug = present,
// connectedAndroidTest = present. See ADR-0006 / devlog A12.
tasks
    .matching {
        it.name == "assembleDebug" ||
            it.name == "assembleRelease" ||
            it.name.startsWith("connected") ||
            it.name.startsWith("install")
    }
    .configureEach { dependsOn(fetchChewingData) }
