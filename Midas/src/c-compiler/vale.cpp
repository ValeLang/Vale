#include <llvm-c/Core.h>
#include <llvm-c/DebugInfo.h>
#include <llvm-c/ExecutionEngine.h>
#include <llvm-c/Analysis.h>
#include <llvm-c/IRReader.h>

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
#include "translatetype.h"

#include <cstring>
#include <llvm-c/Transforms/Scalar.h>
#include <llvm-c/Transforms/Utils.h>
#include <llvm-c/Transforms/IPO.h>
#include <struct/array.h>
#include <function/expressions/shared/weaks.h>
#include <region/assist/assist.h>
#include <region/mega/mega.h>

#ifdef _WIN32
#define asmext "asm"
#define objext "obj"
#else
#define asmext "s"
#define objext "o"
#endif

// for convenience
using json = nlohmann::json;

std::string genMallocName(int bytes) {
  return std::string("__genMalloc") + std::to_string(bytes) + std::string("B");
}
std::string genFreeName(int bytes) {
  return std::string("__genMalloc") + std::to_string(bytes) + std::string("B");
}

void initInternalExterns(GlobalState* globalState) {
  auto voidLT = LLVMVoidType();
  auto voidPtrLT = LLVMPointerType(voidLT, 0);
  auto int1LT = LLVMInt1Type();
  auto int8LT = LLVMInt8Type();
  auto int32LT = LLVMInt32Type();
  auto int64LT = LLVMInt64Type();
  auto int8PtrLT = LLVMPointerType(int8LT, 0);

  auto stringInnerStructPtrLT = LLVMPointerType(globalState->region->getStringInnerStruct(), 0);

  globalState->genMalloc = addExtern(globalState->mod, "__genMalloc", voidPtrLT, {int64LT});
  globalState->genFree = addExtern(globalState->mod, "__genFree", voidLT, {voidPtrLT});
  globalState->malloc = addExtern(globalState->mod, "malloc", int8PtrLT, {int64LT});
  globalState->free = addExtern(globalState->mod, "free", voidLT, {int8PtrLT});

  globalState->exit = addExtern(globalState->mod, "exit", voidLT, {int8LT});
  globalState->assert = addExtern(globalState->mod, "__vassert", voidLT, {int1LT, int8PtrLT});
  globalState->assertI64Eq = addExtern(globalState->mod, "__vassertI64Eq", voidLT,
      {int64LT, int64LT, int8PtrLT});
  globalState->flareI64 = addExtern(globalState->mod, "__vflare_i64", voidLT, {int64LT, int64LT});
  globalState->printCStr = addExtern(globalState->mod, "__vprintCStr", voidLT, {int8PtrLT});
  globalState->getch = addExtern(globalState->mod, "getchar", int64LT, {});
  globalState->printInt = addExtern(globalState->mod, "__vprintI64", voidLT, {int64LT});
  globalState->printBool = addExtern(globalState->mod, "__vprintBool", voidLT, {int1LT});
  globalState->initStr =
      addExtern(globalState->mod, "__vinitStr", voidLT,
          {stringInnerStructPtrLT, int8PtrLT});
  globalState->addStr =
      addExtern(globalState->mod, "__vaddStr", voidLT,
          {stringInnerStructPtrLT, stringInnerStructPtrLT, stringInnerStructPtrLT});
  globalState->eqStr =
      addExtern(globalState->mod, "__veqStr", int8LT,
          {stringInnerStructPtrLT, stringInnerStructPtrLT});
  globalState->printVStr =
      addExtern(globalState->mod, "__vprintStr", voidLT,
          {stringInnerStructPtrLT});
  globalState->intToCStr = addExtern(globalState->mod, "__vintToCStr", voidLT,
      {int64LT, int8PtrLT, int64LT});
  globalState->strlen = addExtern(globalState->mod, "strlen", int64LT, {int8PtrLT});
  globalState->censusContains = addExtern(globalState->mod, "__vcensusContains", int64LT,
      {voidPtrLT});
  globalState->censusAdd = addExtern(globalState->mod, "__vcensusAdd", voidLT, {voidPtrLT});
  globalState->censusRemove = addExtern(globalState->mod, "__vcensusRemove", voidLT, {voidPtrLT});

  initWeakInternalExterns(globalState);
}


