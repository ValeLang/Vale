#include <llvm-c/Core.h>
#include <llvm-c/DebugInfo.h>
#include <llvm-c/ExecutionEngine.h>
#include <llvm-c/Analysis.h>

#include <assert.h>
#include <string>
#include <vector>
#include <iostream>
#include <fstream>

#include <nlohmann/json.hpp>
#include "function/expressions/shared/shared.h"
#include "struct/interface.h"

#include "metal/types.h"
#include "metal/ast.h"
#include "metal/instructions.h"

#include "function/function.h"
#include "struct/struct.h"
#include "metal/readjson.h"
#include "error.h"

#include <cstring>
#include <llvm-c/Transforms/Scalar.h>
#include <llvm-c/Transforms/Utils.h>
#include <llvm-c/Transforms/IPO.h>

#ifdef _WIN32
#define asmext "asm"
#define objext "obj"
#else
#define asmext "s"
#define objext "o"
#endif

// for convenience
using json = nlohmann::json;


void initInternalExterns(GlobalState* globalState) {
  {
    LLVMTypeRef retType = LLVMPointerType(LLVMInt8Type(), 0);
    LLVMTypeRef paramType = LLVMInt64Type();
    LLVMTypeRef funcType = LLVMFunctionType(retType, &paramType, 1, 0);
    globalState->malloc = LLVMAddFunction(globalState->mod, "malloc", funcType);
  }

  {
    LLVMTypeRef retType = LLVMVoidType();
    LLVMTypeRef paramType = LLVMPointerType(LLVMInt8Type(), 0);
    LLVMTypeRef funcType = LLVMFunctionType(retType, &paramType, 1, 0);
    globalState->free = LLVMAddFunction(globalState->mod, "free", funcType);
  }

  {
    LLVMTypeRef retType = LLVMVoidType();
    LLVMTypeRef paramType = LLVMInt8Type();
    LLVMTypeRef funcType = LLVMFunctionType(retType, &paramType, 1, 0);
    globalState->exit = LLVMAddFunction(globalState->mod, "exit", funcType);
  }

  {
    LLVMTypeRef retType = LLVMVoidType();
    LLVMTypeRef paramType = LLVMInt1Type();
    LLVMTypeRef funcType = LLVMFunctionType(retType, &paramType, 1, 0);
    globalState->assert = LLVMAddFunction(globalState->mod, "__vassert", funcType);
  }

  {
    LLVMTypeRef retType = LLVMVoidType();
    LLVMTypeRef paramTypes[2] = { LLVMInt64Type(), LLVMInt64Type() };
    LLVMTypeRef funcType = LLVMFunctionType(retType, paramTypes, 2, 0);
    globalState->assertI64Eq = LLVMAddFunction(globalState->mod, "__vassertI64Eq", funcType);
  }

  {
    LLVMTypeRef retType = LLVMVoidType();
    LLVMTypeRef paramTypes[] = { LLVMInt64Type(), LLVMInt64Type() };
    LLVMTypeRef funcType = LLVMFunctionType(retType, paramTypes, 2, 0);
    globalState->flareI64 = LLVMAddFunction(globalState->mod, "__vflare_i64", funcType);
  }

  {
    LLVMTypeRef retType = LLVMVoidType();
    std::vector<LLVMTypeRef> paramTypes = {
        LLVMPointerType(LLVMInt8Type(), 0)
    };
    LLVMTypeRef funcType = LLVMFunctionType(retType, paramTypes.data(), paramTypes.size(), 0);
    globalState->printCStr = LLVMAddFunction(globalState->mod, "__vprintCStr", funcType);
  }

  {
    LLVMTypeRef retType = LLVMInt64Type();
    LLVMTypeRef funcType = LLVMFunctionType(retType, nullptr, 0, 0);
    globalState->getch = LLVMAddFunction(globalState->mod, "getchar", funcType);
  }

  {
    LLVMTypeRef retType = LLVMVoidType();
    std::vector<LLVMTypeRef> paramTypes = {
        LLVMInt64Type()
    };
    LLVMTypeRef funcType = LLVMFunctionType(retType, paramTypes.data(), paramTypes.size(), 0);
    globalState->printInt = LLVMAddFunction(globalState->mod, "__vprintI64", funcType);
  }

  {
    LLVMTypeRef retType = LLVMVoidType();
    std::vector<LLVMTypeRef> paramTypes = {
        LLVMInt1Type()
    };
    LLVMTypeRef funcType = LLVMFunctionType(retType, paramTypes.data(), paramTypes.size(), 0);
    globalState->printBool = LLVMAddFunction(globalState->mod, "__vprintBool", funcType);
  }

  {
    LLVMTypeRef retType = LLVMVoidType();
    std::vector<LLVMTypeRef> paramTypes = {
        LLVMPointerType(globalState->stringInnerStructL, 0),
        LLVMPointerType(LLVMInt8Type(), 0),
    };
    LLVMTypeRef funcType = LLVMFunctionType(retType, paramTypes.data(), paramTypes.size(), 0);
    globalState->initStr = LLVMAddFunction(globalState->mod, "__vinitStr", funcType);
  }

  {
    LLVMTypeRef retType = LLVMVoidType();
    std::vector<LLVMTypeRef> paramTypes = {
        LLVMPointerType(globalState->stringInnerStructL, 0),
        LLVMPointerType(globalState->stringInnerStructL, 0),
        LLVMPointerType(globalState->stringInnerStructL, 0)
    };
    LLVMTypeRef funcType = LLVMFunctionType(retType, paramTypes.data(), paramTypes.size(), 0);
    globalState->addStr = LLVMAddFunction(globalState->mod, "__vaddStr", funcType);
  }

  {
    LLVMTypeRef retType = LLVMInt8Type();
    std::vector<LLVMTypeRef> paramTypes = {
        LLVMPointerType(globalState->stringInnerStructL, 0),
        LLVMPointerType(globalState->stringInnerStructL, 0)
    };
    LLVMTypeRef funcType = LLVMFunctionType(retType, paramTypes.data(), paramTypes.size(), 0);
    globalState->eqStr = LLVMAddFunction(globalState->mod, "__veqStr", funcType);
  }

  {
    LLVMTypeRef retType = LLVMVoidType();
    std::vector<LLVMTypeRef> paramTypes = {
        LLVMPointerType(globalState->stringInnerStructL, 0)
    };
    LLVMTypeRef funcType = LLVMFunctionType(retType, paramTypes.data(), paramTypes.size(), 0);
    globalState->printVStr = LLVMAddFunction(globalState->mod, "__vprintStr", funcType);
  }

  {
    LLVMTypeRef retType = LLVMVoidType();
    std::vector<LLVMTypeRef> paramTypes = {
        LLVMInt64Type(),
        LLVMPointerType(LLVMInt8Type(), 0),
        LLVMInt64Type(),
    };
    LLVMTypeRef funcType = LLVMFunctionType(retType, paramTypes.data(), paramTypes.size(), 0);
    globalState->intToCStr = LLVMAddFunction(globalState->mod, "__vintToCStr", funcType);
  }

  {
    LLVMTypeRef retType = LLVMInt64Type();
    std::vector<LLVMTypeRef> paramTypes = {
        LLVMPointerType(LLVMInt8Type(), 0),
    };
    LLVMTypeRef funcType = LLVMFunctionType(retType, paramTypes.data(), paramTypes.size(), 0);
    globalState->strlen = LLVMAddFunction(globalState->mod, "strlen", funcType);
  }

  {
    LLVMTypeRef retType = LLVMInt64Type();
    std::vector<LLVMTypeRef> paramTypes = {
        LLVMPointerType(LLVMVoidType(), 0),
    };
    LLVMTypeRef funcType = LLVMFunctionType(retType, paramTypes.data(), paramTypes.size(), 0);
    globalState->censusContains = LLVMAddFunction(globalState->mod, "__vcensusContains", funcType);
  }

  {
    LLVMTypeRef retType = LLVMVoidType();
    std::vector<LLVMTypeRef> paramTypes = {
        LLVMPointerType(LLVMVoidType(), 0),
    };
    LLVMTypeRef funcType = LLVMFunctionType(retType, paramTypes.data(), paramTypes.size(), 0);
    globalState->censusAdd = LLVMAddFunction(globalState->mod, "__vcensusAdd", funcType);
  }

  {
    LLVMTypeRef retType = LLVMVoidType();
    std::vector<LLVMTypeRef> paramTypes = {
        LLVMPointerType(LLVMVoidType(), 0),
    };
    LLVMTypeRef funcType = LLVMFunctionType(retType, paramTypes.data(), paramTypes.size(), 0);
    globalState->censusRemove = LLVMAddFunction(globalState->mod, "__vcensusRemove", funcType);
  }
}

