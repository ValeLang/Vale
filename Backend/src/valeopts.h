
#ifndef valeopts_h
#define valeopts_h

#include <string>
#include <stdint.h>
#include <stddef.h>
#include <vector>
#include <unordered_map>
#include <unordered_set>

enum class RegionOverride {
  NAIVE_RC,
  RESILIENT_V3,
  SAFE_FASTEST,
  SAFE,
  FAST
};

enum class ValeOptimizationLevel {
    O0,
    O1,
    O2,
    O2i,
    O3
};


// Compiler options
struct ValeOptions {
    std::string outputDir;

    std::string triple;
    std::string cpu;
    std::string features;

    void* data = nullptr; // User-defined data for unit test callbacks

    // Boolean flags
    bool wasm = false;        // 1=WebAssembly
    ValeOptimizationLevel optLevel = ValeOptimizationLevel::O2i;   // O0-O3
    bool library = false;    // 1=generate a C-API compatible static library
    bool pic = false;        // Compile using position independent code
    bool verify = false;        // Verify LLVM IR
    bool debug = false;
    bool print_asm = false;        // Print out assembly file
    bool print_llvmir = false;    // Print out LLVM IR
    bool docs = false;            // Generate code documentation
    bool census = false;    // Enable census checking
    bool flares = false;    // Enable flare output
    bool fastCrash = false;    // Enable single-instruction crash, a bit faster
    int generationSize = 32;    // Size of generation integer, in bits.
    bool elideChecksForKnownLive = true;    // Elide checks for static-analysis-known live
    bool elideChecksForRegions = true;    // Elide checks for immutable regions
    bool includeBoundsChecks = true;
    bool useAtomicRc = false;
    bool overrideKnownLiveTrue = false;    // Enables generational heap
    bool printMemOverhead = false;    // Enables generational heap
    bool enableReplaying = false;    // Enables deterministic replaying
    bool enableSideCalling = false;    // Enables side calling, used for fearless FFI
    std::unordered_map<std::string, std::unordered_set<std::string>> projectNameToReplayWhitelistedExterns;

    RegionOverride regionOverride = RegionOverride::RESILIENT_V3;
};

int valeOptSet(ValeOptions *opt, int *argc, char **argv);

#endif
