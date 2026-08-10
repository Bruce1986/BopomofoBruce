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
// from a clean checkout. See ADR-0006 / devlog A4.
//
// package*Assets — NOT :decoder-native's own assembleDebug/assembleRelease
// lifecycle task — is what a downstream consumer (:app, via :decoder)
// actually schedules when it builds against this module as a regular AAR
// dependency. `--dry-run` evidence: both `:app:assembleDebug --dry-run`
// and `:app:assembleRelease --dry-run` schedule
// `:decoder-native:packageDebugAssets` but never
// `:decoder-native:assembleDebug`/`assembleRelease`. A prior round of this
// fix wired fetchChewingData only onto the assemble*/connected*/install*
// lifecycle tasks below and left package*Assets depending on the
// lightweight mkdir-only task — which meant a clean-checkout :app build
// packaged an *empty* assets/chewing/ directory; it only worked locally
// because word.dat/tsi.dat were already on disk from a previous fetch.
// package*Assets must therefore depend on the real fetch, not just the
// mkdir.
//
// package*Assets is ALSO what AGP unconditionally wires
// lintAnalyzeDebugUnitTest/lintAnalyzeDebugAndroidTest to (for the
// compiled test artifacts' resource classpath) — that wiring lives inside
// AGP and this build script cannot sever it. Since it is the same task
// instance either way, making package*Assets depend on the real fetch
// means `:lint` now transitively requires network too. That is an
// accepted, deliberate trade-off, not an oversight: APK correctness
// outranks lint being offline-runnable. See ADR-0006 for the record of
// this decision (downgraded from "solved" to "known trade-off").
tasks.matching { it.name.contains("Assets") }.configureEach {
    dependsOn(ensureChewingDataDir)
    dependsOn(fetchChewingData)
}

// lint's own model-builder tasks (lintAnalyzeDebug, generateDebugLintReportModel — as opposed
// to lintAnalyzeDebugUnitTest/lintAnalyzeDebugAndroidTest, which reach the assets dir
// transitively through package*Assets above) also read src/main/assets/chewing directly and do
// NOT contain "Assets" in their task name, so Gradle's task-validation still flags them as an
// "implicit dependency" without this. Wiring them straight to fetchChewingData (not just
// ensureChewingDataDir) is consistent with the accepted trade-off above: :lint already requires
// network transitively via package*Assets, so there is no remaining "keep lint offline" case
// left to preserve by splitting these two off onto the mkdir-only task.
tasks
    .matching { it.name.contains("Lint", ignoreCase = true) }
    .configureEach { dependsOn(fetchChewingData) }

// Tasks that actually produce or install a real artifact also get
// fetchChewingData wired in directly. This is redundant with the
// Assets-matching block above (assemble*/connected*/install* all pull in
// package*Assets transitively) but is kept as an explicit, self-documenting
// safety net — `--dry-run` on `:decoder-native:connectedAndroidTest` is
// asserted to include fetchChewingData in CI/manual checks, and this line
// is what guarantees that independent of how AGP wires package*Assets in a
// future version.
tasks
    .matching {
        it.name == "assembleDebug" ||
            it.name == "assembleRelease" ||
            it.name.startsWith("connected") ||
            it.name.startsWith("install")
    }
    .configureEach { dependsOn(fetchChewingData) }
