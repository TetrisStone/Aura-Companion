cmake_minimum_required(VERSION 3.31.6)

project("ai-chat" VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED true)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED true)

set(CMAKE_C_FLAGS   "${CMAKE_C_FLAGS}"   CACHE STRING "" FORCE)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS}" CACHE STRING "" FORCE)

# --------------------------------------------------------------------------
# OpenCL headers and ICD loader build (in-tree for arm64-v8a)
# --------------------------------------------------------------------------
set(OPENCL_HEADERS_DIR ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/opencl-headers)
set(OPENCL_LOADER_DIR ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/opencl-loader)

set(OPENCL_ICD_LOADER_HEADERS_DIR ${OPENCL_HEADERS_DIR} CACHE PATH "Path to OpenCL Headers")
set(BUILD_TESTING OFF CACHE BOOL "Disable OpenCL loader tests")
set(ENABLE_OPENCL_LAYER_SUPPORT OFF CACHE BOOL "Disable layers in OpenCL loader")

# Build OpenCL loader as shared library libOpenCL.so
add_subdirectory(${OPENCL_LOADER_DIR} build-opencl-loader)

# Provide OpenCL package variables so find_package(OpenCL) or ggml-opencl finds it
set(OpenCL_FOUND TRUE CACHE BOOL "OpenCL found" FORCE)
set(OpenCL_INCLUDE_DIR "${OPENCL_HEADERS_DIR}" CACHE PATH "OpenCL include directory" FORCE)
set(OpenCL_INCLUDE_DIRS "${OPENCL_HEADERS_DIR}" CACHE PATH "OpenCL include directories" FORCE)
set(OpenCL_LIBRARY OpenCL CACHE STRING "OpenCL library" FORCE)
set(OpenCL_LIBRARIES OpenCL CACHE STRING "OpenCL libraries" FORCE)

# --------------------------------------------------------------------------
# AI Chat library & llama.cpp
# --------------------------------------------------------------------------

if(DEFINED ANDROID_ABI)
    message(STATUS "Detected Android ABI: ${ANDROID_ABI}")
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        set(GGML_SYSTEM_ARCH "ARM")
        set(GGML_CPU_KLEIDIAI ON)
        set(GGML_OPENMP ON)
    elseif(ANDROID_ABI STREQUAL "x86_64")
        set(GGML_SYSTEM_ARCH "x86")
        set(GGML_CPU_KLEIDIAI OFF)
        set(GGML_OPENMP OFF)
    else()
        message(FATAL_ERROR "Unsupported ABI: ${ANDROID_ABI}")
    endif()
endif()

# Point to third_party/llama.cpp
set(LLAMA_SRC ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp)
add_subdirectory(${LLAMA_SRC} build-llama)

add_library(${CMAKE_PROJECT_NAME} SHARED
        ai_chat.cpp)

target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE
        GGML_SYSTEM_ARCH=${GGML_SYSTEM_ARCH}
        GGML_CPU_KLEIDIAI=$<BOOL:${GGML_CPU_KLEIDIAI}>
        GGML_OPENMP=$<BOOL:${GGML_OPENMP}>
)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${LLAMA_SRC}
        ${LLAMA_SRC}/common
        ${LLAMA_SRC}/include
        ${LLAMA_SRC}/ggml/include
        ${LLAMA_SRC}/ggml/src
        ${OPENCL_HEADERS_DIR})

target_link_libraries(${CMAKE_PROJECT_NAME}
        llama
        llama-common
        android
        log)