void initInternalStructs(GlobalState* globalState) {
  {
    auto controlBlockStructL =
        LLVMStructCreateNamed(
            LLVMGetGlobalContext(), CONTROL_BLOCK_STRUCT_NAME);
    std::vector<LLVMTypeRef> memberTypesL;

    globalState->controlBlockTypeStrIndex = memberTypesL.size();
    memberTypesL.push_back(LLVMPointerType(LLVMInt8Type(), 0));

    globalState->controlBlockObjIdIndex = memberTypesL.size();
    memberTypesL.push_back(LLVMInt64Type());

    globalState->controlBlockRcMemberIndex = memberTypesL.size();
    memberTypesL.push_back(LLVMInt64Type());

    LLVMStructSetBody(
        controlBlockStructL, memberTypesL.data(), memberTypesL.size(), false);
    globalState->controlBlockStructL = controlBlockStructL;
  }

  {
    auto stringInnerStructL =
        LLVMStructCreateNamed(
            LLVMGetGlobalContext(), "__Str");
    std::vector<LLVMTypeRef> memberTypesL;
    memberTypesL.push_back(LLVMInt64Type());
    memberTypesL.push_back(LLVMArrayType(LLVMInt8Type(), 0));
    LLVMStructSetBody(
        stringInnerStructL, memberTypesL.data(), memberTypesL.size(), false);
    globalState->stringInnerStructL = stringInnerStructL;
  }

  {
    auto stringWrapperStructL =
        LLVMStructCreateNamed(
            LLVMGetGlobalContext(), "__Str_rc");
    std::vector<LLVMTypeRef> memberTypesL;
    memberTypesL.push_back(globalState->controlBlockStructL);
    memberTypesL.push_back(globalState->stringInnerStructL);
    LLVMStructSetBody(
        stringWrapperStructL, memberTypesL.data(), memberTypesL.size(), false);
    globalState->stringWrapperStructL = stringWrapperStructL;
  }
}

