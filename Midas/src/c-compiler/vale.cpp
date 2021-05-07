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

#include "json.hpp"
#include "function/expressions/shared/shared.h"

#include "metal/types.h"
#include "metal/ast.h"
#include "metal/instructions.h"

#include "function/function.h"
#include "metal/readjson.h"
#include "error.h"
#include "translatetype.h"
#include "midasfunctions.h"
#include "externs.h"

#include <cstring>
#include <llvm-c/Transforms/Scalar.h>
#include <llvm-c/Transforms/Utils.h>
#include <llvm-c/Transforms/IPO.h>
#include <region/assist/assist.h>
#include <region/resilientv3/resilientv3.h>
#include <region/unsafe/unsafe.h>
#include <function/expressions/shared/string.h>
#include <sstream>
#include <region/linear/linear.h>
#include <function/expressions/shared/members.h>
#include <function/expressions/expressions.h>
#include <region/naiverc/naiverc.h>
#include <region/resilientv4/resilientv4.h>

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

std::tuple<LLVMValueRef, LLVMBuilderRef> makeStringSetupFunction(GlobalState* globalState);
Prototype* makeValeMainFunction(
    GlobalState* globalState,
    LLVMValueRef stringSetupFunctionL,
    Prototype* mainSetupFuncProto,
    Prototype* userMainFunctionPrototype,
    Prototype* mainCleanupFunctionPrototype);
LLVMValueRef makeEntryFunction(
    GlobalState* globalState,
    Prototype* valeMainPrototype);

LLVMValueRef declareFunction(
  GlobalState* globalState,
  Function* functionM,
  bool skipExporting);

void initInternalExterns(GlobalState* globalState) {
//  auto voidLT = LLVMVoidTypeInContext(globalState->context);
  auto int1LT = LLVMInt1TypeInContext(globalState->context);
  auto int8LT = LLVMInt8TypeInContext(globalState->context);
  auto int32LT = LLVMInt32TypeInContext(globalState->context);
  auto int32PtrLT = LLVMPointerType(int32LT, 0);
  auto int64LT = LLVMInt64TypeInContext(globalState->context);
  auto voidPtrLT = LLVMPointerType(int8LT, 0);
  auto int8PtrLT = LLVMPointerType(int8LT, 0);

  globalState->genMalloc = addExtern(globalState->mod, "__genMalloc", voidPtrLT, {int64LT});
  globalState->genFree = addExtern(globalState->mod, "__genFree", LLVMVoidTypeInContext(globalState->context), {voidPtrLT});

  {
    globalState->wrcTableStructLT = LLVMStructCreateNamed(globalState->context, "__WRCTable");
    std::vector<LLVMTypeRef> memberTypesL;
    memberTypesL.push_back(int32LT);
    memberTypesL.push_back(int32LT);
    memberTypesL.push_back(int32PtrLT);
    LLVMStructSetBody(
        globalState->wrcTableStructLT, memberTypesL.data(), memberTypesL.size(), false);
  }

  {
    globalState->lgtEntryStructLT = LLVMStructCreateNamed(globalState->context, "__LgtEntry");

    std::vector<LLVMTypeRef> memberTypesL;

    assert(LGT_ENTRY_MEMBER_INDEX_FOR_GEN == memberTypesL.size());
    memberTypesL.push_back(LLVMInt32TypeInContext(globalState->context));

    assert(LGT_ENTRY_MEMBER_INDEX_FOR_NEXT_FREE == memberTypesL.size());
    memberTypesL.push_back(LLVMInt32TypeInContext(globalState->context));

    LLVMStructSetBody(globalState->lgtEntryStructLT, memberTypesL.data(), memberTypesL.size(), false);
  }

  {
    globalState->lgtTableStructLT = LLVMStructCreateNamed(globalState->context, "__LgtTable");
    std::vector<LLVMTypeRef> memberTypesL;
    memberTypesL.push_back(LLVMInt32TypeInContext(globalState->context));
    memberTypesL.push_back(LLVMInt32TypeInContext(globalState->context));
    memberTypesL.push_back(LLVMPointerType(globalState->lgtEntryStructLT, 0));
    LLVMStructSetBody(globalState->lgtTableStructLT, memberTypesL.data(), memberTypesL.size(), false);
  }


  globalState->expandWrcTable =
      addExtern(
          globalState->mod, "__expandWrcTable",
          LLVMVoidTypeInContext(globalState->context),
          {
              LLVMPointerType(globalState->wrcTableStructLT, 0)
          });
  globalState->checkWrci =
      addExtern(
          globalState->mod, "__checkWrc",
          LLVMVoidTypeInContext(globalState->context),
          {
              LLVMPointerType(globalState->wrcTableStructLT, 0),
              int32LT
          });
  globalState->getNumWrcs =
      addExtern(
          globalState->mod, "__getNumWrcs",
          int32LT,
          {
              LLVMPointerType(globalState->wrcTableStructLT, 0),
          });

  globalState->expandLgt =
      addExtern(
          globalState->mod, "__expandLgt",
          LLVMVoidTypeInContext(globalState->context),
          {
              LLVMPointerType(globalState->lgtTableStructLT, 0),
          });
  globalState->checkLgti =
      addExtern(
          globalState->mod, "__checkLgti",
          LLVMVoidTypeInContext(globalState->context),
          {
              LLVMPointerType(globalState->lgtTableStructLT, 0),
              int32LT
          });
  globalState->getNumLiveLgtEntries =
      addExtern(
          globalState->mod, "__getNumLiveLgtEntries",
          int32LT,
          {
              LLVMPointerType(globalState->lgtTableStructLT, 0),
          });
}

