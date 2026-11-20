cmake_minimum_required(VERSION 3.22.1)
project(animationstudio C CXX)

# ---------------------------------------------------------------------------------------------
# AnimBrush: biblioteca de pincéis e borrachas própria do projeto (C++17, sem dependências).
#
# O código fica em third_party/AnimBrush (pastas include/ e src/; ver docs/ANIMBRUSH.md). Se a pasta existir, é
# compilada aqui e ligada ao motor da app; se não existir, o build compila na mesma, sem os pincéis AnimBrush.
# ---------------------------------------------------------------------------------------------
get_filename_component(ANIMBRUSH_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/AnimBrush" ABSOLUTE)

add_library(animation_engine SHARED native-lib.cpp animbrush_core.cpp animbrush_jni.cpp)
set_target_properties(animation_engine PROPERTIES CXX_STANDARD 17 CXX_STANDARD_REQUIRED ON)
target_include_directories(animation_engine PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}")

if(EXISTS "${ANIMBRUSH_ROOT}/src/core.cpp" AND EXISTS "${ANIMBRUSH_ROOT}/include/animbrush/animbrush.hpp")
    file(GLOB ANIMBRUSH_SOURCES "${ANIMBRUSH_ROOT}/src/*.cpp" "${ANIMBRUSH_ROOT}/src/brushes/*.cpp")
    add_library(animbrush STATIC ${ANIMBRUSH_SOURCES})
    set_target_properties(animbrush PROPERTIES POSITION_INDEPENDENT_CODE ON CXX_STANDARD 17 CXX_STANDARD_REQUIRED ON)
    target_include_directories(animbrush PUBLIC "${ANIMBRUSH_ROOT}/include")
    target_compile_options(animbrush PRIVATE -O2 -w)
    target_link_libraries(animation_engine animbrush)
    target_compile_definitions(animation_engine PRIVATE ANIMBRUSH_ENABLED=1)
    message(STATUS "AnimBrush: ativa (${ANIMBRUSH_ROOT})")
else()
    message(STATUS "AnimBrush: pasta third_party/AnimBrush não encontrada; build sem os pincéis AnimBrush")
endif()

find_library(log-lib log)
target_link_libraries(animation_engine ${log-lib})
