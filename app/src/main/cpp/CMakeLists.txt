cmake_minimum_required(VERSION 3.22.1)
project(animationstudio C CXX)

# ---------------------------------------------------------------------------------------------
# libmypaint (licença ISC): motor de pincéis do MyPaint.
#
# O código é obtido do repositório oficial, fixado a um commit exato (build reproduzível e verificável).
# Não há cópia do código no nosso repositório; ver docs/LIBMYPAINT.md (origem, licença, limitações).
# Para compilar sem rede, aponte -DFETCHCONTENT_SOURCE_DIR_LIBMYPAINT=<pasta do libmypaint nesse commit>.
# ---------------------------------------------------------------------------------------------
include(FetchContent)
FetchContent_Declare(libmypaint
    GIT_REPOSITORY https://github.com/mypaint/libmypaint.git
    GIT_TAG d5a88fbe6649d5ec776bc42ec8c1f4bb29d7fd7f)
FetchContent_GetProperties(libmypaint)
if(NOT libmypaint_POPULATED)
    FetchContent_Populate(libmypaint)
endif()

# Cabeçalhos das definições dos pincéis: o libmypaint gera-os a partir de brushsettings.json.
set(MYPAINT_GEN_DIR "${CMAKE_CURRENT_BINARY_DIR}/mypaint_gen")
file(MAKE_DIRECTORY "${MYPAINT_GEN_DIR}")
if(NOT EXISTS "${MYPAINT_GEN_DIR}/brushsettings-gen.h" OR NOT EXISTS "${MYPAINT_GEN_DIR}/mypaint-brush-settings-gen.h")
    find_program(MYPAINT_PYTHON NAMES python3 python)
    if(NOT MYPAINT_PYTHON)
        message(FATAL_ERROR "O libmypaint precisa de Python 3 para gerar os cabeçalhos das definições dos pincéis.")
    endif()
    execute_process(
        COMMAND "${MYPAINT_PYTHON}" generate.py
                "${MYPAINT_GEN_DIR}/mypaint-brush-settings-gen.h"
                "${MYPAINT_GEN_DIR}/brushsettings-gen.h"
        WORKING_DIRECTORY "${libmypaint_SOURCE_DIR}"
        RESULT_VARIABLE MYPAINT_GEN_RESULT
        OUTPUT_QUIET)
    if(NOT MYPAINT_GEN_RESULT EQUAL 0)
        message(FATAL_ERROR "Falha ao gerar os cabeçalhos do libmypaint (generate.py devolveu ${MYPAINT_GEN_RESULT}).")
    endif()
endif()

# Só o núcleo dos pincéis: o resto (tiled surface, GEGL, GLib) não é necessário, porque o desenho dos toques
# é feito em Kotlin. config.h e json.h em mypaint_compat/ substituem o autotools e o json-c.
set(MYPAINT_SOURCES
    mypaint-brush.c
    mypaint-brush-settings.c
    mypaint-mapping.c
    mypaint-surface.c
    mypaint-rectangle.c
    mypaint-matrix.c
    mypaint-symmetry.c
    helpers.c
    rng-double.c)
list(TRANSFORM MYPAINT_SOURCES PREPEND "${libmypaint_SOURCE_DIR}/")

add_library(mypaint STATIC ${MYPAINT_SOURCES})
set_target_properties(mypaint PROPERTIES POSITION_INDEPENDENT_CODE ON C_STANDARD 11)
target_include_directories(mypaint PUBLIC
    "${CMAKE_CURRENT_SOURCE_DIR}/mypaint_compat"
    "${MYPAINT_GEN_DIR}"
    "${libmypaint_SOURCE_DIR}")
target_compile_options(mypaint PRIVATE -O2 -w)
target_link_libraries(mypaint PUBLIC m)

# ---------------------------------------------------------------------------------------------
# AnimBrush: biblioteca de pincéis própria (C++17, sem dependências, ~500 pincéis).
#
# É opcional: se existir a pasta animbrush/ ao lado deste ficheiro (com include/ e src/ do AnimBrush), compila-se e
# liga-se ao motor nativo; se não existir, a app compila e funciona sem os pincéis do AnimBrush.
# Como a juntar: docs/ANIMBRUSH.md.
# ---------------------------------------------------------------------------------------------
set(ANIMBRUSH_DIR "${CMAKE_CURRENT_SOURCE_DIR}/animbrush")
set(ANIMBRUSH_ENABLED FALSE)
if(EXISTS "${ANIMBRUSH_DIR}/include/animbrush/animbrush.hpp" AND EXISTS "${ANIMBRUSH_DIR}/src/library.cpp")
    file(GLOB ANIMBRUSH_SOURCES "${ANIMBRUSH_DIR}/src/*.cpp" "${ANIMBRUSH_DIR}/src/brushes/*.cpp")
    add_library(animbrush STATIC ${ANIMBRUSH_SOURCES})
    set_target_properties(animbrush PROPERTIES POSITION_INDEPENDENT_CODE ON CXX_STANDARD 17 CXX_STANDARD_REQUIRED ON)
    target_include_directories(animbrush PUBLIC "${ANIMBRUSH_DIR}/include")
    target_compile_options(animbrush PRIVATE -O2 -w)
    set(ANIMBRUSH_ENABLED TRUE)
    message(STATUS "AnimBrush: ligado a partir de ${ANIMBRUSH_DIR}")
else()
    message(STATUS "AnimBrush: pasta animbrush/ não encontrada; a compilar sem os pincéis do AnimBrush")
endif()

# ---------------------------------------------------------------------------------------------
# Motor nativo da app.
# ---------------------------------------------------------------------------------------------
add_library(animation_engine SHARED native-lib.cpp mypaint_engine.cpp animbrush_jni.cpp)
set_target_properties(animation_engine PROPERTIES CXX_STANDARD 17)

find_library(log-lib log)
target_link_libraries(animation_engine mypaint ${log-lib})

if(ANIMBRUSH_ENABLED)
    target_link_libraries(animation_engine animbrush)
    target_compile_definitions(animation_engine PRIVATE ANIMBRUSH_ENABLED=1)
endif()