void generateExports(GlobalState* globalState, Prototype* mainM) {
  auto program = globalState->program;
  auto cByExportedName = std::unordered_map<std::string, std::string>();
  for (auto p : program->structs) {
    auto structM = p.second;
    // can we think of this in terms of regions? it's kind of like we're
    // generating some stuff for the outside to point inside.
    if (globalState->program->isExported(structM->name)) {
      if (structM->mutability == Mutability::IMMUTABLE) {
        globalState->linearRegion->generateStructDefsC(&cByExportedName, structM);
      } else {
        globalState->mutRegion->generateStructDefsC(&cByExportedName, structM);
      }
    }
  }
  for (auto p : program->unknownSizeArrays) {
    auto usaDefM = p.second;
    // can we think of this in terms of regions? it's kind of like we're
    // generating some stuff for the outside to point inside.
    if (globalState->program->isExported(usaDefM->name)) {
      if (usaDefM->rawArray->mutability == Mutability::IMMUTABLE) {
        globalState->linearRegion->generateUnknownSizeArrayDefsC(&cByExportedName, usaDefM);
      } else {
        globalState->mutRegion->generateUnknownSizeArrayDefsC(&cByExportedName, usaDefM);
      }
    }
  }
  for (auto p : program->interfaces) {
    auto interfaceM = p.second;
    if (globalState->program->isExported(interfaceM->name)) {
      if (interfaceM->mutability == Mutability::IMMUTABLE) {
        globalState->linearRegion->generateInterfaceDefsC(&cByExportedName, interfaceM);
      } else {
        globalState->mutRegion->generateInterfaceDefsC(&cByExportedName, interfaceM);
      }
    }
  }
  for (auto p : program->functions) {
    auto functionM = p.second;
    if (functionM->prototype->name != mainM->name &&
        globalState->program->isExported(functionM->prototype->name)) {
      auto exportedName = program->getExportedName(functionM->prototype->name);
      std::stringstream s;
      auto externReturnType = globalState->getRegion(functionM->prototype->returnType)->getExternalType(functionM->prototype->returnType);
      s << "extern " << globalState->getRegion(externReturnType)->getRefNameC(externReturnType) << " ";
      s << exportedName << "(";
      for (int i = 0; i < functionM->prototype->params.size(); i++) {
        if (i > 0) {
          s << ", ";
        }
        auto hostParamRefMT = globalState->getRegion(functionM->prototype->params[i])->getExternalType(functionM->prototype->params[i]);
        s << globalState->getRegion(hostParamRefMT)->getRefNameC(hostParamRefMT) << " param" << i;
      }
      s << ");" << std::endl;
      cByExportedName.insert(std::make_pair(exportedName, s.str()));
    }
  }
  for (auto p : cByExportedName) {
    auto exportedName = p.first;
    auto c = p.second;
    std::string filepath = "";
    if (!globalState->opt->exportsDir.empty()) {
      filepath = globalState->opt->exportsDir + "/";
    }
    filepath += exportedName + ".h";
    std::ofstream out(filepath, std::ofstream::out);
    if (!out) {
      std::cerr << "Couldn't make file '" << filepath << std::endl;
      exit(1);
    }
    std::cout << "Writing " << filepath << std::endl;
    out << c;
  }
}

