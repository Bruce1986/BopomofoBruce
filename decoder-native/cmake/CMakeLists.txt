# :decoder-native — builds libbpmf.so
#
# libchewing >= 0.9.0 (vendored here at v0.12.0, see cmake/libchewing/) has
# its core rewritten in Rust; the public C API (`chewing_capi` crate) is
# still C-ABI, produced via cbindgen. See ADR-0006 for why this build
# pipeline exists instead of a pure-C CMake build (ADR-0001's original
# assumption).
#
# We follow libchewing's own upstream CMakeLists.txt recipe: use Corrosion
# (https://github.com/corrosion-rs/corrosion) to cross-compile the
# `chewing_capi` Rust crate as a staticlib for the current Android ABI, then
# link a small C wrapper (src/bpmf_wrapper.c) on top of it into a single
# `libbpmf.so`. We do NOT reuse upstream's own `add_subdirectory(..)` of the
# whole libchewing CMakeLists.txt — that would also try to build docs,
# tests, and the full multi-symbol libchewing.so we don't need; instead we
# import just the one crate we need directly.

cmake_minimum_required(VERSION 3.22.1)
project(bpmf LANGUAGES C)

set(CMAKE_C_STANDARD 99)
set(LIBCHEWING_DIR "${CMAKE_CURRENT_SOURCE_DIR}/libchewing")

if(NOT EXISTS "${LIBCHEWING_DIR}/Cargo.toml")
    message(FATAL_ERROR
        "libchewing submodule not found at ${LIBCHEWING_DIR}. Run "
        "`git submodule update --init --recursive decoder-native/cmake/libchewing` "
        "from the repo root first.")
endif()

# --- Corrosion: drives `cargo build` for the current Android ABI ---------
include(FetchContent)
FetchContent_Declare(
    Corrosion
    GIT_REPOSITORY https://github.com/corrosion-rs/corrosion.git
    GIT_TAG 1499b14e4906a2890f5cee1547c8848db261753d # v0.6.1
)
FetchContent_MakeAvailable(Corrosion)

# chewing_capi's [lib] crate-type is ["rlib", "staticlib"] (no "cdylib") —
# see cmake/libchewing/capi/Cargo.toml. We only need the staticlib to link
# into our own libbpmf.so.
corrosion_import_crate(
    MANIFEST_PATH "${LIBCHEWING_DIR}/Cargo.toml"
    CRATES chewing_capi
    NO_DEFAULT_FEATURES
)

# --- libbpmf.so: our thin C wrapper + the linked-in Rust staticlib -------
# bpmf_test_jni.c is a TEST-ONLY JNI bridge (see its header comment) that
# lets connectedAndroidTest exercise the four bpmf_* functions on-device; it
# is not part of the public C API in bpmf.h and W2-A is free to delete it.
#
# It is compiled in ONLY when the consuming Gradle variant explicitly opts in
# via -DBPMF_BUILD_TEST_BRIDGE=ON (see decoder-native/build.gradle.kts, which
# sets it on the debug variant only). This keeps the four
# Java_..._BpmfTestBridge_nativeTest* JNI symbols — including nativeTestFree(),
# which trusts its jlong argument as a raw pointer to free() — out of the
# release .so entirely, instead of merely being undocumented in a shipping
# artifact.
#
# The gate is deliberately NOT `CMAKE_BUILD_TYPE STREQUAL "Debug"`: AGP derives
# CMAKE_BUILD_TYPE by first matching the variant NAME against
# debug/release/relwithdebinfo/minsizerel, and only falls back to
# `if (isDebuggable) "Debug" else "RelWithDebInfo"` for names that match none of
# those (see AOSP CreateCxxVariantModel.kt). A future debuggable build type
# whose name contains neither keyword — e.g. a QA `staging` flavour — would
# therefore receive CMAKE_BUILD_TYPE=Debug and silently ship the test bridge.
# An explicit per-variant flag has no such hidden coupling.
#
# Verify with `nm -D libbpmf.so | grep nativeTest` on the release output
# whenever this gate changes (see devlog A5/A10).
set(BPMF_SOURCES src/bpmf_wrapper.c)
if(BPMF_BUILD_TEST_BRIDGE)
    list(APPEND BPMF_SOURCES src/bpmf_test_jni.c)
endif()
add_library(bpmf SHARED ${BPMF_SOURCES})
target_include_directories(bpmf
    PRIVATE
        include
        "${LIBCHEWING_DIR}/capi/include"
)
target_link_libraries(bpmf PRIVATE chewing_capi)

if(ANDROID)
    # env_logger (a chewing_capi dependency) and Rust's panic machinery
    # both end up wanting liblog on Android.
    target_link_libraries(bpmf PRIVATE log)
