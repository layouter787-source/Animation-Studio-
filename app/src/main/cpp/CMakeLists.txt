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
# Motor nativo da app.
# ---------------------------------------------------------------------------------------------
add_library(animation_engine SHARED native-lib.cpp mypaint_engine.cpp)
set_target_properties(animation_engine PROPERTIES CXX_STANDARD 17)

find_library(log-lib log)
target_link_libraries(animation_engine mypaint ${log-lib})
