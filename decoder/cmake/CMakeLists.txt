# :decoder — libbpmf_jni.so。只 include decoder-native 的 bpmf.h、不連結 libbpmf.so（執行期 dlopen，
# 見 src/bpmf_jni.c），所以不需要 Rust／Corrosion 或 prefab。
cmake_minimum_required(VERSION 3.22.1)
project(bpmf_jni LANGUAGES C)

set(CMAKE_C_STANDARD 99)

add_library(bpmf_jni SHARED src/bpmf_jni.c)
target_include_directories(bpmf_jni PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}/../../decoder-native/cmake/include")
target_link_libraries(bpmf_jni PRIVATE dl)
