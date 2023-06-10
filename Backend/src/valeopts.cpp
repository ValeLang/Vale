
#include "valeopts.h"
#include "options.h"

#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include <iostream>

// List of option ids
enum
{
    OPT_DEBUG,
    OPT_OPT_LEVEL,
    OPT_BUILDFLAG,
    OPT_STRIP,
    OPT_PATHS,
    OPT_OUTPUT_DIR,
    OPT_LIBRARY,
    OPT_RUNTIMEBC,
    OPT_PIC,
    OPT_NOPIC,
    OPT_DOCS,
    OPT_DOCS_PUBLIC,

    OPT_SAFE,
    OPT_CPU,
    OPT_FEATURES,
    OPT_WASM,
    OPT_TRIPLE,
    OPT_STATS,
    OPT_LINK_ARCH,
    OPT_LINKER,

    OPT_VERBOSE,
    OPT_IR,
    OPT_ASM,
    OPT_LLVMIR,
    OPT_TRACE,
    OPT_WIDTH,
    OPT_IMMERR,
    OPT_VERIFY,
    OPT_FLARES,
    OPT_FAST_CRASH,
    OPT_GEN_HEAP,
    OPT_GENERATION_SIZE,
    OPT_ELIDE_CHECKS_FOR_KNOWN_LIVE,
    OPT_ELIDE_CHECKS_FOR_REGIONS,
    OPT_INCLUDE_BOUNDS_CHECKS,
    OPT_FORCE_ALL_KNOWN_LIVE,
    OPT_USE_ATOMIC_RC,
    OPT_PRINT_MEM_OVERHEAD,
    OPT_ENABLE_REPLAYING,
    OPT_REPLAY_WHITELIST_EXTERN,
    OPT_ENABLE_SIDE_CALLING,
    OPT_CENSUS,
    OPT_REGION_OVERRIDE,
    OPT_FILENAMES,
    OPT_CHECKTREE,
    OPT_EXTFUN,
    OPT_SIMPLEBUILTIN,
    OPT_LINT_LLVM,

    OPT_BNF,
    OPT_ANTLR,
    OPT_ANTLRRAW
};

