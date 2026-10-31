cmake_minimum_required(VERSION 3.22.1)
project(cytv_ksc_read C)
add_library(cytv_ksc_read SHARED ksc_read.c)
target_compile_options(cytv_ksc_read PRIVATE -Wall -Wextra -Werror -fvisibility=hidden)