void compileValeCode(GlobalState* globalState, const std::string& filename) {
  auto voidLT = LLVMVoidTypeInContext(globalState->context);
  auto int8LT = LLVMInt8TypeInContext(globalState->context);
  auto int64LT = LLVMInt64TypeInContext(globalState->context);
  auto int32LT = LLVMInt32TypeInContext(globalState->context);
  auto int32PtrLT = LLVMPointerType(int32LT, 0);
  auto int8PtrLT = LLVMPointerType(int8LT, 0);

  if (globalState->opt->regionOverride == RegionOverride::RESILIENT_V3 ||
      globalState->opt->regionOverride == RegionOverride::RESILIENT_V4) {
    if (!globalState->opt->genHeap) {
      std::cerr << "Error: using resilient without generational heap, overriding generational heap to true!" << std::endl;
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
    case RegionOverride::FAST:
      std::cout << "Region override: fast" << std::endl;
      break;
    case RegionOverride::RESILIENT_V3:
      std::cout << "Region override: resilient-v3" << std::endl;
      break;
    case RegionOverride::RESILIENT_V4:
      std::cout << "Region override: resilient-v4" << std::endl;
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
  if (str.size() == 0) {
    std::cerr << "Nothing found in " << filename << std::endl;
    exit(1);
  }


  AddressNumberer addressNumberer;
  MetalCache metalCache(&addressNumberer);
  globalState->metalCache = &metalCache;

  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
      metalCache.mutRegionId = metalCache.assistRegionId;
      break;
    case RegionOverride::FAST:
      metalCache.mutRegionId = metalCache.unsafeRegionId;
      break;
    case RegionOverride::NAIVE_RC:
      metalCache.mutRegionId = metalCache.naiveRcRegionId;
      break;
    case RegionOverride::RESILIENT_V3:
      metalCache.mutRegionId = metalCache.resilientV3RegionId;
      break;
    case RegionOverride::RESILIENT_V4:
      metalCache.mutRegionId = metalCache.resilientV4RegionId;
      break;
    default:
      assert(false);
  }

  json programJ;
  try {
    programJ = json::parse(str.c_str());
  }
  catch (const nlohmann::detail::parse_error& error) {
    std::cerr << "Error while parsing json: " << error.what() << std::endl;
    exit(1);
  }
  auto program = readProgram(&metalCache, programJ);

  assert(globalState->metalCache->emptyTupleStruct != nullptr);
  assert(globalState->metalCache->emptyTupleStructRef != nullptr);


  LLVMValueRef stringSetupFunctionL = nullptr;
  LLVMBuilderRef stringConstantBuilder = nullptr;
  std::tie(stringSetupFunctionL, stringConstantBuilder) = makeStringSetupFunction(globalState);
  globalState->stringConstantBuilder = stringConstantBuilder;

  globalState->ram64Struct =
      LLVMStructCreateNamed(globalState->context, "__Ram64Struct");
  LLVMTypeRef memberI64 = LLVMInt64TypeInContext(globalState->context);
  LLVMStructSetBody(globalState->ram64Struct, &memberI64, 1, false);


  globalState->program = program;

  globalState->serializeName = globalState->metalCache->getName("__vale_serialize");
  globalState->serializeThunkName = globalState->metalCache->getName("__vale_serialize_thunk");
  globalState->unserializeName = globalState->metalCache->getName("__vale_unserialize");
  globalState->unserializeThunkName = globalState->metalCache->getName("__vale_unserialize_thunk");

  Externs externs(globalState->mod, globalState->context);
  globalState->externs = &externs;

//  globalState->stringConstantBuilder = entryBuilder;

  globalState->numMainArgs =
      LLVMAddGlobal(globalState->mod, LLVMInt64TypeInContext(globalState->context), "__main_num_args");
  LLVMSetLinkage(globalState->numMainArgs, LLVMExternalLinkage);
  LLVMSetInitializer(globalState->numMainArgs, LLVMConstInt(LLVMInt64TypeInContext(globalState->context), 0, false));

  auto mainArgsLT =
      LLVMPointerType(LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0), 0);
  globalState->mainArgs =
      LLVMAddGlobal(globalState->mod, mainArgsLT, "__main_args");
  LLVMSetLinkage(globalState->mainArgs, LLVMExternalLinkage);
  LLVMSetInitializer(globalState->mainArgs, LLVMConstNull(mainArgsLT));

  globalState->liveHeapObjCounter =
      LLVMAddGlobal(globalState->mod, LLVMInt64TypeInContext(globalState->context), "__liveHeapObjCounter");
  LLVMSetInitializer(globalState->liveHeapObjCounter, LLVMConstInt(LLVMInt64TypeInContext(globalState->context), 0, false));

  globalState->writeOnlyGlobal =
      LLVMAddGlobal(globalState->mod, LLVMInt64TypeInContext(globalState->context), "__writeOnlyGlobal");
  LLVMSetInitializer(globalState->writeOnlyGlobal, LLVMConstInt(LLVMInt64TypeInContext(globalState->context), 0, false));

  globalState->crashGlobal =
      LLVMAddGlobal(globalState->mod, LLVMPointerType(LLVMInt64TypeInContext(globalState->context), 0), "__crashGlobal");
  LLVMSetInitializer(globalState->crashGlobal, LLVMConstNull(LLVMPointerType(LLVMInt64TypeInContext(globalState->context), 0)));

  globalState->ram64 =
      LLVMAddGlobal(globalState->mod, LLVMPointerType(globalState->ram64Struct, 0), "__ram64");
  LLVMSetInitializer(globalState->ram64, LLVMConstNull(LLVMPointerType(globalState->ram64Struct, 0)));

  globalState->ram64IndexToWriteOnlyGlobal =
      LLVMAddGlobal(globalState->mod, LLVMInt64TypeInContext(globalState->context), "__ram64IndexToWriteOnlyGlobal");
  LLVMSetInitializer(globalState->ram64IndexToWriteOnlyGlobal, constI64LE(globalState, 0));

  globalState->objIdCounter =
      LLVMAddGlobal(globalState->mod, LLVMInt64TypeInContext(globalState->context), "__objIdCounter");
  LLVMSetInitializer(globalState->objIdCounter, LLVMConstInt(LLVMInt64TypeInContext(globalState->context), 501, false));

  globalState->derefCounter =
      LLVMAddGlobal(globalState->mod, LLVMInt64TypeInContext(globalState->context), "derefCounter");
  LLVMSetInitializer(globalState->derefCounter, LLVMConstInt(LLVMInt64TypeInContext(globalState->context), 0, false));

  globalState->neverPtr = LLVMAddGlobal(globalState->mod, makeNeverType(globalState), "__never");
  LLVMValueRef empty[1] = {};
  LLVMSetInitializer(globalState->neverPtr, LLVMConstArray(LLVMIntTypeInContext(globalState->context, NEVER_INT_BITS), empty, 0));

  globalState->mutRcAdjustCounter =
      LLVMAddGlobal(globalState->mod, LLVMInt64TypeInContext(globalState->context), "__mutRcAdjustCounter");
  LLVMSetInitializer(globalState->mutRcAdjustCounter, LLVMConstInt(LLVMInt64TypeInContext(globalState->context), 0, false));

  globalState->livenessCheckCounter =
      LLVMAddGlobal(globalState->mod, LLVMInt64TypeInContext(globalState->context), "__livenessCheckCounter");
  LLVMSetInitializer(globalState->livenessCheckCounter, LLVMConstInt(LLVMInt64TypeInContext(globalState->context), 0, false));

  initInternalExterns(globalState);

  RCImm rcImm(globalState);
  globalState->rcImm = &rcImm;
  globalState->regions.emplace(globalState->rcImm->getRegionId(), globalState->rcImm);
  Assist assistRegion(globalState);
  globalState->assistRegion = &assistRegion;
  globalState->regions.emplace(globalState->assistRegion->getRegionId(), globalState->assistRegion);
  NaiveRC naiveRcRegion(globalState, globalState->metalCache->naiveRcRegionId);
  globalState->naiveRcRegion = &naiveRcRegion;
  globalState->regions.emplace(globalState->naiveRcRegion->getRegionId(), globalState->naiveRcRegion);
  Unsafe unsafeRegion(globalState);
  globalState->unsafeRegion = &unsafeRegion;
  globalState->regions.emplace(globalState->unsafeRegion->getRegionId(), globalState->unsafeRegion);
  Linear linearRegion(globalState);
  globalState->linearRegion = &linearRegion;
  globalState->regions.emplace(globalState->linearRegion->getRegionId(), globalState->linearRegion);
  
  ResilientV3 resilientV3Region(globalState, globalState->metalCache->resilientV3RegionId);
  globalState->resilientV3Region = &resilientV3Region;
  globalState->regions.emplace(globalState->resilientV3Region->getRegionId(), globalState->resilientV3Region);
  /*ResilientV4 resilientV4Region(globalState, globalState->metalCache->resilientV4RegionId);
  globalState->resilientV4Region = &resilientV4Region;
  globalState->regions.emplace(globalState->resilientV4Region->getRegionId(), globalState->resilientV4Region);
  */
//  Mega megaRegion(globalState);
  globalState->mutRegion = globalState->getRegion(metalCache.mutRegionId);


  assert(LLVMTypeOf(globalState->neverPtr) == globalState->getRegion(globalState->metalCache->neverRef)->translateType(globalState->metalCache->neverRef));

  auto mainSetupFuncName = globalState->metalCache->getName("__Vale_mainSetup");
  auto mainSetupFuncProto =
      globalState->metalCache->getPrototype(mainSetupFuncName, globalState->metalCache->intRef, {});
  declareAndDefineExtraFunction(
      globalState, mainSetupFuncProto, mainSetupFuncName->name,
      [globalState](FunctionState* functionState, LLVMBuilderRef builder) {
        buildFlare(FL(), globalState, functionState, builder);
        for (auto i : globalState->regions) {
          buildFlare(FL(), globalState, functionState, builder);
          i.second->mainSetup(functionState, builder);
          buildFlare(FL(), globalState, functionState, builder);
        }
        buildFlare(FL(), globalState, functionState, builder);
        LLVMBuildRet(builder, constI64LE(globalState, 0));
      });

  for (auto p : program->structs) {
    auto name = p.first;
    auto structM = p.second;
    globalState->getRegion(structM->regionId)->declareStruct(structM);
    if (structM->mutability == Mutability::IMMUTABLE) {
      globalState->linearRegion->declareStruct(structM);
    }
  }

  for (auto p : program->interfaces) {
    auto name = p.first;
    auto interfaceM = p.second;
    globalState->getRegion(interfaceM->regionId)->declareInterface(interfaceM);
    if (interfaceM->mutability == Mutability::IMMUTABLE) {
      globalState->linearRegion->declareInterface(interfaceM);
    }
  }

  for (auto p : program->knownSizeArrays) {
    auto name = p.first;
    auto arrayM = p.second;
    globalState->getRegion(arrayM->rawArray->regionId)->declareKnownSizeArray(arrayM);
    if (arrayM->rawArray->mutability == Mutability::IMMUTABLE) {
      globalState->linearRegion->declareKnownSizeArray(arrayM);
    }
  }

  for (auto p : program->unknownSizeArrays) {
    auto name = p.first;
    auto arrayM = p.second;
    globalState->getRegion(arrayM->rawArray->regionId)->declareUnknownSizeArray(arrayM);
    if (arrayM->rawArray->mutability == Mutability::IMMUTABLE) {
      globalState->linearRegion->declareUnknownSizeArray(arrayM);
    }
  }

  for (auto p : program->structs) {
    auto name = p.first;
    auto structM = p.second;
    globalState->getRegion(structM->regionId)->declareStructExtraFunctions(structM);
    if (structM->mutability == Mutability::IMMUTABLE) {
      globalState->linearRegion->declareStructExtraFunctions(structM);
    }
  }

  for (auto p : program->interfaces) {
    auto name = p.first;
    auto interfaceM = p.second;
    globalState->getRegion(interfaceM->regionId)->declareInterfaceExtraFunctions(interfaceM);
    if (interfaceM->mutability == Mutability::IMMUTABLE) {
      globalState->linearRegion->declareInterfaceExtraFunctions(interfaceM);
    }
  }

  for (auto p : program->knownSizeArrays) {
    auto name = p.first;
    auto arrayM = p.second;
    globalState->getRegion(arrayM->rawArray->regionId)->declareKnownSizeArrayExtraFunctions(arrayM);
    if (arrayM->rawArray->mutability == Mutability::IMMUTABLE) {
      globalState->linearRegion->declareKnownSizeArrayExtraFunctions(arrayM);
    }
  }

  for (auto p : program->unknownSizeArrays) {
    auto name = p.first;
    auto arrayM = p.second;
    globalState->getRegion(arrayM->rawArray->regionId)->declareUnknownSizeArrayExtraFunctions(arrayM);
    if (arrayM->rawArray->mutability == Mutability::IMMUTABLE) {
      globalState->linearRegion->declareUnknownSizeArrayExtraFunctions(arrayM);
    }
  }

  // This is here before any defines because:
  // 1. It has to be before we define any extra functions for structs etc because the linear
  //    region's extra functions need to know all the substructs for interfaces so it can number
  //    them, which is used in supporting its interface calling.
  // 2. Everything else is declared here too and it seems consistent
  for (auto p : program->structs) {
    auto name = p.first;
    auto structM = p.second;
    for (auto e : structM->edges) {
      globalState->getRegion(structM->regionId)->declareEdge(e);
      if (structM->mutability == Mutability::IMMUTABLE) {
        globalState->linearRegion->declareEdge(e);
      }
    }
  }

  for (auto p : program->structs) {
    auto name = p.first;
    auto structM = p.second;
    assert(name == structM->name->name);
    globalState->getRegion(structM->regionId)->defineStruct(structM);
    if (structM->mutability == Mutability::IMMUTABLE) {
      globalState->linearRegion->defineStruct(structM);
    }
  }

  // This must be before we start defining extra functions, because some of them might rely
  // on knowing the interface tables' layouts to make interface calls.
  for (auto p : program->interfaces) {
    auto name = p.first;
    auto interfaceM = p.second;
    globalState->getRegion(interfaceM->regionId)->defineInterface(interfaceM);
    if (interfaceM->mutability == Mutability::IMMUTABLE) {
      globalState->linearRegion->defineInterface(interfaceM);
    }
  }

  for (auto p : program->knownSizeArrays) {
    auto name = p.first;
    auto arrayM = p.second;
    globalState->getRegion(arrayM->rawArray->regionId)->defineKnownSizeArray(arrayM);
    if (arrayM->rawArray->mutability == Mutability::IMMUTABLE) {
      globalState->linearRegion->defineKnownSizeArray(arrayM);
    }
  }

  for (auto p : program->unknownSizeArrays) {
    auto name = p.first;
    auto arrayM = p.second;
    globalState->getRegion(arrayM->rawArray->regionId)->defineUnknownSizeArray(arrayM);
    if (arrayM->rawArray->mutability == Mutability::IMMUTABLE) {
      globalState->linearRegion->defineUnknownSizeArray(arrayM);
    }
  }

  // This has to come after we declare all the other structs, because we
  // add functions for all the known structs and interfaces.
  // It also has to be after we *define* them, because they want to access members.
  // But it has to be before we translate interfaces, because thats when we manifest
  // the itable layouts.
  for (auto region : globalState->regions) {
    region.second->declareExtraFunctions();
  }

  for (auto p : program->structs) {
    auto name = p.first;
    auto structM = p.second;
    assert(name == structM->name->name);
    globalState->getRegion(structM->regionId)->defineStructExtraFunctions(structM);
    if (structM->mutability == Mutability::IMMUTABLE) {
      globalState->linearRegion->defineStructExtraFunctions(structM);
    }
  }

  for (auto p : program->knownSizeArrays) {
    auto name = p.first;
    auto arrayM = p.second;
    globalState->getRegion(arrayM->rawArray->regionId)->defineKnownSizeArrayExtraFunctions(arrayM);
    if (arrayM->rawArray->mutability == Mutability::IMMUTABLE) {
      globalState->linearRegion->defineKnownSizeArrayExtraFunctions(arrayM);
    }
  }

  for (auto p : program->unknownSizeArrays) {
    auto name = p.first;
    auto arrayM = p.second;
    globalState->getRegion(arrayM->rawArray->regionId)->defineUnknownSizeArrayExtraFunctions(arrayM);
    if (arrayM->rawArray->mutability == Mutability::IMMUTABLE) {
      globalState->linearRegion->defineUnknownSizeArrayExtraFunctions(arrayM);
    }
  }

  // This keeps us from accidentally adding interfaces and interface methods after we've
  // started compiling interfaces.
  globalState->interfacesOpen = false;

  for (auto p : program->interfaces) {
    auto name = p.first;
    auto interfaceM = p.second;
    globalState->getRegion(interfaceM->regionId)->defineInterfaceExtraFunctions(interfaceM);
    if (interfaceM->mutability == Mutability::IMMUTABLE) {
      globalState->linearRegion->defineInterfaceExtraFunctions(interfaceM);
    }
  }

  for (auto region : globalState->regions) {
    region.second->defineExtraFunctions();
  }

  for (auto p : program->externs) {
    auto name = p.first;
    auto prototype = p.second;
    declareExternFunction(globalState, prototype);
  }


  for (auto p : program->functions) {
    auto name = p.first;
    auto function = p.second;
    bool skipExporting =
        program->isExported(function->prototype->name) &&
        program->getExportedName(function->prototype->name) == "main";
    declareFunction(globalState, function, skipExporting);
  }

  for (auto p : program->functions) {
    auto name = p.first;
    auto function = p.second;
    translateFunction(globalState, function);
  }

  // We translate the edges after the functions are declared because the
  // functions have to exist for the itables to point to them.
  for (auto p : program->structs) {
    auto name = p.first;
    auto structM = p.second;
    for (auto e : structM->edges) {

      if (structM->mutability == Mutability::IMMUTABLE) {
        globalState->rcImm->defineEdge(e);
        globalState->linearRegion->defineEdge(e);
      } else {
        globalState->mutRegion->defineEdge(e);
      }
    }
  }



  auto mainCleanupFuncName = globalState->metalCache->getName("__Vale_mainCleanup");
  auto mainCleanupFuncProto =
      globalState->metalCache->getPrototype(mainCleanupFuncName, globalState->metalCache->intRef, {});
  declareAndDefineExtraFunction(
      globalState, mainCleanupFuncProto, mainCleanupFuncName->name,
      [globalState](FunctionState* functionState, LLVMBuilderRef builder) {
        for (auto i : globalState->regions) {
          i.second->mainCleanup(functionState, builder);
        }
        LLVMBuildRet(builder, constI64LE(globalState, 0));
      });



  Prototype* mainM = nullptr;
  int numFuncs = program->functions.size();
  for (auto p : program->functions) {
    auto name = p.first;
    auto function = p.second;
//    LLVMValueRef entryFunctionL = declareFunction(globalState, function);
    bool isExportedMain = program->isExported(function->prototype->name) &&
                          program->getExportedName(function->prototype->name) == "main";
//    bool isExternMain = program->externs.find(function->prototype->name->name) != program->externs.end() &&
//                        function->prototype->name->name == "main";
    if (isExportedMain) {
      mainM = function->prototype;
    }
  }
  if (mainM == nullptr) {
    std::cerr << "Couldn't find main function! (Did you forget to export it?)" << std::endl;
    exit(1);
  }
  auto valeMainPrototype =
      makeValeMainFunction(
          globalState, stringSetupFunctionL, mainSetupFuncProto, mainM, mainCleanupFuncProto);
  auto entryFuncL = makeEntryFunction(globalState, valeMainPrototype);

  generateExports(globalState, mainM);
}

