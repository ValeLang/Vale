/** Cone compiler options
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef coneopts_h
#define coneopts_h

#include <string>
#include <stdint.h>
#include <stddef.h>

// Compiler options
typedef struct ConeOptions {

    char **package_search_paths;
    char **safe_packages;
    // magic_package_t* magic_packages;

    char* srcpath;    // Full path
    std::string srcDir;    // Just the directory
    std::string srcNameNoExt;    // Just the name of the file, without extension
    std::string srcDirAndNameNoExt;    // Just the name of the file, without extension

    char* output;
    char* link_arch;
    char* linker;

    char* triple;
    const char* cpu;
    const char* features;

    //typecheck_t check;

    void* data; // User-defined data for unit test callbacks

    unsigned int ptrsize;    // Size of a pointer (in bits)

    // Boolean flags
    int wasm;        // 1=WebAssembly
    int release;    // 0=debug (no optimizations). 1=release (default)
    int library;    // 1=generate a C-API compatible static library
    int runtimebc;    // Compile with the LLVM bitcode file for the runtime
    int pic;        // Compile using position independent code
    int print_stats;    // Print some compiler statistics
    int verify;        // Verify LLVM IR
    int extfun;        // Set function default linkage to external
    int simple_builtin;    // Use a minimal builtin package
    int strip_debug;    // Strip debug info
    int print_filenames;    // Print source file names as each is processed
    int print_ir;        // Print out IR
    int print_asm;        // Print out assembly file
    int print_llvmir;    // Print out LLVM IR
    int check_tree;        // Verify IR well-formedness
    int lint_llvm;        // Run the LLVM linting pass on generated IR
    int docs;            // Generate code documentation
    int docs_private;    // Generate code docs for private
    int verbosity;       // 0 - 4 (0 = default)

    // verbosity_level verbosity;

    size_t ir_print_width;
    int allow_test_symbols;
    int parse_trace;
} ConeOptions;

int coneOptSet(ConeOptions *opt, int *argc, char **argv);

#endif