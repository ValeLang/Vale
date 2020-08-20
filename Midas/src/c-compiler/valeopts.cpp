
#include "valeopts.h"
#include "utils/options.h"

#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>

// List of option ids
enum
{
    OPT_VERSION,
    OPT_HELP,
    OPT_DEBUG,
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
    { "version", 'v', OPT_ARG_NONE, OPT_VERSION },
    { "help", 'h', OPT_ARG_NONE, OPT_HELP },
    { "debug", 'd', OPT_ARG_NONE, OPT_DEBUG },
    { "define", 'D', OPT_ARG_REQUIRED, OPT_BUILDFLAG },
    { "strip", 's', OPT_ARG_NONE, OPT_STRIP },
    { "path", 'p', OPT_ARG_REQUIRED, OPT_PATHS },
    { "output-dir", '\0', OPT_ARG_REQUIRED, OPT_OUTPUT_DIR },
    { "library", 'l', OPT_ARG_NONE, OPT_LIBRARY },
    { "runtimebc", '\0', OPT_ARG_NONE, OPT_RUNTIMEBC },
    { "pic", '\0', OPT_ARG_NONE, OPT_PIC },
    { "nopic", '\0', OPT_ARG_NONE, OPT_NOPIC },
    { "docs", 'g', OPT_ARG_NONE, OPT_DOCS },
    { "docs-public", '\0', OPT_ARG_NONE, OPT_DOCS_PUBLIC },

    { "safe", '\0', OPT_ARG_OPTIONAL, OPT_SAFE },
    { "cpu", '\0', OPT_ARG_REQUIRED, OPT_CPU },
    { "features", '\0', OPT_ARG_REQUIRED, OPT_FEATURES },
    { "wasm", '\0', OPT_ARG_NONE, OPT_WASM },
    { "triple", '\0', OPT_ARG_REQUIRED, OPT_TRIPLE },
    { "stats", '\0', OPT_ARG_NONE, OPT_STATS },
    { "link-arch", '\0', OPT_ARG_REQUIRED, OPT_LINK_ARCH },
    { "linker", '\0', OPT_ARG_REQUIRED, OPT_LINKER },

    { "verbose", 'V', OPT_ARG_REQUIRED, OPT_VERBOSE },
    { "flares", '\0', OPT_ARG_OPTIONAL, OPT_FLARES },
    { "census", '\0', OPT_ARG_OPTIONAL, OPT_CENSUS },
    { "region-override", '\0', OPT_ARG_REQUIRED, OPT_REGION_OVERRIDE },
    { "ir", '\0', OPT_ARG_NONE, OPT_IR },
    { "asm", '\0', OPT_ARG_NONE, OPT_ASM },
    { "llvmir", '\0', OPT_ARG_NONE, OPT_LLVMIR },
    { "trace", 't', OPT_ARG_NONE, OPT_TRACE },
    { "width", 'w', OPT_ARG_REQUIRED, OPT_WIDTH },
    { "immerr", '\0', OPT_ARG_NONE, OPT_IMMERR },
    { "verify", '\0', OPT_ARG_NONE, OPT_VERIFY },
    { "files", '\0', OPT_ARG_NONE, OPT_FILENAMES },
    { "checktree", '\0', OPT_ARG_NONE, OPT_CHECKTREE },
    { "extfun", '\0', OPT_ARG_NONE, OPT_EXTFUN },
    { "simplebuiltin", '\0', OPT_ARG_NONE, OPT_SIMPLEBUILTIN },
    { "lint-llvm", '\0', OPT_ARG_NONE, OPT_LINT_LLVM },

    OPT_ARGS_FINISH
};