void compileValeCode(GlobalState* globalState, const char* filename) {
  std::ifstream instream(filename);
  std::string str(std::istreambuf_iterator<char>{instream}, {});

  assert(str.size() > 0);
  auto programJ = json::parse(str.c_str());
  auto program = readProgram(&globalState->metalCache, programJ);



  // Start making the entry function. We make it up here because we want its
  // builder for creating string constants. In a perfect world we wouldn't need
  // a builder for making string constants, but LLVM wants one, and it wants one
  // that's attached to a function.
  auto paramTypesL = std::vector<LLVMTypeRef>{
      LLVMInt64Type(),
      LLVMPointerType(LLVMPointerType(LLVMInt8Type(), 0), 0)
  };
  LLVMTypeRef functionTypeL =
      LLVMFunctionType(
          LLVMInt64Type(), paramTypesL.data(), paramTypesL.size(), 0);
  LLVMValueRef entryFunctionL =
      LLVMAddFunction(globalState->mod, "main", functionTypeL);
  LLVMSetLinkage(entryFunctionL, LLVMDLLExportLinkage);
  LLVMSetDLLStorageClass(entryFunctionL, LLVMDLLExportStorageClass);
  LLVMSetFunctionCallConv(entryFunctionL, LLVMX86StdcallCallConv);
  LLVMBuilderRef entryBuilder = LLVMCreateBuilder();
  LLVMBasicBlockRef blockL =
      LLVMAppendBasicBlock(entryFunctionL, "thebestblock");
  LLVMPositionBuilderAtEnd(entryBuilder, blockL);





  globalState->program = program;

  globalState->stringConstantBuilder = entryBuilder;

  globalState->liveHeapObjCounter =
      LLVMAddGlobal(globalState->mod, LLVMInt64Type(), "__liveHeapObjCounter");
//  LLVMSetLinkage(globalState->liveHeapObjCounter, LLVMExternalLinkage);
  LLVMSetInitializer(globalState->liveHeapObjCounter, LLVMConstInt(LLVMInt64Type(), 0, false));

  globalState->objIdCounter =
      LLVMAddGlobal(globalState->mod, LLVMInt64Type(), "__objIdCounter");
//  LLVMSetLinkage(globalState->liveHeapObjCounter, LLVMExternalLinkage);
  LLVMSetInitializer(globalState->objIdCounter, LLVMConstInt(LLVMInt64Type(), 501, false));

  initInternalStructs(globalState);
  initInternalExterns(globalState);

  for (auto p : program->structs) {
    auto name = p.first;
    auto structM = p.second;
    declareStruct(globalState, structM);
  }

  for (auto p : program->interfaces) {
    auto name = p.first;
    auto interfaceM = p.second;
    declareInterface(globalState, interfaceM);
  }

  for (auto p : program->structs) {
    auto name = p.first;
    auto structM = p.second;
    translateStruct(globalState, structM);
  }

  for (auto p : program->interfaces) {
    auto name = p.first;
    auto interfaceM = p.second;
    translateInterface(globalState, interfaceM);
  }

  for (auto p : program->structs) {
    auto name = p.first;
    auto structM = p.second;
    for (auto e : structM->edges) {
      declareEdge(globalState, e);
    }
  }

  LLVMValueRef mainL = nullptr;
  int numFuncs = program->functions.size();
  for (auto p : program->functions) {
    auto name = p.first;
    auto function = p.second;
    LLVMValueRef entryFunctionL = declareFunction(globalState, function);
    if (function->prototype->name->name == "F(\"main\")") {
      mainL = entryFunctionL;
    }
  }
  assert(mainL != nullptr);

  // We translate the edges after the functions are declared because the
  // functions have to exist for the itables to point to them.
  for (auto p : program->structs) {
    auto name = p.first;
    auto structM = p.second;
    for (auto e : structM->edges) {
      translateEdge(globalState, e);
    }
  }

  for (auto p : program->functions) {
    auto name = p.first;
    auto function = p.second;
    translateFunction(globalState, function);
  }



  LLVMValueRef emptyValues[1] = {};
  LLVMValueRef mainResult =
      LLVMBuildCall(entryBuilder, mainL, emptyValues, 0, "valeMainCall");

  LLVMValueRef args[2] = {
      LLVMConstInt(LLVMInt64Type(), 0, false),
      LLVMBuildLoad(entryBuilder, globalState->liveHeapObjCounter, "numLiveObjs")
  };
  LLVMBuildCall(entryBuilder, globalState->assertI64Eq, args, 2, "");

  LLVMBuildRet(entryBuilder, mainResult);
  LLVMDisposeBuilder(entryBuilder);
}