void createModule(GlobalState *globalState) {
  globalState->mod = LLVMModuleCreateWithNameInContext(globalState->opt->srcDirAndNameNoExt.c_str(), globalState->context);
  if (!globalState->opt->release) {
    globalState->dibuilder = LLVMCreateDIBuilder(globalState->mod);
    globalState->difile = LLVMDIBuilderCreateFile(globalState->dibuilder, "main.vale", 9, ".", 1);
    // If theres a compile error on this line, its some sort of LLVM version issue, try commenting or uncommenting the last four args.
    globalState->compileUnit =
        LLVMDIBuilderCreateCompileUnit(
            globalState->dibuilder, LLVMDWARFSourceLanguageC, globalState->difile, "Vale compiler",
            13, 0, "", 0, 0, "", 0, LLVMDWARFEmissionFull, 0, 0, 0,
            "isysroothere", strlen("isysroothere"), "sdkhere", strlen("sdkhere"));
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
    const std::string& objPath,
    const std::string& asmPath,
    LLVMModuleRef mod,
    const char *triple,
    LLVMTargetMachineRef machine) {
  char *err = nullptr;

  LLVMSetTarget(mod, triple);
  LLVMTargetDataRef dataref = LLVMCreateTargetDataLayout(machine);
  char *layout = LLVMCopyStringRepOfTargetData(dataref);
  LLVMSetDataLayout(mod, layout);
  LLVMDisposeMessage(layout);

  if (!asmPath.empty()) {
    // Generate assembly file if requested
    if (LLVMTargetMachineEmitToFile(machine, mod, const_cast<char*>(asmPath.c_str()),
        LLVMAssemblyFile, &err) != 0) {
      std::cerr << "Could not emit asm file: " << asmPath << std::endl;
      LLVMDisposeMessage(err);
    }
  }

  // Generate .o or .obj file
  if (LLVMTargetMachineEmitToFile(machine, mod, const_cast<char*>(objPath.c_str()), LLVMObjectFile, &err) != 0) {
    std::cerr << "Could not emit obj file to path " << objPath << " " << err << std::endl;
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
    LLVMVerifyModule(globalState->mod, LLVMReturnStatusAction, &error);
    if (error) {
      if (*error)
        errorExit(ExitCode::VerifyFailed, "Module verification failed:\n", error);
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
        objpath.c_str(), globalState->opt->print_asm ? asmpath : "",
        globalState->mod, globalState->opt->triple.c_str(), globalState->machine);
  }

  LLVMDisposeModule(globalState->mod);
  // LLVMContextDispose(gen.context);  // Only need if we created a new context
}

// Setup LLVM generation, ensuring we know intended target
void setup(GlobalState *globalState, ValeOptions *opt) {
  globalState->opt = opt;

  // LLVM inlining bugs prevent use of LLVMContextCreate();
  globalState->context = LLVMContextCreate();

  LLVMTargetMachineRef machine = createMachine(opt);
  if (!machine)
    exit((int)(ExitCode::BadOpts));

  // Obtain data layout info, particularly pointer sizes
  globalState->machine = machine;
  globalState->dataLayout = LLVMCreateTargetDataLayout(machine);
  globalState->ptrSize = LLVMPointerSize(globalState->dataLayout) << 3u;

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
  AddressNumberer addressNumberer;
  GlobalState globalState(&addressNumberer);
  setup(&globalState, &valeOptions);

  // Parse source file, do semantic analysis, and generate code
//    ModuleNode *modnode = NULL;
//    if (!errors)
  generateModule(&globalState);

  closeGlobalState(&globalState);
//    errorSummary();
}
