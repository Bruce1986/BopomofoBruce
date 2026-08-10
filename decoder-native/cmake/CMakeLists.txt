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
# It is compiled in ONLY for Debug builds (AGP sets CMAKE_BUILD_TYPE per
# variant; connectedAndroidTest runs against the debug variant by default).
# This keeps the four Java_..._BpmfTestBridge_nativeTest* JNI symbols —
# including nativeTestFree(), which trusts its jlong argument as a raw
# pointer to free() — out of the release .so entirely, instead of merely
# being undocumented in a shipping artifact. Verify with
# `nm -D libbpmf.so | grep nativeTest` on the release output whenever this
# gate changes (see devlog A5).
set(BPMF_SOURCES src/bpmf_wrapper.c)
if(CMAKE_BUILD_TYPE STREQUAL "Debug")
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
