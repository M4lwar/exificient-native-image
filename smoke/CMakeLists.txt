cmake_minimum_required(VERSION 3.15)
project(smoke CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(exificient CONFIG REQUIRED)

add_executable(smoke smoke.cpp)
target_link_libraries(smoke PRIVATE exificient::exificient)
