/** Code generation via LLVM
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "coneopts.h"
#include "utils/fileio.h"
#include "genllvm.h"
#include "vale.h"
#include "error.h"

#include "llvm-c/ExecutionEngine.h"
#include <llvm-c/Target.h>
#include <llvm-c/Analysis.h>
#include <llvm-c/BitWriter.h>
#include <llvm-c/Transforms/Scalar.h>
#include <llvm-c/Transforms/IPO.h>
#if LLVM_VERSION_MAJOR >= 7
#include "llvm-c/Transforms/Utils.h"
#endif

#include <cassert>
#include <string>
#include <cstring>
#include <iostream>

#ifdef _WIN32
#define asmext "asm"
#define objext "obj"
#else
#define asmext "s"
#define objext "o"
#endif

void genlPackage(GenState *gen) {
    gen->module = LLVMModuleCreateWithNameInContext(gen->opt->srcDirAndNameNoExt.c_str(), gen->context);
    if (!gen->opt->release) {
        gen->dibuilder = LLVMCreateDIBuilder(gen->module);
        gen->difile = LLVMDIBuilderCreateFile(gen->dibuilder, "main.cone", 9, ".", 1);
        // If theres a compile error on this line, its some sort of LLVM version issue, try commenting or uncommenting the last four args.
        gen->compileUnit = LLVMDIBuilderCreateCompileUnit(gen->dibuilder, LLVMDWARFSourceLanguageC,
            gen->difile, "Cone compiler", 13, 0, "", 0, 0, "", 0, LLVMDWARFEmissionFull, 0, 0, 0/*, "isysroothere", strlen("isysroothere"), "sdkhere", strlen("sdkhere")*/);
    }
  //    genlModule(gen, mod);
  compileValeCode(gen->module, gen->opt->srcpath);
    if (!gen->opt->release)
        LLVMDIBuilderFinalize(gen->dibuilder);
}

// Use provided options (triple, etc.) to creation a machine
LLVMTargetMachineRef genlCreateMachine(ConeOptions *opt) {
    char *err;
    LLVMTargetRef target;
    LLVMCodeGenOptLevel opt_level;
    LLVMRelocMode reloc;
    LLVMTargetMachineRef machine;

//    LLVMInitializeAllTargetInfos();
//    LLVMInitializeAllTargetMCs();
//    LLVMInitializeAllTargets();
//    LLVMInitializeAllAsmPrinters();
//    LLVMInitializeAllAsmParsers();

    LLVMInitializeX86TargetInfo();
    LLVMInitializeX86TargetMC();
    LLVMInitializeX86Target();
    LLVMInitializeX86AsmPrinter();
    LLVMInitializeX86AsmParser();

    // Find target for the specified triple
    if (!opt->triple)
        opt->triple = LLVMGetDefaultTargetTriple();
    if (LLVMGetTargetFromTriple(opt->triple, &target, &err) != 0) {
        errorExit(ExitCode::LlvmSetupFailed, "Could not create target: ", err);
        LLVMDisposeMessage(err);
        return NULL;
    }

    // Create a specific target machine
    opt_level = opt->release? LLVMCodeGenLevelAggressive : LLVMCodeGenLevelNone;
    reloc = (opt->pic || opt->library)? LLVMRelocPIC : LLVMRelocDefault;
    if (!opt->cpu)
        opt->cpu = "generic";
    if (!opt->features)
        opt->features = "";
    if (!(machine = LLVMCreateTargetMachine(target, opt->triple, opt->cpu, opt->features, opt_level, reloc, LLVMCodeModelDefault))) {
      errorExit(ExitCode::LlvmSetupFailed, "Could not create target machine");
        return NULL;
    }

    return machine;
}