static opt_arg_t args[] =
{
    { "debug", 'd', OPT_ARG_NONE, OPT_DEBUG },
    { "define", 'D', OPT_ARG_REQUIRED, OPT_BUILDFLAG },
    { "strip", 's', OPT_ARG_NONE, OPT_STRIP },
    { "path", 'p', OPT_ARG_REQUIRED, OPT_PATHS },
    { "output_dir", '\0', OPT_ARG_REQUIRED, OPT_OUTPUT_DIR },
    { "library", 'l', OPT_ARG_NONE, OPT_LIBRARY },
    { "runtimebc", '\0', OPT_ARG_NONE, OPT_RUNTIMEBC },
    { "pic", '\0', OPT_ARG_NONE, OPT_PIC },
    { "nopic", '\0', OPT_ARG_NONE, OPT_NOPIC },
    { "docs", 'g', OPT_ARG_NONE, OPT_DOCS },
    { "docs_public", '\0', OPT_ARG_NONE, OPT_DOCS_PUBLIC },

    { "safe", '\0', OPT_ARG_OPTIONAL, OPT_SAFE },
    { "cpu", '\0', OPT_ARG_REQUIRED, OPT_CPU },
    { "features", '\0', OPT_ARG_REQUIRED, OPT_FEATURES },
    { "wasm", '\0', OPT_ARG_NONE, OPT_WASM },
    { "triple", '\0', OPT_ARG_REQUIRED, OPT_TRIPLE },
    { "stats", '\0', OPT_ARG_NONE, OPT_STATS },
    { "link_arch", '\0', OPT_ARG_REQUIRED, OPT_LINK_ARCH },
    { "linker", '\0', OPT_ARG_REQUIRED, OPT_LINKER },

    { "verbose", 'V', OPT_ARG_REQUIRED, OPT_VERBOSE },
    { "flares", '\0', OPT_ARG_OPTIONAL, OPT_FLARES },
    { "opt_level", '\0', OPT_ARG_REQUIRED, OPT_OPT_LEVEL },
    { "fast_crash", '\0', OPT_ARG_OPTIONAL, OPT_FAST_CRASH },
    { "gen_heap", '\0', OPT_ARG_OPTIONAL, OPT_GEN_HEAP },
    { "elide_checks_for_known_live", '\0', OPT_ARG_OPTIONAL, OPT_ELIDE_CHECKS_FOR_KNOWN_LIVE },
    { "gen_size", '\0', OPT_ARG_REQUIRED, OPT_GENERATION_SIZE },
    { "elide_checks_for_regions", '\0', OPT_ARG_OPTIONAL, OPT_ELIDE_CHECKS_FOR_REGIONS },
    { "include_bounds_checks", '\0', OPT_ARG_OPTIONAL, OPT_INCLUDE_BOUNDS_CHECKS },
    { "use_atomic_rc", '\0', OPT_ARG_OPTIONAL, OPT_USE_ATOMIC_RC },
    { "force_all_known_live", '\0', OPT_ARG_NONE, OPT_FORCE_ALL_KNOWN_LIVE },
    { "print_mem_overhead", '\0', OPT_ARG_OPTIONAL, OPT_PRINT_MEM_OVERHEAD },
    { "enable_replaying", '\0', OPT_ARG_OPTIONAL, OPT_ENABLE_REPLAYING },
    { "replay_whitelist_extern", '\0', OPT_ARG_REQUIRED, OPT_REPLAY_WHITELIST_EXTERN },
    { "enable_side_calling", '\0', OPT_ARG_OPTIONAL, OPT_ENABLE_SIDE_CALLING },
    { "census", '\0', OPT_ARG_OPTIONAL, OPT_CENSUS },
    { "region_override", '\0', OPT_ARG_REQUIRED, OPT_REGION_OVERRIDE },
    { "ir", '\0', OPT_ARG_NONE, OPT_IR },
    { "asm", '\0', OPT_ARG_NONE, OPT_ASM },
    { "llvm_ir", '\0', OPT_ARG_NONE, OPT_LLVMIR },
    { "trace", 't', OPT_ARG_NONE, OPT_TRACE },
    { "width", 'w', OPT_ARG_REQUIRED, OPT_WIDTH },
    { "immerr", '\0', OPT_ARG_NONE, OPT_IMMERR },
    { "verify", '\0', OPT_ARG_NONE, OPT_VERIFY },
    { "files", '\0', OPT_ARG_NONE, OPT_FILENAMES },
    { "checktree", '\0', OPT_ARG_NONE, OPT_CHECKTREE },
    { "extfun", '\0', OPT_ARG_NONE, OPT_EXTFUN },
    { "simplebuiltin", '\0', OPT_ARG_NONE, OPT_SIMPLEBUILTIN },
    { "lint_llvm", '\0', OPT_ARG_NONE, OPT_LINT_LLVM },

    OPT_ARGS_FINISH
};