endif()

# --- Symbol visibility: only export bpmf.h's own 4 functions -------------
# corrosion_import_crate() links chewing_capi's staticlib (and, transitively,
# its entire Rust dependency tree — regex, aho-corasick, gimli, jiff, std,
# …) into libbpmf.so with no visibility control of its own: every
# GLOBAL-visibility symbol in that archive lands in the final .so's dynamic
# symbol table verbatim, alongside our own bpmf_* API. Without this section
# libbpmf.so re-exports the entire libchewing C API (~130 chewing_*
# functions) plus thousands of Rust-mangled symbols — contradicting bpmf.h's
# own "nothing else from libchewing is re-exported" contract. See R1 in the
# 20260811 devlog entry for the measured before/after symbol counts.
#
# We follow the exact recipe upstream libchewing's own CMakeLists.txt uses
# for its libchewing.so target (capi/src/symbols-elf.map via
# LINKER:-version-script) rather than inventing a different mechanism.
#
# BPMF_TESTBRIDGE_SYMBOLS_BLOCK: when the test bridge is compiled in, its
# four Java_..._BpmfTestBridge_nativeTest* JNI entry points must stay
# dynamically visible — the JVM resolves JNI-exported native methods via
# dlsym() by their mangled Java_* name at runtime, so hiding them would
# break connectedAndroidTest with UnsatisfiedLinkError. They are listed
# explicitly (not via a wildcard) so the version script itself documents
# exactly which four symbols the test-only surface adds.
if(BPMF_BUILD_TEST_BRIDGE)
    set(BPMF_TESTBRIDGE_SYMBOLS_BLOCK "    Java_com_bopomofobruce_decoder_nativ_testbridge_BpmfTestBridge_nativeTestInit;
    Java_com_bopomofobruce_decoder_nativ_testbridge_BpmfTestBridge_nativeTestInput;
    Java_com_bopomofobruce_decoder_nativ_testbridge_BpmfTestBridge_nativeTestCommit;
    Java_com_bopomofobruce_decoder_nativ_testbridge_BpmfTestBridge_nativeTestFree;")
else()
    set(BPMF_TESTBRIDGE_SYMBOLS_BLOCK "")
endif()

set(BPMF_VERSION_SCRIPT "${CMAKE_CURRENT_BINARY_DIR}/bpmf.map")
configure_file(
    "${CMAKE_CURRENT_SOURCE_DIR}/src/bpmf.map.in"
    "${BPMF_VERSION_SCRIPT}"
    @ONLY
)

if(CMAKE_C_COMPILER_ID MATCHES "^(GNU|Clang)$")
    # NOT -fvisibility=hidden: Android's <jni.h> JNIEXPORT macro already
    # expands to an explicit `__attribute__((visibility("default")))` (so
    # the JNI entry points in bpmf_test_jni.c would survive -fvisibility=
    # hidden regardless), but bpmf_wrapper.c's own bpmf_init/input/commit/
    # free carry no such attribute — under -fvisibility=hidden the compiler
    # marks their ELF symbols STV_HIDDEN, which --version-script's `global:`
    # list cannot undo (a version script can only demote a default-visibility
    # symbol to local, not promote a hidden one back to default). Verified
    # by trying it: with -fvisibility=hidden added here, `nm -D
    # --defined-only` on the release .so dropped to 0 symbols total — it hid
    # bpmf_init/input/commit/free too, not just the Rust/libchewing symbols
    # this section exists to hide. The version script's own `local: *;`
    # catch-all already hides everything not explicitly listed under
    # `global:`, so -fvisibility=hidden would be redundant even if it
    # worked.
    target_compile_options(bpmf PRIVATE
        -ffunction-sections
        -fdata-sections
    )
    target_link_options(bpmf PRIVATE
        "LINKER:--version-script,${BPMF_VERSION_SCRIPT}"
        # Hides symbols originating from statically-linked archives (i.e.
        # chewing_capi's .a and its dependency .rlibs) from the dynamic
        # symbol table too — belt-and-suspenders alongside the version
        # script, and independent of it: --exclude-libs applies regardless
        # of the source-level visibility the archive's objects were built
        # with (we don't control chewing_capi's own rustc flags).
        "LINKER:--exclude-libs,ALL"
        # Lets the linker drop Rust dependency code that bpmf_wrapper.c
        # never calls into, now that the version script also stops that
        # code being pinned in the dynamic table as a side effect.
        "LINKER:--gc-sections"
    )
    set_target_properties(bpmf PROPERTIES
        LINK_DEPENDS "${BPMF_VERSION_SCRIPT}"
    )
endif()
