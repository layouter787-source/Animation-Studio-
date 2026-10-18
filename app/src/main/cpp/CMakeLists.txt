cmake_minimum_required(VERSION 3.22.1)
project(animationstudio)

add_library(animation_engine SHARED native-lib.cpp)

find_library(log-lib log)
target_link_libraries(animation_engine ${log-lib})
