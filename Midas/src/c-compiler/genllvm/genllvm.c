/** Code generation via LLVM
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#include "../ir/ir.h"
#include "../parser/lexer.h"
#include "../shared/error.h"
#include "../shared/timer.h"
#include "../coneopts.h"
#include "../ir/nametbl.h"
#include "../shared/fileio.h"
#include "genllvm.h"
#include "../vale.h"

#include "llvm-c/ExecutionEngine.h"
#include <llvm-c/Target.h>
#include <llvm-c/Analysis.h>
#include <llvm-c/BitWriter.h>
#include <llvm-c/Transforms/Scalar.h>
#include <llvm-c/Transforms/IPO.h>
#if LLVM_VERSION_MAJOR >= 7
#include "llvm-c/Transforms/Utils.h"
#endif

#include <stdio.h>
#include <assert.h>
#include <string.h>

#ifdef _WIN32
#define asmext "asm"
#define objext "obj"
#else
#define asmext "s"
#define objext "o"
#endif

// Insert every alloca before the allocaPoint in the function's entry block.
// Why? To improve LLVM optimization of SRoA and mem2reg, all allocas
// should be located in the function's entry block before the first call.
LLVMValueRef genlAlloca(GenState *gen, LLVMTypeRef type, const char *name) {
    LLVMBasicBlockRef current_block = LLVMGetInsertBlock(gen->builder);
    LLVMPositionBuilderBefore(gen->builder, gen->allocaPoint);
    LLVMValueRef alloca = LLVMBuildAlloca(gen->builder, type, name);
    LLVMPositionBuilderAtEnd(gen->builder, current_block);
    return alloca;
}

void genlPackage(GenState *gen, ModuleNode *mod) {

    if (mod) {
      assert(mod->tag == ModuleTag);
    }
    gen->module = LLVMModuleCreateWithNameInContext(gen->opt->srcname, gen->context);
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
        errorMsg(ErrorGenErr, "Could not create target: %s", err);
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
        errorMsg(ErrorGenErr, "Could not create target machine");
        return NULL;
    }

    return machine;
}

// Generate requested object file
void genlOut(char *objpath, char *asmpath, LLVMModuleRef mod, char *triple, LLVMTargetMachineRef machine) {
    char *err;
    LLVMTargetDataRef dataref;
    char *layout;

    LLVMSetTarget(mod, triple);
    dataref = LLVMCreateTargetDataLayout(machine);
    layout = LLVMCopyStringRepOfTargetData(dataref);
    LLVMSetDataLayout(mod, layout);
    LLVMDisposeMessage(layout);

    // Generate assembly file if requested
    if (asmpath && LLVMTargetMachineEmitToFile(machine, mod, asmpath, LLVMAssemblyFile, &err) != 0) {
        errorMsg(ErrorGenErr, "Could not emit asm file: %s", err);
        LLVMDisposeMessage(err);
    }

    // Generate .o or .obj file
    if (LLVMTargetMachineEmitToFile(machine, mod, objpath, LLVMObjectFile, &err) != 0) {
        errorMsg(ErrorGenErr, "Could not emit obj file to path %s: %s", objpath, err);
        LLVMDisposeMessage(err);
    }
}

// Generate IR nodes into LLVM IR using LLVM
void genMod(GenState *gen, ModuleNode *mod) {
    char *err;

    // Generate IR to LLVM IR
    genlPackage(gen, mod);

    // Serialize the LLVM IR, if requested
    if (gen->opt->print_llvmir && LLVMPrintModuleToFile(gen->module, fileMakePath(gen->opt->output, gen->opt->srcname, "pre.ll"), &err) != 0) {
      errorMsg(ErrorGenErr, "Could not emit pre-ir file: %s", err);
      LLVMDisposeMessage(err);
    }

    // Verify generated IR
    if (gen->opt->verify) {
        timerBegin(VerifyTimer);
        char *error = NULL;
        LLVMVerifyModule(gen->module, LLVMAbortProcessAction, &error);
        if (error) {
            if (*error)
                errorMsg(ErrorGenErr, "Module verification failed:\n%s", error);
            LLVMDisposeMessage(error);
        }
    }

    // Optimize the generated LLVM IR
    timerBegin(OptTimer);
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
  if (gen->opt->print_llvmir && LLVMPrintModuleToFile(gen->module, fileMakePath(gen->opt->output, gen->opt->srcname, "ll"), &err) != 0) {
    errorMsg(ErrorGenErr, "Could not emit ir file: %s", err);
    LLVMDisposeMessage(err);
  }

    // Transform IR to target's ASM and OBJ
    timerBegin(CodeGenTimer);
    if (gen->machine)
        genlOut(fileMakePath(gen->opt->output, gen->opt->srcname, gen->opt->wasm? "wasm" : objext),
            gen->opt->print_asm? fileMakePath(gen->opt->output, gen->opt->srcname, gen->opt->wasm? "wat" : asmext) : NULL,
            gen->module, gen->opt->triple, gen->machine);


  LLVMDisposeModule(gen->module);
    // LLVMContextDispose(gen.context);  // Only need if we created a new context
}

// Setup LLVM generation, ensuring we know intended target
void genSetup(GenState *gen, ConeOptions *opt) {
    gen->opt = opt;

    LLVMTargetMachineRef machine = genlCreateMachine(opt);
    if (!machine)
        exit(ExitOpts);

    // Obtain data layout info, particularly pointer sizes
    gen->machine = machine;
    gen->datalayout = LLVMCreateTargetDataLayout(machine);
    opt->ptrsize = LLVMPointerSize(gen->datalayout) << 3u;

    gen->context = LLVMGetGlobalContext(); // LLVM inlining bugs prevent use of LLVMContextCreate();
    gen->fn = NULL;
    gen->allocaPoint = NULL;
    gen->block = NULL;
    gen->loopstack = memAllocBlk(sizeof(GenLoopState)*GenLoopMax);
    gen->loopstackcnt = 0;
}

void genClose(GenState *gen) {
    LLVMDisposeTargetMachine(gen->machine);
}