void compileValeCode(GlobalState* globalState, const std::string& filename) {
//  std::cout << "OVERRIDING to resilient-v2 mode!" << std::endl;
//  globalState->opt->regionOverride = RegionOverride::RESILIENT_V2;

//  std::cout << "OVERRIDING to assist mode!" << std::endl;
//  globalState->opt->regionOverride = RegionOverride::ASSIST;

//  std::cout << "OVERRIDING census to true!" << std::endl;
//  globalState->opt->census = true;
//  std::cout << "OVERRIDING flares to true!" << std::endl;
//  globalState->opt->flares = true;

//  std::cout << "OVERRIDING gen-heap to true!" << std::endl;
//  globalState->opt->genHeap = true;


  if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V2) {
    if (!globalState->opt->genHeap) {
      std::cerr << "Error: using resilient v2 without generational heap, overriding generational heap to true!" << std::endl;
      globalState->opt->genHeap = true;
    }
  }


  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
      std::cout << "Region override: assist" << std::endl;
      break;
    case RegionOverride::NAIVE_RC:
      std::cout << "Region override: naive-rc" << std::endl;
      break;
    case RegionOverride::RESILIENT_V0:
      std::cout << "Region override: resilient-v0" << std::endl;
      break;
    case RegionOverride::FAST:
      std::cout << "Region override: fast" << std::endl;
      break;
    case RegionOverride::RESILIENT_V1:
      std::cout << "Region override: resilient-v1" << std::endl;
      break;
    case RegionOverride::RESILIENT_V2:
      std::cout << "Region override: resilient-v2" << std::endl;
      break;
    default:
      assert(false);
      break;
  }

  if (globalState->opt->regionOverride == RegionOverride::ASSIST) {
    if (!globalState->opt->census) {
      std::cout << "Warning: not using census when in assist mode!" << std::endl;
    }
  } else {
    if (globalState->opt->flares) {
      std::cout << "Warning: using flares outside of assist mode, will slow things down!" << std::endl;
    }
    if (globalState->opt->census) {
      std::cout << "Warning: using census outside of assist mode, will slow things down!" << std::endl;
    }
  }


  std::ifstream instream(filename);
  std::string str(std::istreambuf_iterator<char>{instream}, {});

  assert(str.size() > 0);
  auto programJ = json::parse(str.c_str());
  auto program = readProgram(&globalState->metalCache, programJ);

  assert(globalState->metalCache.emptyTupleStruct != nullptr);
  assert(globalState->metalCache.emptyTupleStructRef != nullptr);


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
  LLVMSetInitializer(globalState->liveHeapObjCounter, LLVMConstInt(LLVMInt64Type(), 0, false));

  globalState->objIdCounter =
      LLVMAddGlobal(globalState->mod, LLVMInt64Type(), "__objIdCounter");
  LLVMSetInitializer(globalState->objIdCounter, LLVMConstInt(LLVMInt64Type(), 501, false));

  globalState->derefCounter =
      LLVMAddGlobal(globalState->mod, LLVMInt64Type(), "derefCounter");
  LLVMSetInitializer(globalState->derefCounter, LLVMConstInt(LLVMInt64Type(), 0, false));

  globalState->neverPtr = LLVMAddGlobal(globalState->mod, makeNeverType(), "__never");
  LLVMValueRef empty[1] = {};
  LLVMSetInitializer(globalState->neverPtr, LLVMConstArray(LLVMIntType(NEVER_INT_BITS), empty, 0));

  globalState->mutRcAdjustCounter =
      LLVMAddGlobal(globalState->mod, LLVMInt64Type(), "__mutRcAdjustCounter");
  LLVMSetInitializer(globalState->mutRcAdjustCounter, LLVMConstInt(LLVMInt64Type(), 0, false));

