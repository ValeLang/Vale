
#ifndef valeopts_h
#define valeopts_h

#include <string>
#include <stdint.h>
#include <stddef.h>

// Compiler options
typedef struct ValeOptions {
    char* srcpath;    // Full path
    std::string srcDir;    // Just the directory
    std::string srcNameNoExt;    // Just the name of the file, without extension
    std::string srcDirAndNameNoExt;    // Just the name of the file, without extension

    char* output;

    const char* triple;
    const char* cpu;
    const char* features;

    void* data; // User-defined data for unit test callbacks

    // Boolean flags
    int wasm;        // 1=WebAssembly
    int release;    // 0=debug (no optimizations). 1=release (default)
    int library;    // 1=generate a C-API compatible static library
    int pic;        // Compile using position independent code
    int verify;        // Verify LLVM IR
    int print_asm;        // Print out assembly file
    int print_llvmir;    // Print out LLVM IR
    int docs;            // Generate code documentation
} ValeOptions;

int valeOptSet(ValeOptions *opt, int *argc, char **argv);

#endif