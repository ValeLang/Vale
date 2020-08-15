
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
    bool wasm;        // 1=WebAssembly
    bool release;    // 0=debug (no optimizations). 1=release (default)
    bool library;    // 1=generate a C-API compatible static library
    bool pic;        // Compile using position independent code
    bool verify;        // Verify LLVM IR
    bool print_asm;        // Print out assembly file
    bool print_llvmir;    // Print out LLVM IR
    bool docs;            // Generate code documentation
    bool census;    // Enable census checking
    bool flares;    // Enable flare output
    bool fastmode;    // Fast mode, no constraint ref counters!
} ValeOptions;

int valeOptSet(ValeOptions *opt, int *argc, char **argv);

#endif