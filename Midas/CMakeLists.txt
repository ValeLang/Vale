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
		src/c-compiler/conec.c
		src/c-compiler/coneopts.c

		src/c-compiler/shared/error.c
		src/c-compiler/shared/fileio.c
		src/c-compiler/shared/memory.c
		src/c-compiler/shared/options.c
		src/c-compiler/shared/timer.c
		src/c-compiler/shared/utf8.c

		src/c-compiler/ir/clone.c
		src/c-compiler/ir/flow.c
		src/c-compiler/ir/iexp.c
		src/c-compiler/ir/inode.c
		src/c-compiler/ir/instype.c
		src/c-compiler/ir/itype.c
		src/c-compiler/ir/name.c
		src/c-compiler/ir/namespace.c
		src/c-compiler/ir/nametbl.c
		src/c-compiler/ir/nodelist.c
		src/c-compiler/ir/nodes.c

		src/c-compiler/ir/stmt/break.c
		src/c-compiler/ir/stmt/continue.c
		src/c-compiler/ir/stmt/fielddcl.c
		src/c-compiler/ir/stmt/fndcl.c
		src/c-compiler/ir/stmt/intrinsic.c
		src/c-compiler/ir/stmt/module.c
		src/c-compiler/ir/stmt/return.c
		src/c-compiler/ir/stmt/vardcl.c

		src/c-compiler/ir/exp/allocate.c
		src/c-compiler/ir/exp/assign.c
		src/c-compiler/ir/exp/block.c
		src/c-compiler/ir/exp/borrow.c
		src/c-compiler/ir/exp/cast.c
		src/c-compiler/ir/exp/deref.c
		src/c-compiler/ir/exp/fncall.c
		src/c-compiler/ir/exp/if.c
		src/c-compiler/ir/exp/literal.c
		src/c-compiler/ir/exp/logic.c
		src/c-compiler/ir/exp/loop.c
		src/c-compiler/ir/exp/namedval.c
		src/c-compiler/ir/exp/nameuse.c
		src/c-compiler/ir/exp/sizeof.c
		src/c-compiler/ir/exp/typelit.c
		src/c-compiler/ir/exp/vtuple.c

		src/c-compiler/ir/types/array.c
		src/c-compiler/ir/types/arrayref.c
		src/c-compiler/ir/types/enum.c
		src/c-compiler/ir/types/fnsig.c
		src/c-compiler/ir/types/lifetime.c
		src/c-compiler/ir/types/number.c
		src/c-compiler/ir/types/permission.c
		src/c-compiler/ir/types/pointer.c
		src/c-compiler/ir/types/reference.c
		src/c-compiler/ir/types/struct.c
		src/c-compiler/ir/types/ttuple.c
		src/c-compiler/ir/types/typedef.c
		src/c-compiler/ir/types/void.c

		src/c-compiler/ir/meta/genvardcl.c
		src/c-compiler/ir/meta/generic.c

		src/c-compiler/corelib/corelib.c
		src/c-compiler/corelib/corenumber.c

		src/c-compiler/parser/lexer.c
		src/c-compiler/parser/parser.c
		src/c-compiler/parser/parseflow.c
		src/c-compiler/parser/parseexpr.c
		src/c-compiler/parser/parsetype.c

		src/c-compiler/genllvm/genllvm.c
)

target_link_libraries(valec ${llvm_libs} nlohmann_json::nlohmann_json)