//  Assist assistRegion(globalState);
//  Mega megaRegion(globalState);
  IRegion* defaultRegion = nullptr;
  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
      defaultRegion = new Assist(globalState);
      break;
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST:
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2:
      defaultRegion = new Mega(globalState);
      break;
    default:
      assert(false);
  }
  globalState->region = defaultRegion;

  initInternalExterns(globalState);

  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
      std::cout << "Region override: assist" << std::endl;
      break;
    case RegionOverride::NAIVE_RC:
      std::cout << "Region override: naive-rc" << std::endl;
      break;
    case RegionOverride::RESILIENT_V0:
      std::cout << "Region override: resilient-v0" << std::endl;
      break;
    case RegionOverride::FAST:
      std::cout << "Region override: fast" << std::endl;
      break;
    case RegionOverride::RESILIENT_V1:
      std::cout << "Region override: resilient-v1" << std::endl;
      break;
    case RegionOverride::RESILIENT_V2:
      std::cout << "Region override: resilient-v2" << std::endl;
      break;
    default:
      assert(false);
      break;
  }


  assert(LLVMTypeOf(globalState->neverPtr) == defaultRegion->translateType(globalState->metalCache.neverRef));

  for (auto p : program->structs) {
    auto name = p.first;
    auto structM = p.second;
    defaultRegion->declareStruct(structM);
  }

  for (auto p : program->interfaces) {
    auto name = p.first;
    auto interfaceM = p.second;
    defaultRegion->declareInterface(interfaceM);
  }

  for (auto p : program->knownSizeArrays) {
    auto name = p.first;
    auto arrayM = p.second;
    defaultRegion->declareKnownSizeArray(arrayM);
  }

  for (auto p : program->unknownSizeArrays) {
    auto name = p.first;
    auto arrayM = p.second;
    defaultRegion->declareUnknownSizeArray(arrayM);
  }

  for (auto p : program->structs) {
    auto name = p.first;
    auto structM = p.second;
    defaultRegion->translateStruct(structM);
  }

  for (auto p : program->interfaces) {
    auto name = p.first;
    auto interfaceM = p.second;
    defaultRegion->translateInterface(interfaceM);
  }

  for (auto p : program->knownSizeArrays) {
    auto name = p.first;
    auto arrayM = p.second;
    defaultRegion->translateKnownSizeArray(arrayM);
  }

  for (auto p : program->unknownSizeArrays) {
    auto name = p.first;
    auto arrayM = p.second;
    defaultRegion->translateUnknownSizeArray(arrayM);
  }

  for (auto p : program->structs) {
    auto name = p.first;
    auto structM = p.second;
    for (auto e : structM->edges) {
      defaultRegion->declareEdge(e);
    }
  }

  Prototype* mainM = nullptr;
  LLVMValueRef mainL = nullptr;
  int numFuncs = program->functions.size();
  for (auto p : program->functions) {
    auto name = p.first;
    auto function = p.second;
    LLVMValueRef entryFunctionL = declareFunction(globalState, defaultRegion, function);
    if (function->prototype->name->name == "main") {
      mainM = function->prototype;
      mainL = entryFunctionL;
    }
  }
  assert(mainL != nullptr);
  assert(mainM != nullptr);

  // We translate the edges after the functions are declared because the
  // functions have to exist for the itables to point to them.
  for (auto p : program->structs) {
    auto name = p.first;
    auto structM = p.second;
    for (auto e : structM->edges) {
      defaultRegion->translateEdge(e);
    }
  }

  for (auto p : program->functions) {
    auto name = p.first;
    auto function = p.second;
    translateFunction(globalState, defaultRegion, function);
  }


  if (globalState->opt->census) {
    // Add all the edges to the census, so we can check that fat pointers are right.
    // We remove them again at the end of outer main.
    // We should one day do this for all globals.
    for (auto edgeAndItablePtr : globalState->interfaceTablePtrs) {
      auto itablePtrLE = edgeAndItablePtr.second;
      LLVMValueRef itablePtrAsVoidPtrLE =
          LLVMBuildBitCast(
              entryBuilder, itablePtrLE, LLVMPointerType(LLVMVoidType(), 0), "");
      LLVMBuildCall(entryBuilder, globalState->censusAdd, &itablePtrAsVoidPtrLE, 1, "");
    }
  }

  LLVMValueRef emptyValues[1] = {};
  LLVMValueRef mainResult =
      LLVMBuildCall(entryBuilder, mainL, emptyValues, 0, "");


  if (globalState->opt->census) {
    // Remove all the things from the census that we added at the start of the program.
    for (auto edgeAndItablePtr : globalState->interfaceTablePtrs) {
      auto itablePtrLE = edgeAndItablePtr.second;
      LLVMValueRef itablePtrAsVoidPtrLE =
          LLVMBuildBitCast(
              entryBuilder, itablePtrLE, LLVMPointerType(LLVMVoidType(), 0), "");
      LLVMBuildCall(entryBuilder, globalState->censusRemove, &itablePtrAsVoidPtrLE, 1, "");
    }

    LLVMValueRef args[3] = {
        LLVMConstInt(LLVMInt64Type(), 0, false),
        LLVMBuildLoad(entryBuilder, globalState->liveHeapObjCounter, "numLiveObjs"),
        globalState->getOrMakeStringConstant("Memory leaks!"),
    };
    LLVMBuildCall(entryBuilder, globalState->assertI64Eq, args, 3, "");
  }

  if (globalState->opt->census) {
    if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V1) {
      LLVMValueRef args[3] = {
          LLVMConstInt(LLVMInt64Type(), 0, false),
          LLVMBuildZExt(
              entryBuilder,
              LLVMBuildCall(
                  entryBuilder, globalState->getNumLiveLgtEntries, nullptr, 0, "numLgtEntries"),
              LLVMInt64Type(),
              ""),
          globalState->getOrMakeStringConstant("WRC leaks!"),
      };
      LLVMBuildCall(entryBuilder, globalState->assertI64Eq, args, 3, "");
    } else {
      LLVMValueRef args[3] = {
          LLVMConstInt(LLVMInt64Type(), 0, false),
          LLVMBuildZExt(
              entryBuilder,
              LLVMBuildCall(
                  entryBuilder, globalState->getNumWrcs, nullptr, 0, "numWrcs"),
              LLVMInt64Type(),
              ""),
          globalState->getOrMakeStringConstant("WRC leaks!"),
      };
      LLVMBuildCall(entryBuilder, globalState->assertI64Eq, args, 3, "");
    }
  }

  if (mainM->returnType->referend == globalState->metalCache.vooid) {
    LLVMBuildRet(entryBuilder, LLVMConstInt(LLVMInt64Type(), 0, true));
  } else if (mainM->returnType->referend == globalState->metalCache.emptyTupleStruct) {
    LLVMBuildRet(entryBuilder, LLVMConstInt(LLVMInt64Type(), 0, true));
  } else if (mainM->returnType->referend == globalState->metalCache.innt) {
    LLVMBuildRet(entryBuilder, mainResult);
  } else if (mainM->returnType->referend == globalState->metalCache.never) {
    LLVMBuildRet(entryBuilder, LLVMConstInt(LLVMInt64Type(), 0, true));
  } else {
    assert(false);
  }
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
  if (opt->triple.empty())
    opt->triple = LLVMGetDefaultTargetTriple();

  LLVMTargetRef target;
  if (LLVMGetTargetFromTriple(opt->triple.c_str(), &target, &err) != 0) {
    errorExit(ExitCode::LlvmSetupFailed, "Could not create target: ", err);
    LLVMDisposeMessage(err);
    return NULL;
  }

  // Create a specific target machine

  LLVMCodeGenOptLevel opt_level = opt->release? LLVMCodeGenLevelAggressive : LLVMCodeGenLevelNone;

  LLVMRelocMode reloc = (opt->pic || opt->library)? LLVMRelocPIC : LLVMRelocDefault;
  if (opt->cpu.empty())
    opt->cpu = "generic";
  if (opt->features.empty())
    opt->features = "";

  LLVMTargetMachineRef machine;
  if (!(machine = LLVMCreateTargetMachine(target, opt->triple.c_str(), opt->cpu.c_str(), opt->features.c_str(), opt_level, reloc, LLVMCodeModelDefault))) {
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
//
//LLVMModuleRef loadLLFileIntoModule(LLVMContextRef context, const std::string& file) {
//  char* errorMessage = nullptr;
//  LLVMMemoryBufferRef buffer = nullptr;
//  if (LLVMCreateMemoryBufferWithContentsOfFile(file.c_str(), &buffer, &errorMessage) != 0) {
//    std::cerr << "Couldnt create buffer: " << errorMessage << std::endl;
//    exit(1);
//  }
//  LLVMModuleRef newMod = nullptr;
//  if (LLVMParseIRInContext(context, buffer, &newMod, &errorMessage) != 0) {
//    std::cerr << "Couldnt load file: " << errorMessage << std::endl;
//    exit(1);
//  }
//  LLVMDisposeMemoryBuffer(buffer);
//}

// Generate IR nodes into LLVM IR using LLVM
void generateModule(GlobalState *globalState) {
  char *err;

  // Generate IR to LLVM IR
  createModule(globalState);

  // Serialize the LLVM IR, if requested
  if (globalState->opt->print_llvmir) {
    auto outputFilePath = fileMakePath(globalState->opt->output.c_str(), globalState->opt->srcNameNoExt.c_str(), "ll");
    std::cout << "Printing file " << outputFilePath << std::endl;
    if (LLVMPrintModuleToFile(globalState->mod, outputFilePath.c_str(), &err) != 0) {
      std::cerr << "Could not emit pre-ir file: " << err << std::endl;
      LLVMDisposeMessage(err);
    }
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
//  LLVMAddPromoteMemoryToRegisterPass(passmgr);     // Demote allocas to registers.
////  LLVMAddInstructionCombiningPass(passmgr);        // Do simple "peephole" and bit-twiddling optimizations
//  LLVMAddReassociatePass(passmgr);                 // Reassociate expressions.
//  LLVMAddGVNPass(passmgr);                         // Eliminate common subexpressions.
//  LLVMAddCFGSimplificationPass(passmgr);           // Simplify the control flow graph

//  if (globalState->opt->release) {
//    LLVMAddFunctionInliningPass(passmgr);        // Function inlining
//  }
  LLVMRunPassManager(passmgr, globalState->mod);
  LLVMDisposePassManager(passmgr);

  // Serialize the LLVM IR, if requested
  if (globalState->opt->print_llvmir) {
    auto outputFilePath = fileMakePath(globalState->opt->output.c_str(), globalState->opt->srcNameNoExt.c_str(), "opt.ll");
    std::cout << "Printing file " << outputFilePath << std::endl;
    if (LLVMPrintModuleToFile(globalState->mod, outputFilePath.c_str(), &err) != 0) {
      std::cerr << "Could not emit ir file: " << err << std::endl;
      LLVMDisposeMessage(err);
    }
  }

  // Transform IR to target's ASM and OBJ
  if (globalState->machine) {
    auto objpath =
        fileMakePath(globalState->opt->output.c_str(), globalState->opt->srcNameNoExt.c_str(),
            globalState->opt->wasm ? "wasm" : objext);
    auto asmpath =
        fileMakePath(globalState->opt->output.c_str(),
            globalState->opt->srcNameNoExt.c_str(),
            globalState->opt->wasm ? "wat" : asmext);
    generateOutput(
        objpath.c_str(), globalState->opt->print_asm ? asmpath.c_str() : NULL,
        globalState->mod, globalState->opt->triple.c_str(), globalState->machine);
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
  valeOptions.srcDir = std::string(fileDirectory(valeOptions.srcpath));
  valeOptions.srcNameNoExt = std::string(getFileNameNoExt(valeOptions.srcpath));
  valeOptions.srcDirAndNameNoExt = std::string(valeOptions.srcDir + valeOptions.srcNameNoExt);

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