//static void usage()
//{
//    printf("%s\n%s\n%s\n%s\n%s\n%s", // for complying with -Woverlength-strings
//        "valec [OPTIONS] <source_file>\n"
//        ,
//        "The source directory defaults to the current directory.\n"
//        ,
//        "Options:\n"
//        "  --version, -v   Print the version of the compiler and exit.\n"
//        "  --help, -h      Print this help text and exit.\n"
//        "  --debug, -d     Don't optimise the output.\n"
//        "  --define, -D    Define the specified build flag.\n"
//        "    =name\n"
//        "  --strip, -s     Strip debug info.\n"
//        "  --path, -p      Add an additional search path.\n"
//        "    =path         Used to find packages and libraries.\n"
//        "  --o             Name the resulting executable.\n"
//        "  --output_dir    Write output to this directory.\n"
//        "    =path         Defaults to the current directory.\n"
//        "  --library, -l   Generate a C-API compatible static library.\n"
//        "  --runtimebc     Compile with the LLVM bitcode file for the runtime.\n"
//        "  --wasm          Compile for WebAssembly target.\n"
//        "  --pic           Compile using position independent code.\n"
//        "  --nopic         Don't compile using position independent code.\n"
//        "  --docs, -g      Generate code documentation.\n"
//        "  --docs_public   Generate code documentation for public types only.\n"
//        ,
//        "Rarely needed options:\n"
//        "  --safe          Allow only the listed packages to use C FFI.\n"
//        "    =package      With no packages listed, only builtin is allowed.\n"
//        "  --cpu           Set the target CPU.\n"
//        "    =name         Default is the host CPU.\n"
//        "  --features      CPU features to enable or disable.\n"
//        "    =+this,-that  Use + to enable, - to disable.\n"
//        "                  Defaults to detecting all CPU features from the host.\n"
//        "  --triple        Set the target triple.\n"
//        "    =name         Defaults to the host triple.\n"
//        "  --stats         Print some compiler stats.\n"
//        "  --link_arch     Set the linking architecture.\n"
//        "    =name         Default is the host architecture.\n"
//        "  --linker        Set the linker command to use.\n"
//        "    =name         Default is the compiler.\n"
//        ,
//        "Debugging options:\n"
//        "  --verbose, -V   Verbosity level.\n"
//        "    =0            Only print errors.\n"
//        "    =1            Print info on compiler stages.\n"
//        "    =2            More detailed compilation information.\n"
//        "    =3            External tool command lines.\n"
//        "    =4            Very low-level detail.\n"
//        "  --ir            Output an IR tree for the whole program.\n"
//        "  --asm           Output an assembly file.\n"
//        "  --llvm_ir       Output an LLVM IR file.\n"
//        "  --trace, -t     Enable parse trace.\n"
//        "  --width, -w     Width to target when printing the IR.\n"
//        "    =columns      Defaults to the terminal width.\n"
//        "  --immerr        Report errors immediately rather than deferring.\n"
//        "  --checktree     Verify IR well-formedness.\n"
//        "  --verify        Verify LLVM IR.\n"
//        "  --extfun        Set function default linkage to external.\n"
//        "  --simplebuiltin Use a minimal builtin package.\n"
//        "  --files         Print source file names as each is processed.\n"
//        "  --lint_llvm     Run the LLVM linting pass on generated IR.\n"
//        ,
//        "" // "Runtime options for Vale programs (not for use with Vale compiler):\n"
//    );
//}

