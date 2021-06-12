#include <llvm-c/Core.h>
#include <llvm-c/DebugInfo.h>
#include <llvm-c/ExecutionEngine.h>
#include <llvm-c/Analysis.h>
#include <llvm-c/IRReader.h>

#include <sys/stat.h>

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
  Function* functionM);

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
  auto packageCoordToHeaderNameToC =
      std::unordered_map<
          PackageCoordinate*,
          std::unordered_map<std::string, std::stringstream>,
          AddressHasher<PackageCoordinate*>>(
      0, globalState->addressNumberer->makeHasher<PackageCoordinate*>());

  std::stringstream builtinExportsCode;
  builtinExportsCode << "#include <stdint.h>" << std::endl;
  builtinExportsCode << "#include <stdlib.h>" << std::endl;
  builtinExportsCode << "#include <string.h>" << std::endl;
  builtinExportsCode << "typedef int32_t ValeInt;" << std::endl;
  builtinExportsCode << "typedef struct { ValeInt length; char chars[0]; } ValeStr;" << std::endl;
  builtinExportsCode << "ValeStr* ValeStrNew(ValeInt length);" << std::endl;
  builtinExportsCode << "ValeStr* ValeStrFrom(char* source);" << std::endl;
  builtinExportsCode << "#define ValeReleaseMessage(msg) (free(*((void**)(msg) - 2)))" << std::endl;

  {
    packageCoordToHeaderNameToC[globalState->metalCache->builtinPackageCoord]
        .emplace("ValeBuiltins", std::stringstream()).first->second
        << builtinExportsCode.str();
  }

  for (auto[packageCoord, package] : program->packages) {
    for (auto[exportName, kind] : package->exportNameToKind) {
      if (auto structMT = dynamic_cast<StructKind*>(kind)) {
        auto structDefM = globalState->program->getStruct(structMT);
        // can we think of this in terms of regions? it's kind of like we're
        // generating some stuff for the outside to point inside.
        auto region = (structDefM->mutability == Mutability::IMMUTABLE ? globalState->linearRegion : globalState->mutRegion);
        auto defString = region->generateStructDefsC(package, structDefM);
        packageCoordToHeaderNameToC[packageCoord].emplace(exportName, std::stringstream()).first->second << defString;
      } else if (auto interfaceMT = dynamic_cast<InterfaceKind*>(kind)) {
        auto interfaceDefM = globalState->program->getInterface(interfaceMT);
        auto region = (interfaceDefM->mutability == Mutability::IMMUTABLE ? globalState->linearRegion : globalState->mutRegion);
        auto defString = region->generateInterfaceDefsC(package, interfaceDefM);
        packageCoordToHeaderNameToC[packageCoord].emplace(exportName, std::stringstream()).first->second << defString;
      } else if (auto ssaMT = dynamic_cast<StaticSizedArrayT*>(kind)) {
        auto ssaDefM = globalState->program->getStaticSizedArray(ssaMT);
        // can we think of this in terms of regions? it's kind of like we're
        // generating some stuff for the outside to point inside.
        auto region = (ssaDefM->rawArray->mutability == Mutability::IMMUTABLE ? globalState->linearRegion : globalState->mutRegion);
        auto defString = region->generateStaticSizedArrayDefsC(package, ssaDefM);
        packageCoordToHeaderNameToC[packageCoord].emplace(exportName, std::stringstream()).first->second << defString;
      } else if (auto rsaMT = dynamic_cast<RuntimeSizedArrayT*>(kind)) {
        auto rsaDefM = globalState->program->getRuntimeSizedArray(rsaMT);
        // can we think of this in terms of regions? it's kind of like we're
        // generating some stuff for the outside to point inside.
        auto region = (rsaDefM->rawArray->mutability == Mutability::IMMUTABLE ? globalState->linearRegion : globalState->mutRegion);
        auto defString = region->generateRuntimeSizedArrayDefsC(package, rsaDefM);
        packageCoordToHeaderNameToC[packageCoord].emplace(exportName, std::stringstream()).first->second << defString;
      } else {
        std::cerr << "Unknown exportee: " << typeid(*kind).name() << std::endl;
        assert(false);
        exit(1);
      }
    }
  }
  for (auto[packageCoord, package] : program->packages) {
    for (auto[exportName, prototype] : package->exportNameToFunction) {
      auto functionM = program->getFunction(prototype->name);
      bool skipExporting = exportName == "main";
      if (!skipExporting) {
        std::stringstream s;
        auto externReturnType = globalState->getRegion(functionM->prototype->returnType)->getExternalType(functionM->prototype->returnType);
        auto returnExportName = globalState->getRegion(externReturnType)->getExportName(package, externReturnType, true);
        s << "extern " << returnExportName << " ";
        s << packageCoord->projectName << "_" << exportName << "(";
        for (int i = 0; i < functionM->prototype->params.size(); i++) {
          if (i > 0) {
            s << ", ";
          }
          auto hostParamRefMT = globalState->getRegion(functionM->prototype->params[i])->getExternalType(functionM->prototype->params[i]);
          auto paramExportName = globalState->getRegion(hostParamRefMT)->getExportName(package, hostParamRefMT, true);
          s << paramExportName << " param" << i;
        }
        s << ");" << std::endl;

        packageCoordToHeaderNameToC[packageCoord].emplace(exportName, std::stringstream()).first->second << s.str() << std::endl;
      }
    }
  }
  for (auto& [packageCoord, headerNameToC] : packageCoordToHeaderNameToC) {
    for (auto& [exportedName, exportCode] : headerNameToC) {
      if (globalState->opt->outputDir.empty()) {
        std::cerr << "Must specify --output-dir!" << std::endl;
        assert(false);
      }
      std::string moduleExternsDirectory = globalState->opt->outputDir;
      if (!packageCoord->projectName.empty()) {
        moduleExternsDirectory += "/" + packageCoord->projectName;
        int failed = mkdir(moduleExternsDirectory.c_str(), 0700);
        if (failed) {
          int error = errno;
          if (error == EEXIST) {
            // do nothing
            std::cerr << "Directory " << moduleExternsDirectory << " already exists, continuing." << std::endl;
          } else {
            std::cerr << "Couldn't make directory: " << moduleExternsDirectory << " (" << error << ")" << std::endl;
            exit(1);
          }
        }
      }

      std::string filepath = moduleExternsDirectory + "/" + exportedName + ".h";
      std::ofstream out(filepath, std::ofstream::out);
      if (!out) {
        std::cerr << "Couldn't make file '" << filepath << std::endl;
        exit(1);
      }
      // std::cout << "Writing " << filepath << std::endl;

      out << "#ifndef VALE_EXPORTS_" << exportedName << "_H_" << std::endl;
      out << "#define VALE_EXPORTS_" << exportedName << "_H_" << std::endl;
      out << "#include \"ValeBuiltins.h\"" << std::endl;
      out << exportCode.str();
      out << "#endif" << std::endl;
    }
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


  globalState->program = program;

  globalState->serializeName = globalState->metalCache->getName(globalState->metalCache->builtinPackageCoord, "__vale_serialize");
  globalState->serializeThunkName = globalState->metalCache->getName(globalState->metalCache->builtinPackageCoord, "__vale_serialize_thunk");
  globalState->unserializeName = globalState->metalCache->getName(globalState->metalCache->builtinPackageCoord, "__vale_unserialize");
  globalState->unserializeThunkName = globalState->metalCache->getName(globalState->metalCache->builtinPackageCoord, "__vale_unserialize_thunk");

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
  LLVMSetLinkage(globalState->mainArgs, LLVMExternalLinkage);
  LLVMSetInitializer(globalState->liveHeapObjCounter, LLVMConstInt(LLVMInt64TypeInContext(globalState->context), 0, false));

  globalState->writeOnlyGlobal =
      LLVMAddGlobal(globalState->mod, LLVMInt64TypeInContext(globalState->context), "__writeOnlyGlobal");
  LLVMSetInitializer(globalState->writeOnlyGlobal, LLVMConstInt(LLVMInt64TypeInContext(globalState->context), 0, false));

  globalState->crashGlobal =
      LLVMAddGlobal(globalState->mod, LLVMPointerType(LLVMInt64TypeInContext(globalState->context), 0), "__crashGlobal");
  LLVMSetInitializer(globalState->crashGlobal, LLVMConstNull(LLVMPointerType(LLVMInt64TypeInContext(globalState->context), 0)));

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

  auto mainSetupFuncName = globalState->metalCache->getName(globalState->metalCache->builtinPackageCoord, "__Vale_mainSetup");
  auto mainSetupFuncProto =
      globalState->metalCache->getPrototype(mainSetupFuncName, globalState->metalCache->i64Ref, {});
  declareAndDefineExtraFunction(
      globalState, mainSetupFuncProto, mainSetupFuncName->name,
      [globalState](FunctionState* functionState, LLVMBuilderRef builder) {
        for (auto i : globalState->regions) {
          i.second->mainSetup(functionState, builder);
        }
        LLVMBuildRet(builder, constI64LE(globalState, 0));
      });

  for (auto packageCoordAndPackage : program->packages) {
    auto[packageCoord, package] = packageCoordAndPackage;
    for (auto p : package->structs) {
      auto name = p.first;
      auto structM = p.second;
      globalState->getRegion(structM->regionId)->declareStruct(structM);
      if (structM->mutability == Mutability::IMMUTABLE) {
        globalState->linearRegion->declareStruct(structM);
      }
    }
  }

  for (auto packageCoordAndPackage : program->packages) {
    auto[packageCoord, package] = packageCoordAndPackage;
    for (auto p : package->interfaces) {
      auto name = p.first;
      auto interfaceM = p.second;
      globalState->getRegion(interfaceM->regionId)->declareInterface(interfaceM);
      if (interfaceM->mutability == Mutability::IMMUTABLE) {
        globalState->linearRegion->declareInterface(interfaceM);
      }
    }
  }

  for (auto packageCoordAndPackage : program->packages) {
    auto[packageCoord, package] = packageCoordAndPackage;
    for (auto p : package->staticSizedArrays) {
      auto name = p.first;
      auto arrayM = p.second;
      globalState->getRegion(arrayM->rawArray->regionId)->declareStaticSizedArray(arrayM);
      if (arrayM->rawArray->mutability == Mutability::IMMUTABLE) {
        globalState->linearRegion->declareStaticSizedArray(arrayM);
      }
    }
  }

  for (auto packageCoordAndPackage : program->packages) {
    auto[packageCoord, package] = packageCoordAndPackage;
    for (auto p : package->runtimeSizedArrays) {
      auto name = p.first;
      auto arrayM = p.second;
      globalState->getRegion(arrayM->rawArray->regionId)->declareRuntimeSizedArray(arrayM);
      if (arrayM->rawArray->mutability == Mutability::IMMUTABLE) {
        globalState->linearRegion->declareRuntimeSizedArray(arrayM);
      }
    }
  }

  for (auto packageCoordAndPackage : program->packages) {
    auto[packageCoord, package] = packageCoordAndPackage;
    for (auto p : package->structs) {
      auto name = p.first;
      auto structM = p.second;
      globalState->getRegion(structM->regionId)->declareStructExtraFunctions(structM);
      if (structM->mutability == Mutability::IMMUTABLE) {
        globalState->linearRegion->declareStructExtraFunctions(structM);
      }
    }
  }

  for (auto[packageCoord, package] : program->packages) {
    for (auto p : package->interfaces) {
      auto name = p.first;
      auto interfaceM = p.second;
      globalState->getRegion(interfaceM->regionId)->declareInterfaceExtraFunctions(interfaceM);
      if (interfaceM->mutability == Mutability::IMMUTABLE) {
        globalState->linearRegion->declareInterfaceExtraFunctions(interfaceM);
      }
    }
  }

  for (auto[packageCoord, package] : program->packages) {
    for (auto p : package->staticSizedArrays) {
      auto name = p.first;
      auto arrayM = p.second;
      globalState->getRegion(arrayM->rawArray->regionId)->declareStaticSizedArrayExtraFunctions(arrayM);
      if (arrayM->rawArray->mutability == Mutability::IMMUTABLE) {
        globalState->linearRegion->declareStaticSizedArrayExtraFunctions(arrayM);
      }
    }
  }

  for (auto[packageCoord, package] : program->packages) {
    for (auto p : package->runtimeSizedArrays) {
      auto name = p.first;
      auto arrayM = p.second;
      globalState->getRegion(arrayM->rawArray->regionId)->declareRuntimeSizedArrayExtraFunctions(arrayM);
      if (arrayM->rawArray->mutability == Mutability::IMMUTABLE) {
        globalState->linearRegion->declareRuntimeSizedArrayExtraFunctions(arrayM);
      }
    }
  }

  // This is here before any defines because:
  // 1. It has to be before we define any extra functions for structs etc because the linear
  //    region's extra functions need to know all the substructs for interfaces so it can number
  //    them, which is used in supporting its interface calling.
  // 2. Everything else is declared here too and it seems consistent
  for (auto[packageCoord, package] : program->packages) {
    for (auto p : package->structs) {
      auto name = p.first;
      auto structM = p.second;
      for (auto e : structM->edges) {
        globalState->getRegion(structM->regionId)->declareEdge(e);
        if (structM->mutability == Mutability::IMMUTABLE) {
          globalState->linearRegion->declareEdge(e);
        }
      }
    }
  }

  for (auto[packageCoord, package] : program->packages) {
    for (auto p : package->structs) {
      auto name = p.first;
      auto structM = p.second;
      assert(name == structM->name->name);
      globalState->getRegion(structM->regionId)->defineStruct(structM);
      if (structM->mutability == Mutability::IMMUTABLE) {
        globalState->linearRegion->defineStruct(structM);
      }
    }
  }

  // This must be before we start defining extra functions, because some of them might rely
  // on knowing the interface tables' layouts to make interface calls.
  for (auto[packageCoord, package] : program->packages) {
    for (auto p : package->interfaces) {
      auto name = p.first;
      auto interfaceM = p.second;
      globalState->getRegion(interfaceM->regionId)->defineInterface(interfaceM);
      if (interfaceM->mutability == Mutability::IMMUTABLE) {
        globalState->linearRegion->defineInterface(interfaceM);
      }
    }
  }

  for (auto[packageCoord, package] : program->packages) {
    for (auto p : package->staticSizedArrays) {
      auto name = p.first;
      auto arrayM = p.second;
      globalState->getRegion(arrayM->rawArray->regionId)->defineStaticSizedArray(arrayM);
      if (arrayM->rawArray->mutability == Mutability::IMMUTABLE) {
        globalState->linearRegion->defineStaticSizedArray(arrayM);
      }
    }
  }

  for (auto[packageCoord, package] : program->packages) {
    for (auto p : package->runtimeSizedArrays) {
      auto name = p.first;
      auto arrayM = p.second;
      globalState->getRegion(arrayM->rawArray->regionId)->defineRuntimeSizedArray(arrayM);
      if (arrayM->rawArray->mutability == Mutability::IMMUTABLE) {
        globalState->linearRegion->defineRuntimeSizedArray(arrayM);
      }
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

  for (auto[packageCoord, package] : program->packages) {
    for (auto p : package->structs) {
      auto name = p.first;
      auto structM = p.second;
      assert(name == structM->name->name);
      globalState->getRegion(structM->regionId)->defineStructExtraFunctions(structM);
      if (structM->mutability == Mutability::IMMUTABLE) {
        globalState->linearRegion->defineStructExtraFunctions(structM);
      }
    }
  }

  for (auto[packageCoord, package] : program->packages) {
    for (auto p : package->staticSizedArrays) {
      auto name = p.first;
      auto arrayM = p.second;
      globalState->getRegion(arrayM->rawArray->regionId)->defineStaticSizedArrayExtraFunctions(arrayM);
      if (arrayM->rawArray->mutability == Mutability::IMMUTABLE) {
        globalState->linearRegion->defineStaticSizedArrayExtraFunctions(arrayM);
      }
    }
  }

  for (auto[packageCoord, package] : program->packages) {
    for (auto p : package->runtimeSizedArrays) {
      auto name = p.first;
      auto arrayM = p.second;
      globalState->getRegion(arrayM->rawArray->regionId)->defineRuntimeSizedArrayExtraFunctions(arrayM);
      if (arrayM->rawArray->mutability == Mutability::IMMUTABLE) {
        globalState->linearRegion->defineRuntimeSizedArrayExtraFunctions(arrayM);
      }
    }
  }

  // This keeps us from accidentally adding interfaces and interface methods after we've
  // started compiling interfaces.
  globalState->interfacesOpen = false;

  for (auto[packageCoord, package] : program->packages) {
    for (auto p : package->interfaces) {
      auto name = p.first;
      auto interfaceM = p.second;
      globalState->getRegion(interfaceM->regionId)->defineInterfaceExtraFunctions(interfaceM);
      if (interfaceM->mutability == Mutability::IMMUTABLE) {
        globalState->linearRegion->defineInterfaceExtraFunctions(interfaceM);
      }
    }
  }

  for (auto region : globalState->regions) {
    region.second->defineExtraFunctions();
  }

  for (auto[packageCoord, package] : program->packages) {
    for (auto[externName, prototype] : package->externNameToFunction) {
      declareExternFunction(globalState, package, prototype);
    }
  }

  for (auto[packageCoord, package] : program->packages) {
    for (auto[name, function] : package->functions) {
      declareFunction(globalState, function);
    }
  }

  for (auto[packageCoord, package] : program->packages) {
    for (auto[exportName, prototype] : package->exportNameToFunction) {
      bool skipExporting = exportName == "main";
      if (!skipExporting) {
        auto function = program->getFunction(prototype->name);
        exportFunction(globalState, package, function);
      }
    }
  }

  for (auto[packageCoord, package] : program->packages) {
    for (auto p : package->functions) {
      auto name = p.first;
      auto function = p.second;
      translateFunction(globalState, function);
    }
  }

  // We translate the edges after the functions are declared because the
  // functions have to exist for the itables to point to them.
  for (auto[packageCoord, package] : program->packages) {
    for (auto p : package->structs) {
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
  }



  auto mainCleanupFuncName = globalState->metalCache->getName(globalState->metalCache->builtinPackageCoord, "__Vale_mainCleanup");
  auto mainCleanupFuncProto =
      globalState->metalCache->getPrototype(mainCleanupFuncName, globalState->metalCache->i64Ref, {});
  declareAndDefineExtraFunction(
      globalState, mainCleanupFuncProto, mainCleanupFuncName->name,
      [globalState](FunctionState* functionState, LLVMBuilderRef builder) {
        for (auto i : globalState->regions) {
          i.second->mainCleanup(functionState, builder);
        }
        LLVMBuildRet(builder, constI64LE(globalState, 0));
      });



  Prototype* mainM = nullptr;
  for (auto[packageCoord, package] : program->packages) {
    for (auto[exportName, prototype] : package->exportNameToFunction) {
      auto function = program->getFunction(prototype->name);
      bool isExportedMain = exportName == "main";
      if (isExportedMain) {
        mainM = function->prototype;
      }
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
    auto outputFilePath = fileMakePath(globalState->opt->outputDir.c_str(), globalState->opt->srcNameNoExt.c_str(), "ll");
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
    auto outputFilePath = fileMakePath(globalState->opt->outputDir.c_str(), globalState->opt->srcNameNoExt.c_str(), "opt.ll");
    std::cout << "Printing file " << outputFilePath << std::endl;
    if (LLVMPrintModuleToFile(globalState->mod, outputFilePath.c_str(), &err) != 0) {
      std::cerr << "Could not emit ir file: " << err << std::endl;
      LLVMDisposeMessage(err);
    }
  }

  // Transform IR to target's ASM and OBJ
  if (globalState->machine) {
    auto objpath =
        fileMakePath(globalState->opt->outputDir.c_str(), globalState->opt->srcNameNoExt.c_str(),
            globalState->opt->wasm ? "wasm" : objext);
    auto asmpath =
        fileMakePath(globalState->opt->outputDir.c_str(),
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
