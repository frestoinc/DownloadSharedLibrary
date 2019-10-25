# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.
set(openssl_DIR ${CMAKE_SOURCE_DIR}/openssl)
include_directories(${openssl_DIR}/${ANDROID_ABI}/include)
add_library(crypto1 SHARED IMPORTED)
set_target_properties(crypto1 PROPERTIES IMPORTED_LOCATION
        ${openssl_DIR}/${ANDROID_ABI}/libcrypto1.so)

add_library(mytest
        SHARED
        crypto.cpp)
target_link_libraries(mytest crypto1)


# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.