int valeOptSet(ValeOptions *opt, int *argc, char **argv) {
  opt_state_t s;
  int ok = 1;
  int i;

  // options->limit = PASS_ALL;
  // options->check.errors = errors_alloc();

  optInit(args, &s, argc, argv);

  for (int id = 0; (id = optNext(&s)) != -1; ) {
    switch (id) {
      case OPT_OUTPUT_DIR:
        opt->outputDir = s.arg_val;
        break;
      case OPT_LIBRARY:
        opt->library = 1;
        break;
      case OPT_PIC:
        opt->pic = 1;
        break;
      case OPT_NOPIC:
        opt->pic = 0;
        break;
      case OPT_DOCS:
        opt->docs = 1;
        break;
      case OPT_WASM:
        opt->wasm = 1;
        opt->triple = "wasm32-unknown-unknown-wasm";
        break;

      case OPT_CPU:
        opt->cpu = s.arg_val;
        break;
      case OPT_FEATURES:
        opt->features = s.arg_val;
        break;
      case OPT_TRIPLE:
        opt->triple = s.arg_val;
        break;

      case OPT_ASM:
        opt->print_asm = 1;
        break;
      case OPT_LLVMIR:
        opt->print_llvmir = 1;
        break;
      case OPT_VERIFY:
        opt->verify = 1;
        break;

      case OPT_FLARES: {
        if (!s.arg_val) {
          opt->flares = true;
        } else if (s.arg_val == std::string("on")) {
          opt->flares = true;
        } else if (s.arg_val == std::string("off")) {
          opt->flares = false;
        } else
          assert(false);
        break;
      }

      case OPT_FAST_CRASH: {
        if (!s.arg_val) {
          opt->fastCrash = true;
        } else if (s.arg_val == std::string("on")) {
          opt->fastCrash = true;
        } else if (s.arg_val == std::string("off")) {
          opt->fastCrash = false;
        } else
          assert(false);
        break;
      }

      case OPT_ELIDE_CHECKS_FOR_KNOWN_LIVE: {
        if (!s.arg_val) {
          opt->elideChecksForKnownLive = true;
        } else if (s.arg_val == std::string("true")) {
          opt->elideChecksForKnownLive = true;
        } else if (s.arg_val == std::string("false")) {
          opt->elideChecksForKnownLive = false;
        } else
          assert(false);
        break;
      }

      case OPT_PRINT_MEM_OVERHEAD: {
        if (!s.arg_val) {
          opt->printMemOverhead = true;
        } else if (s.arg_val == std::string("true")) {
          opt->printMemOverhead = true;
        } else if (s.arg_val == std::string("false")) {
          opt->printMemOverhead = false;
        } else
          assert(false);
        break;
      }

          case OPT_OPT_LEVEL: {
            if (s.arg_val == std::string("O0")) {
              opt->optLevel = ValeOptimizationLevel::O0;
            } else if (s.arg_val == std::string("O1")) {
              opt->optLevel = ValeOptimizationLevel::O1;
            } else if (s.arg_val == std::string("O2")) {
              opt->optLevel = ValeOptimizationLevel::O2;
            } else if (s.arg_val == std::string("O2i")) {
              opt->optLevel = ValeOptimizationLevel::O2i;
            } else if (s.arg_val == std::string("O3")) {
              opt->optLevel = ValeOptimizationLevel::O3;
            } else assert(false);
            break;
          }

          case OPT_GENERATION_SIZE: {
            assert(s.arg_val);
            if (s.arg_val == std::string("32")) {
              opt->generationSize = 32;
            } else if (s.arg_val == std::string("64")) {
              opt->generationSize = 64;
            } else assert(false);
            break;
          }

          case OPT_ELIDE_CHECKS_FOR_REGIONS: {
            if (!s.arg_val) {
              opt->elideChecksForRegions = true;
            } else if (s.arg_val == std::string("true")) {
              opt->elideChecksForRegions = true;
            } else if (s.arg_val == std::string("false")) {
              opt->elideChecksForRegions = false;
            } else assert(false);
            break;
          }

          case OPT_INCLUDE_BOUNDS_CHECKS: {
            if (!s.arg_val) {
              opt->includeBoundsChecks = true;
            } else if (s.arg_val == std::string("on") || s.arg_val == std::string("yes") || s.arg_val == std::string("true")) {
              opt->includeBoundsChecks = true;
            } else if (s.arg_val == std::string("off") || s.arg_val == std::string("no") || s.arg_val == std::string("false")) {
              opt->includeBoundsChecks = false;
            } else assert(false);
            break;
          }

          case OPT_USE_ATOMIC_RC: {
            if (!s.arg_val) {
              opt->useAtomicRc = true;
            } else if (s.arg_val == std::string("on") || s.arg_val == std::string("yes") || s.arg_val == std::string("true")) {
              opt->useAtomicRc = true;
            } else if (s.arg_val == std::string("off") || s.arg_val == std::string("no") || s.arg_val == std::string("false")) {
              opt->useAtomicRc = false;
            } else assert(false);
            break;
          }

          case OPT_FORCE_ALL_KNOWN_LIVE: {
            if (!s.arg_val) {
              opt->forceAllKnownLive = true;
            } else if (s.arg_val == std::string("true")) {
              opt->forceAllKnownLive = true;
            } else if (s.arg_val == std::string("false")) {
              opt->forceAllKnownLive = false;
            } else assert(false);
            break;
          }

          case OPT_ENABLE_REPLAYING: {
            if (!s.arg_val) {
              opt->enableReplaying = true;
            } else if (s.arg_val == std::string("true")) {
              opt->enableReplaying = true;
            } else if (s.arg_val == std::string("false")) {
              opt->enableReplaying = false;
            } else assert(false);
            break;
          }

          case OPT_ENABLE_SIDE_CALLING: {
            if (!s.arg_val) {
              opt->enableSideCalling = true;
            } else if (s.arg_val == std::string("true")) {
              opt->enableSideCalling = true;
            } else if (s.arg_val == std::string("false")) {
              opt->enableSideCalling = false;
            } else assert(false);
            break;
          }

        case OPT_CENSUS: {
          if (!s.arg_val) {
            opt->census = true;
          } else if (s.arg_val == std::string("on")) {
            opt->census = true;
          } else if (s.arg_val == std::string("off")) {
            opt->census = false;
          } else assert(false);
          break;
        }

        case OPT_REGION_OVERRIDE: {
          if (s.arg_val == std::string("unsafe-fast")) {
            opt->regionOverride = RegionOverride::FAST;
//          } else if (s.arg_val == std::string("assist")) {
//            opt->regionOverride = RegionOverride::ASSIST;
          } else if (s.arg_val == std::string("naive-rc")) {
            opt->regionOverride = RegionOverride::NAIVE_RC;
//          } else if (s.arg_val == std::string("resilient-v0")) {
//            opt->regionOverride = RegionOverride::RESILIENT_V0;
//          } else if (s.arg_val == std::string("resilient-v1")) {
//            opt->regionOverride = RegionOverride::RESILIENT_V1;
//          } else if (s.arg_val == std::string("resilient-v2")) {
//            opt->regionOverride = RegionOverride::RESILIENT_V2;
          } else if (s.arg_val == std::string("resilient-v3")) {
            opt->regionOverride = RegionOverride::RESILIENT_V3;
          } else if (s.arg_val == std::string("safe-fastest")) {
            opt->regionOverride = RegionOverride::SAFE_FASTEST;
          } else if (s.arg_val == std::string("safe")) {
            opt->regionOverride = RegionOverride::SAFE;
//          } else if (s.arg_val == std::string("resilient-limit")) {
//            opt->regionOverride = RegionOverride::RESILIENT_LIMIT;
          } else {
            std::cerr << "Unknown region: " << s.arg_val << std::endl;
            exit(1);
            assert(false);
          }
          break;
        }

      case OPT_REPLAY_WHITELIST_EXTERN: {
        assert(s.arg_val);
        std::string argStr = s.arg_val;
        auto dotPos = argStr.find('.');
        if (dotPos == std::string::npos) {
          std::cerr
              << "Error: Invalid --replay_whitelist_extern argument. Must be in the form [module_name].[extern_name], for example --replay_whitelist_extern=mylibrary.paintRectangle"
              << std::endl;
          exit(1);
        }
        auto moduleName = argStr.substr(0, dotPos);
        if (moduleName.empty()) {
          std::cerr
              << "Error: Invalid --replay_whitelist_extern argument. Must be in the form [module_name].[extern_name], for example --replay_whitelist_extern=mylibrary.paintRectangle"
              << std::endl;
          exit(1);
        }
        auto functionName = argStr.substr(dotPos + 1);
        if (functionName.empty()) {
          std::cerr
              << "Error: Invalid --replay_whitelist_extern argument. Must be in the form [module_name].[extern_name], for example --replay_whitelist_extern=mylibrary.paintRectangle"
              << std::endl;
          exit(1);
        }
        std::cerr << "Adding whitelist: " << moduleName << "." << functionName << std::endl;
        opt->projectNameToReplayWhitelistedExterns[moduleName].insert(functionName);
        break;
      }

      default:
        std::cerr << "Unrecognized option!" << std::endl;
        return -1;
    }
  }


  for (i = 1; i < *argc; i++) {
    if (argv[i][0] == '-') {
      printf("Unrecognised option: %s\n", argv[i]);
      return -1;
    }
  }

  return 1;
}