void createModule(GlobalState *globalState) {
  globalState->mod = LLVMModuleCreateWithNameInContext(globalState->opt->srcDirAndNameNoExt.c_str(), globalState->context);
  if (!globalState->opt->release) {
    globalState->dibuilder = LLVMCreateDIBuilder(globalState->mod);
    globalState->difile = LLVMDIBuilderCreateFile(globalState->dibuilder, "main.vale", 9, ".", 1);
    // If theres a compile error on this line, its some sort of LLVM version issue, try commenting or uncommenting the last four args.
    globalState->compileUnit = LLVMDIBuilderCreateCompileUnit(globalState->dibuilder, LLVMDWARFSourceLanguageC,
        globalState->difile, "Vale compiler", 13, 0, "", 0, 0, "", 0, LLVMDWARFEmissionFull, 0, 0, 0/*, "isysroothere", strlen("isysroothere"), "sdkhere", strlen("sdkhere")*/);
  }
  compileValeCode(globalState, globalState->opt->srcpath);
  if (!globalState->opt->release)
    LLVMDIBuilderFinalize(globalState->dibuilder);
}

// Use provided options (triple, etc.) to creation a machine
LLVMTargetMachineRef createMachine(ValeOptions *opt) {
  char *err;

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

  LLVMTargetRef target;
  if (LLVMGetTargetFromTriple(opt->triple, &target, &err) != 0) {
    errorExit(ExitCode::LlvmSetupFailed, "Could not create target: ", err);
    LLVMDisposeMessage(err);
    return NULL;
  }

  // Create a specific target machine

  LLVMCodeGenOptLevel opt_level = opt->release? LLVMCodeGenLevelAggressive : LLVMCodeGenLevelNone;

  LLVMRelocMode reloc = (opt->pic || opt->library)? LLVMRelocPIC : LLVMRelocDefault;
  if (!opt->cpu)
    opt->cpu = "generic";
  if (!opt->features)
    opt->features = "";

  LLVMTargetMachineRef machine;
  if (!(machine = LLVMCreateTargetMachine(target, opt->triple, opt->cpu, opt->features, opt_level, reloc, LLVMCodeModelDefault))) {
    errorExit(ExitCode::LlvmSetupFailed, "Could not create target machine");
    return NULL;
  }

  return machine;
}