static void usage()
{
    printf("%s\n%s\n%s\n%s\n%s\n%s", // for complying with -Woverlength-strings
        "valec [OPTIONS] <source_file>\n"
        ,
        "The source directory defaults to the current directory.\n"
        ,
        "Options:\n"
        "  --version, -v   Print the version of the compiler and exit.\n"
        "  --help, -h      Print this help text and exit.\n"
        "  --debug, -d     Don't optimise the output.\n"
        "  --define, -D    Define the specified build flag.\n"
        "    =name\n"
        "  --strip, -s     Strip debug info.\n"
        "  --path, -p      Add an additional search path.\n"
        "    =path         Used to find packages and libraries.\n"
        "  --output, -o    Write output to this directory.\n"
        "    =path         Defaults to the current directory.\n"
        "  --library, -l   Generate a C-API compatible static library.\n"
        "  --runtimebc     Compile with the LLVM bitcode file for the runtime.\n"
        "  --wasm          Compile for WebAssembly target.\n"
        "  --pic           Compile using position independent code.\n"
        "  --nopic         Don't compile using position independent code.\n"
        "  --docs, -g      Generate code documentation.\n"
        "  --docs-public   Generate code documentation for public types only.\n"
        ,
        "Rarely needed options:\n"
        "  --safe          Allow only the listed packages to use C FFI.\n"
        "    =package      With no packages listed, only builtin is allowed.\n"
        "  --cpu           Set the target CPU.\n"
        "    =name         Default is the host CPU.\n"
        "  --features      CPU features to enable or disable.\n"
        "    =+this,-that  Use + to enable, - to disable.\n"
        "                  Defaults to detecting all CPU features from the host.\n"
        "  --triple        Set the target triple.\n"
        "    =name         Defaults to the host triple.\n"
        "  --stats         Print some compiler stats.\n"
        "  --link-arch     Set the linking architecture.\n"
        "    =name         Default is the host architecture.\n"
        "  --linker        Set the linker command to use.\n"
        "    =name         Default is the compiler.\n"
        ,
        "Debugging options:\n"
        "  --verbose, -V   Verbosity level.\n"
        "    =0            Only print errors.\n"
        "    =1            Print info on compiler stages.\n"
        "    =2            More detailed compilation information.\n"
        "    =3            External tool command lines.\n"
        "    =4            Very low-level detail.\n"
        "  --ir            Output an IR tree for the whole program.\n"
        "  --asm           Output an assembly file.\n"
        "  --llvmir        Output an LLVM IR file.\n"
        "  --trace, -t     Enable parse trace.\n"
        "  --width, -w     Width to target when printing the IR.\n"
        "    =columns      Defaults to the terminal width.\n"
        "  --immerr        Report errors immediately rather than deferring.\n"
        "  --checktree     Verify IR well-formedness.\n"
        "  --verify        Verify LLVM IR.\n"
        "  --extfun        Set function default linkage to external.\n"
        "  --simplebuiltin Use a minimal builtin package.\n"
        "  --files         Print source file names as each is processed.\n"
        "  --lint-llvm     Run the LLVM linting pass on generated IR.\n"
        ,
        "" // "Runtime options for Vale programs (not for use with Vale compiler):\n"
    );
}

int valeOptSet(ValeOptions *opt, int *argc, char **argv) {
    opt_state_t s;
    int id;
    int ok = 1;
    int print_usage = 0;
    int i;

    // options->limit = PASS_ALL;
    // options->check.errors = errors_alloc();

    optInit(args, &s, argc, argv);
    opt->release = 1;
    opt->flares = false;
    opt->census = false;

    while ((id = optNext(&s)) != -1) {
        switch (id) {
        case OPT_VERSION:
            printf("%s\n", "0.1");
            return 0;

        case OPT_HELP:
            usage();
            return 0;

        case OPT_DEBUG: opt->release = 0; break;
        case OPT_OUTPUT_DIR: opt->output = s.arg_val; break;
        case OPT_LIBRARY: opt->library = 1; break;
        case OPT_PIC: opt->pic = 1; break;
        case OPT_NOPIC: opt->pic = 0; break;
        case OPT_DOCS: opt->docs = 1; break;
        case OPT_WASM: opt->wasm = 1; opt->triple = "wasm32-unknown-unknown-wasm"; break;

        case OPT_CPU: opt->cpu = s.arg_val; break;
        case OPT_FEATURES: opt->features = s.arg_val; break;
        case OPT_TRIPLE: opt->triple = s.arg_val; break;

        case OPT_ASM: opt->print_asm = 1; break;
        case OPT_LLVMIR: opt->print_llvmir = 1; break;
        case OPT_VERIFY: opt->verify = 1; break;

        case OPT_FLARES: {
          if (!s.arg_val) {
            opt->flares = true;
          } else if (s.arg_val == std::string("on")) {
            opt->flares = true;
          } else if (s.arg_val == std::string("off")) {
            opt->flares = false;
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
          } else if (s.arg_val == std::string("assist")) {
            opt->regionOverride = RegionOverride::ASSIST;
          } else if (s.arg_val == std::string("resilient")) {
            opt->regionOverride = RegionOverride::RESILIENT;
          } else assert(false);
          break;
        }

        default: usage(); return -1;
        }
    }

    for (i = 1; i < *argc; i++) {
        if (argv[i][0] == '-') {
            printf("Unrecognised option: %s\n", argv[i]);
            ok = 0;
            print_usage = 1;
        }
    }

    if (!ok) {
        // errors_print(opt.check.errors);
        if (print_usage)
            usage();
        return -1;
    }
    return 1;
}


