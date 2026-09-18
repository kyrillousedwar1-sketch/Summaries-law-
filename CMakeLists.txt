cmake_minimum_required(VERSION 3.22.1)
project("lawllama")
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(LLAMA_SRC "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp")
if(NOT EXISTS "${LLAMA_SRC}/CMakeLists.txt")
    message(FATAL_ERROR "llama.cpp was not downloaded. Run scripts/prepare_llama.sh before Gradle.")
endif()

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)

add_subdirectory("${LLAMA_SRC}" "${CMAKE_CURRENT_BINARY_DIR}/llama")

add_library(lawllama SHARED native-lib.cpp)
target_include_directories(lawllama PRIVATE "${LLAMA_SRC}/include")
target_link_libraries(lawllama llama log android)
