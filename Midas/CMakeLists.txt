cmake_minimum_required(VERSION 3.11)
project(valec)
cmake_policy(VERSION 3.11)

find_package(LLVM 7 REQUIRED CONFIG)

message(STATUS "Found LLVM ${LLVM_PACKAGE_VERSION}")
message(STATUS "Using LLVMConfig.cmake in: ${LLVM_DIR}")

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -g -std=c++11")

include(FetchContent)

FetchContent_Declare(
	json
	GIT_REPOSITORY https://github.com/nlohmann/json
	GIT_TAG v3.7.3
)

FetchContent_GetProperties(json)
if(NOT json_POPULATED)
	FetchContent_Populate(json)
	add_subdirectory(${json_SOURCE_DIR} ${json_BINARY_DIR} EXCLUDE_FROM_ALL)
endif()

include_directories(
		${LLVM_INCLUDE_DIRS}
		"${CMAKE_SOURCE_DIR}/src/c-compiler/"
)
add_definitions(${LLVM_DEFINITIONS})

# See either:
#   llvm-config-7 --libfiles --link-static
#   llvm-config --components | tr " " "\n"
# for what we could use here.
# We'll keep it to a minimum for now, because different machines
# seem to support different components.
set(LLVM_LINK_COMPONENTS
		Core
		Passes

#		WebAssemblyAsmParser
#		WebAssemblyAsmPrinter
#		WebAssemblyCodeGen
#		WebAssemblyDesc
#		WebAssemblyDisassembler
#		WebAssemblyInfo

		X86AsmParser
		X86AsmPrinter
		X86CodeGen
		X86Desc
		X86Disassembler
		X86Info
		X86Utils
)

llvm_map_components_to_libnames(llvm_libs ${LLVM_LINK_COMPONENTS})

add_executable(valec
		src/c-compiler/vale.cpp
		src/c-compiler/function.cpp
		src/c-compiler/struct.cpp
		src/c-compiler/readjson.cpp
		src/c-compiler/translatetype.cpp
		src/c-compiler/main.cpp
		src/c-compiler/coneopts.cpp

		src/c-compiler/utils/fileio.cpp
		src/c-compiler/utils/options.cpp

		src/c-compiler/genllvm.cpp
)

target_link_libraries(valec ${llvm_libs} nlohmann_json::nlohmann_json)

