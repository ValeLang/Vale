cmake_minimum_required(VERSION 3.4.3)
project(midas)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -g -std=c++17")

find_package(LLVM 11.1 REQUIRED CONFIG)
add_definitions(${LLVM_DEFINITIONS})
include_directories(
	${LLVM_INCLUDE_DIRS}
	"${CMAKE_SOURCE_DIR}/src/c-compiler/"
)

message(STATUS "LLVM VERSION : ${LLVM_PACKAGE_VERSION}")
message(STATUS "Using LLVMConfig.cmake in: ${LLVM_DIR}")


# See either:
#   llvm-config-11 --libfiles --link-static
#   llvm-config --components | tr " " "\n"
# for what we could use here.
# We'll keep it to a minimum for now, because different machines
# seem to support different components.
set(LLVM_LINK_COMPONENTS
		Core
		Support
		IRReader
		x86asmparser x86codegen x86desc x86disassembler x86info
)

llvm_map_components_to_libnames(llvm_libs ${LLVM_LINK_COMPONENTS})

# llvm_map_components_to_libnames(llvm_libs support core irreader)

add_executable(midas
		src/c-compiler/vale.cpp
		src/c-compiler/globalstate.cpp
		src/c-compiler/metal/ast.cpp
		src/c-compiler/metal/readjson.cpp
		src/c-compiler/metal/types.cpp
		src/c-compiler/translatetype.cpp
        src/c-compiler/valeopts.cpp
        src/c-compiler/midasfunctions.cpp
		src/c-compiler/mainFunction.cpp
		src/c-compiler/externs.cpp

		src/c-compiler/utils/counters.cpp

		src/c-compiler/function/function.cpp
		src/c-compiler/function/boundary.cpp
		src/c-compiler/function/expression.cpp

		src/c-compiler/function/expressions/call.cpp
		src/c-compiler/function/expressions/interfacecall.cpp
		src/c-compiler/function/expressions/construct.cpp
		src/c-compiler/function/expressions/destructure.cpp
		src/c-compiler/function/expressions/block.cpp
		src/c-compiler/function/expressions/discard.cpp
		src/c-compiler/function/expressions/externs.cpp
		src/c-compiler/function/expressions/if.cpp
		src/c-compiler/function/expressions/constantstr.cpp
		src/c-compiler/function/expressions/localload.cpp
		src/c-compiler/function/expressions/while.cpp
		src/c-compiler/function/expressions/newimmruntimesizearray.cpp
		src/c-compiler/function/expressions/newmutruntimesizearray.cpp
		src/c-compiler/function/expressions/staticarrayfromcallable.cpp
		src/c-compiler/function/expressions/newarrayfromvalues.cpp

		src/c-compiler/function/expressions/shared/elements.cpp
		src/c-compiler/function/expressions/shared/members.cpp
		src/c-compiler/function/expressions/shared/shared.cpp
		src/c-compiler/utils/branch.cpp
		src/c-compiler/function/expressions/shared/string.cpp
        src/c-compiler/region/common/heap.cpp
		src/c-compiler/region/common/controlblock.cpp
		src/c-compiler/function/expressions/shared/ref.cpp

		src/c-compiler/region/common/common.cpp
		src/c-compiler/region/common/defaultlayout/structs.cpp
        src/c-compiler/region/common/defaultlayout/structsrouter.cpp
		src/c-compiler/region/rcimm/rcimm.cpp
		src/c-compiler/region/common/lgtweaks/lgtweaks.cpp
		src/c-compiler/region/common/wrcweaks/wrcweaks.cpp
		src/c-compiler/region/common/hgm/hgm.cpp
		src/c-compiler/region/common/fatweaks/fatweaks.cpp
		src/c-compiler/region/assist/assist.cpp
		src/c-compiler/region/resilientv3/resilientv3.cpp
		src/c-compiler/region/resilientv4/resilientv4.cpp
		src/c-compiler/region/naiverc/naiverc.cpp
		src/c-compiler/region/unsafe/unsafe.cpp
		src/c-compiler/region/linear/linear.cpp
		src/c-compiler/region/linear/linearstructs.cpp
		src/c-compiler/region/regions.cpp

        src/c-compiler/fileio.cpp
        src/c-compiler/options.cpp src/c-compiler/mainFunction.cpp src/c-compiler/externs.cpp)

target_link_libraries(midas ${llvm_libs})

target_compile_features(midas PRIVATE cxx_std_17)