// Generate requested object file
void genlOut(const char *objpath, const char *asmpath, LLVMModuleRef mod, const char *triple, LLVMTargetMachineRef machine) {
    char *err;
    LLVMTargetDataRef dataref;
    char *layout;

    LLVMSetTarget(mod, triple);
    dataref = LLVMCreateTargetDataLayout(machine);
    layout = LLVMCopyStringRepOfTargetData(dataref);
    LLVMSetDataLayout(mod, layout);
    LLVMDisposeMessage(layout);

    if (asmpath) {
      char asmpathCStr[1024] = {0};
      strncpy(asmpathCStr, asmpath, 1024);

      // Generate assembly file if requested
      if (LLVMTargetMachineEmitToFile(machine, mod, asmpathCStr,
          LLVMAssemblyFile, &err) != 0) {
        std::cerr << "Could not emit asm file: " << asmpathCStr << std::endl;
        LLVMDisposeMessage(err);
      }
    }

    char objpathCStr[1024] = { 0 };
    strncpy(objpathCStr, objpath, 1024);

    // Generate .o or .obj file
    if (LLVMTargetMachineEmitToFile(machine, mod, objpathCStr, LLVMObjectFile, &err) != 0) {
      std::cerr << "Could not emit obj file to path " << objpathCStr << " " << err << std::endl;
        LLVMDisposeMessage(err);
    }
}

// Generate IR nodes into LLVM IR using LLVM
void genMod(GenState *gen) {
    char *err;

    // Generate IR to LLVM IR
    genlPackage(gen);

    // Serialize the LLVM IR, if requested
    if (gen->opt->print_llvmir && LLVMPrintModuleToFile(gen->module, fileMakePath(gen->opt->output, gen->opt->srcNameNoExt.c_str(), "pre.ll").c_str(), &err) != 0) {
      std::cerr << "Could not emit pre-ir file: " << err << std::endl;
      LLVMDisposeMessage(err);
    }

    // Verify generated IR
    if (gen->opt->verify) {
        char *error = NULL;
        LLVMVerifyModule(gen->module, LLVMAbortProcessAction, &error);
        if (error) {
            if (*error)
                errorExit(ExitCode::VerifyFailed, "Module verification failed:\n%s", error);
            LLVMDisposeMessage(error);
        }
    }

    // Optimize the generated LLVM IR
    LLVMPassManagerRef passmgr = LLVMCreatePassManager();
    LLVMAddPromoteMemoryToRegisterPass(passmgr);     // Demote allocas to registers.
    LLVMAddInstructionCombiningPass(passmgr);        // Do simple "peephole" and bit-twiddling optimizations
    LLVMAddReassociatePass(passmgr);                 // Reassociate expressions.
    LLVMAddGVNPass(passmgr);                         // Eliminate common subexpressions.
    LLVMAddCFGSimplificationPass(passmgr);           // Simplify the control flow graph
    if (gen->opt->release)
        LLVMAddFunctionInliningPass(passmgr);        // Function inlining
    LLVMRunPassManager(passmgr, gen->module);
    LLVMDisposePassManager(passmgr);

  // Serialize the LLVM IR, if requested
  auto outputFilePath = fileMakePath(gen->opt->output, gen->opt->srcNameNoExt.c_str(), "ll");
  std::cout << "Printing file " << outputFilePath << std::endl;
  if (gen->opt->print_llvmir && LLVMPrintModuleToFile(gen->module, outputFilePath.c_str(), &err) != 0) {
    std::cerr << "Could not emit ir file: " << err << std::endl;
    LLVMDisposeMessage(err);
  }

    // Transform IR to target's ASM and OBJ
    if (gen->machine) {
      auto objpath =
          fileMakePath(gen->opt->output, gen->opt->srcNameNoExt.c_str(),
          gen->opt->wasm ? "wasm" : objext);
      auto asmpath =
          fileMakePath(gen->opt->output,
              gen->opt->srcNameNoExt.c_str(),
              gen->opt->wasm ? "wat" : asmext);
      genlOut(
          objpath.c_str(),gen->opt->print_asm ? asmpath.c_str() : NULL,
          gen->module, gen->opt->triple, gen->machine);
    }


  LLVMDisposeModule(gen->module);
    // LLVMContextDispose(gen.context);  // Only need if we created a new context
}

// Setup LLVM generation, ensuring we know intended target
void genSetup(GenState *gen, ConeOptions *opt) {
    gen->opt = opt;

    LLVMTargetMachineRef machine = genlCreateMachine(opt);
    if (!machine)
        exit((int)(ExitCode::BadOpts));

    // Obtain data layout info, particularly pointer sizes
    gen->machine = machine;
    gen->datalayout = LLVMCreateTargetDataLayout(machine);
    opt->ptrsize = LLVMPointerSize(gen->datalayout) << 3u;

    // LLVM inlining bugs prevent use of LLVMContextCreate();
    gen->context = LLVMGetGlobalContext();
}

void genClose(GenState *gen) {
    LLVMDisposeTargetMachine(gen->machine);
}
