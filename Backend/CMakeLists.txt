cmake_minimum_required(VERSION 3.4.3)
project(backend)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -g -std=c++17 -fvisibility=hidden")

if (MSVC)
	# Set the stack size to 4mb instead of the default 1mb for visual studio
	# because for some reason 1mb is too small for the backend on Windows.
	set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} /STACK:4194304")
endif (MSVC)

find_package(LLVM 13 REQUIRED CONFIG)
add_definitions(${LLVM_DEFINITIONS})
include_directories(
	${LLVM_INCLUDE_DIRS}
	"${CMAKE_SOURCE_DIR}/src/"
)


message(STATUS "LLVM VERSION : ${LLVM_PACKAGE_VERSION}")
message(STATUS "Using LLVMConfig.cmake in: ${LLVM_DIR}")

# See either:
#   llvm-config --libfiles --link-static
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

add_executable(backend
		src/vale.cpp
		src/globalstate.cpp
		src/metal/ast.cpp
		src/metal/readjson.cpp
		src/metal/types.cpp
		src/translatetype.cpp
		src/valeopts.cpp
		src/mainFunction.cpp
		src/externs.cpp

		src/utils/counters.cpp
		src/utils/structlt.cpp

		src/function/function.cpp
		src/function/boundary.cpp
		src/function/expression.cpp

		src/function/expressions/call.cpp
		src/function/expressions/interfacecall.cpp
		src/function/expressions/construct.cpp
		src/function/expressions/destructure.cpp
		src/function/expressions/block.cpp
		src/function/expressions/discard.cpp
		src/function/expressions/externs.cpp
		src/function/expressions/if.cpp
		src/function/expressions/constantstr.cpp
		src/function/expressions/localload.cpp
		src/function/expressions/while.cpp
		src/function/expressions/newimmruntimesizearray.cpp
		src/function/expressions/newmutruntimesizearray.cpp
		src/function/expressions/staticarrayfromcallable.cpp
		src/function/expressions/newarrayfromvalues.cpp

		src/function/expressions/shared/elements.cpp
		src/function/expressions/shared/members.cpp
		src/function/expressions/shared/shared.cpp
		src/utils/branch.cpp
		src/utils/call.cpp
		src/function/expressions/shared/string.cpp
		src/region/common/heap.cpp
		src/region/common/controlblock.cpp
		src/region/urefstructlt.cpp
		src/function/expressions/shared/ref.cpp

		src/region/common/common.cpp
		src/region/common/defaultlayout/structs.cpp
		src/region/common/defaultlayout/structsrouter.cpp
		src/region/rcimm/rcimm.cpp
		src/region/common/lgtweaks/lgtweaks.cpp
		src/region/common/wrcweaks/wrcweaks.cpp
		src/region/common/hgm/hgm.cpp
		src/region/common/fatweaks/fatweaks.cpp
		src/region/assist/assist.cpp
		src/region/resilientv3/resilientv3.cpp
		src/region/resilientv4/resilientv4.cpp
		src/region/naiverc/naiverc.cpp
		src/region/unsafe/unsafe.cpp
		src/region/linear/linear.cpp
		src/region/linear/linearstructs.cpp
		src/region/regions.cpp

		src/fileio.cpp
		src/options.cpp src/mainFunction.cpp src/externs.cpp src/determinism/determinism.cpp src/determinism/determinism.h src/utils/definefunction.cpp src/utils/definefunction.h src/utils/flags.cpp src/utils/flags.h src/simplehash/cppsimplehashmap.cpp src/simplehash/cppsimplehashmap.h src/simplehash/llvmsimplehashmap.cpp src/simplehash/llvmsimplehashmap.h src/utils/llvm.cpp src/utils/llvm.h src/utils/structlt.h)

target_link_libraries(backend ${llvm_libs})

target_compile_features(backend PRIVATE cxx_std_17)