// Generate requested object file
void generateOutput(
    const char *objpath,
    const char *asmpath,
    LLVMModuleRef mod,
    const char *triple,
    LLVMTargetMachineRef machine) {
  char *err;

  LLVMSetTarget(mod, triple);
  LLVMTargetDataRef dataref = LLVMCreateTargetDataLayout(machine);
  char *layout = LLVMCopyStringRepOfTargetData(dataref);
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
void generateModule(GlobalState *globalState) {
  char *err;

  // Generate IR to LLVM IR
  createModule(globalState);

  // Serialize the LLVM IR, if requested
  if (globalState->opt->print_llvmir && LLVMPrintModuleToFile(globalState->mod, fileMakePath(globalState->opt->output, globalState->opt->srcNameNoExt.c_str(), "ll").c_str(), &err) != 0) {
    std::cerr << "Could not emit pre-ir file: " << err << std::endl;
    LLVMDisposeMessage(err);
  }

  // Verify generated IR
  if (globalState->opt->verify) {
    char *error = NULL;
    LLVMVerifyModule(globalState->mod, LLVMAbortProcessAction, &error);
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
  if (globalState->opt->release)
    LLVMAddFunctionInliningPass(passmgr);        // Function inlining
  LLVMRunPassManager(passmgr, globalState->mod);
  LLVMDisposePassManager(passmgr);

  // Serialize the LLVM IR, if requested
  auto outputFilePath = fileMakePath(globalState->opt->output, globalState->opt->srcNameNoExt.c_str(), "opt.ll");
  std::cout << "Printing file " << outputFilePath << std::endl;
  if (globalState->opt->print_llvmir && LLVMPrintModuleToFile(globalState->mod, outputFilePath.c_str(), &err) != 0) {
    std::cerr << "Could not emit ir file: " << err << std::endl;
    LLVMDisposeMessage(err);
  }

  // Transform IR to target's ASM and OBJ
  if (globalState->machine) {
    auto objpath =
        fileMakePath(globalState->opt->output, globalState->opt->srcNameNoExt.c_str(),
            globalState->opt->wasm ? "wasm" : objext);
    auto asmpath =
        fileMakePath(globalState->opt->output,
            globalState->opt->srcNameNoExt.c_str(),
            globalState->opt->wasm ? "wat" : asmext);
    generateOutput(
        objpath.c_str(), globalState->opt->print_asm ? asmpath.c_str() : NULL,
        globalState->mod, globalState->opt->triple, globalState->machine);
  }


  LLVMDisposeModule(globalState->mod);
  // LLVMContextDispose(gen.context);  // Only need if we created a new context
}

// Setup LLVM generation, ensuring we know intended target
void setup(GlobalState *globalState, ValeOptions *opt) {
  globalState->opt = opt;

  LLVMTargetMachineRef machine = createMachine(opt);
  if (!machine)
    exit((int)(ExitCode::BadOpts));

  // Obtain data layout info, particularly pointer sizes
  globalState->machine = machine;
  globalState->dataLayout = LLVMCreateTargetDataLayout(machine);
  globalState->ptrSize = LLVMPointerSize(globalState->dataLayout) << 3u;

  // LLVM inlining bugs prevent use of LLVMContextCreate();
  globalState->context = LLVMGetGlobalContext();
}

void closeGlobalState(GlobalState *globalState) {
  LLVMDisposeTargetMachine(globalState->machine);
}


int main(int argc, char **argv) {
  ValeOptions valeOptions;

  // Get compiler's options from passed arguments
  int ok = valeOptSet(&valeOptions, &argc, argv);
  if (ok <= 0) {
    exit((int)(ok == 0 ? ExitCode::Success : ExitCode::BadOpts));
  }
  if (argc < 2)
    errorExit(ExitCode::BadOpts, "Specify a Vale program to compile.");
  valeOptions.srcpath = argv[1];
  new (&valeOptions.srcDir) std::string(fileDirectory(valeOptions.srcpath));
  new (&valeOptions.srcNameNoExt) std::string(getFileNameNoExt(valeOptions.srcpath));
  new (&valeOptions.srcDirAndNameNoExt) std::string(valeOptions.srcDir + valeOptions.srcNameNoExt);

  // We set up generation early because we need target info, e.g.: pointer size
  GlobalState globalState;
  setup(&globalState, &valeOptions);

  // Parse source file, do semantic analysis, and generate code
//    ModuleNode *modnode = NULL;
//    if (!errors)
  generateModule(&globalState);

  closeGlobalState(&globalState);
//    errorSummary();
}
