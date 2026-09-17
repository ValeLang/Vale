// FFI surface for Rust callers (FrontendRust). All entry points are
// `extern "C"` with explicit default visibility (Backend is built with
// -fvisibility=hidden).

#include <cstdint>

#include "metal/metal_cache_ffi.h"

class MetalCache;
class Program;

// Defined in vale.cpp; declared here to avoid pulling in its giant header set.
int32_t runBackendCompile(MetalCache* metalCache, Program* program, int argc, char** argv);

// Defined in metal_cache_ffi.cpp; reaches into the opaque CacheOwner wrapper.
extern "C" MetalCache* metal_cache_ffi_inner(MetalCacheHandle*);

// AST-driven entry. Caller has already populated MetalCache and Program
// (via the metal_cache_ffi.h builders + FrontendRust's MetalLowerer). argv
// carries non-input options for valeOptSet (e.g. --output_dir,
// --region_override); inputs come from the Program parameter. Caller retains
// ownership of cache and program; they are NOT freed by this function.
extern "C" __attribute__((visibility("default")))
int32_t backend_compile_program(
    MetalCacheHandle* cacheH, ProgramHandle* programH,
    int argc, char** argv) {
  return runBackendCompile(
      metal_cache_ffi_inner(cacheH),
      reinterpret_cast<Program*>(programH),
      argc, argv);
}
