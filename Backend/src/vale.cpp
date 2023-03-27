#include <llvm-c/Core.h>
#include <llvm-c/DebugInfo.h>
#include <llvm-c/ExecutionEngine.h>
#include <llvm-c/Analysis.h>
#include <llvm-c/IRReader.h>
#include "llvm/Passes/PassBuilder.h"

#include <sys/stat.h>

#include <assert.h>
#include <string>
#include <vector>
#include <iostream>
#include <fstream>
#include <filesystem>


#include "json.hpp"
#include "function/expressions/shared/shared.h"

#include "metal/types.h"
#include "metal/ast.h"
#include "metal/instructions.h"

#include "function/function.h"
#include "determinism/determinism.h"
#include "metal/readjson.h"
#include "error.h"
#include "translatetype.h"
#include "externs.h"

#include <cstring>
#include <llvm-c/Transforms/Scalar.h>
#include <llvm-c/Transforms/Utils.h>
#include <llvm-c/Transforms/IPO.h>
#include "region/resilientv3/resilientv3.h"
#include "region/unsafe/unsafe.h"
#include "function/expressions/shared/string.h"
#include <sstream>
#include "region/linear/linear.h"
#include "function/expressions/shared/members.h"
#include "function/expressions/expressions.h"
#include "region/naiverc/naiverc.h"

#ifdef _WIN32
#define asmext "asm"
#define objext "obj"
#else
#define asmext "s"
#define objext "o"
#endif

// for convenience
using json = nlohmann::json;
template <typename Out>
void split(const std::string &s, char delim, Out result) {
  std::istringstream iss(s);
  std::string item;
  while (std::getline(iss, item, delim)) {
    *result++ = item;
  }
}

std::vector<std::string> split(const std::string &s, char delim) {
  std::vector<std::string> elems;
  split(s, delim, std::back_inserter(elems));
  return elems;
}

std::string genMallocName(int bytes) {
  return std::string("__genMalloc") + std::to_string(bytes) + std::string("B");
}
std::string genFreeName(int bytes) {
  return std::string("__genMalloc") + std::to_string(bytes) + std::string("B");
}

std::tuple<FuncPtrLE, LLVMBuilderRef> makeStringSetupFunction(GlobalState* globalState);
Prototype* makeValeMainFunction(
    GlobalState* globalState,
    FuncPtrLE stringSetupFunctionL,
    Prototype* mainSetupFuncProto,
    Prototype* userMainFunctionPrototype,
    Prototype* mainCleanupFunctionPrototype);
LLVMValueRef makeEntryFunction(
    GlobalState* globalState,
    Prototype* valeMainPrototype);
//LLVMValueRef makeCoroutineEntryFunc(GlobalState* globalState);

FuncPtrLE declareFunction(
  GlobalState* globalState,
  Function* functionM);


std::string makeIncludeDirectory(GlobalState* globalState);
std::string makeModuleIncludeDirectory(const GlobalState *globalState, PackageCoordinate *packageCoord);
std::string makeModuleAbiDirectory(const GlobalState *globalState, PackageCoordinate *packageCoord);

std::ofstream makeCFile(const std::string &filepath);

void makeExternOrExportFunction(
    GlobalState *globalState,
    std::stringstream* headerC,
    std::stringstream* sourceC,
    const PackageCoordinate *packageCoord, Package *package,
    const std::string &externName,
    Prototype *prototype,
    bool isExport);

void initInternalExterns(GlobalState* globalState) {
//  auto voidLT = LLVMVoidTypeInContext(globalState->context);
  auto int1LT = LLVMInt1TypeInContext(globalState->context);
  auto int8LT = LLVMInt8TypeInContext(globalState->context);
  auto int32LT = LLVMInt32TypeInContext(globalState->context);
  auto int32PtrLT = LLVMPointerType(int32LT, 0);
  auto int64LT = LLVMInt64TypeInContext(globalState->context);
  auto voidPtrLT = LLVMPointerType(int8LT, 0);
  auto int8PtrLT = LLVMPointerType(int8LT, 0);

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

enum class CFuncLineMode {
  EXTERN_INTERMEDIATE_PROTOTYPE,
  EXPORT_INTERMEDIATE_PROTOTYPE,
  EXTERN_USER_PROTOTYPE,
  EXPORT_USER_PROTOTYPE,
  EXTERN_INTERMEDIATE_BODY,
  EXPORT_INTERMEDIATE_BODY,
};

// Returns the header C code and the source C code
std::string generateFunctionC(
    GlobalState* globalState,
    Package* package,
    const std::string& outsideName,
    Prototype* prototype,
    CFuncLineMode lineMode,
    bool isExport) {
  auto returnTypeExportName =
      globalState->getRegion(prototype->returnType)->getExportName(package, prototype->returnType, true);
  auto projectName = package->packageCoordinate->projectName;
  std::string userFuncName =
      (!package->packageCoordinate->projectName.empty() ? package->packageCoordinate->projectName + "_" : "") +
      outsideName;
  std::string abiFuncName = std::string("vale_abi_") + userFuncName;

  bool abiUsingRetOutParam = typeNeedsPointerParameter(globalState, prototype->returnType);

  std::stringstream s;
  switch (lineMode) {
    case CFuncLineMode::EXTERN_INTERMEDIATE_PROTOTYPE:
    case CFuncLineMode::EXPORT_USER_PROTOTYPE: {
      s << "extern " << (abiUsingRetOutParam ? "void " : returnTypeExportName) << " " << abiFuncName << "(";
      break;
    }
    case CFuncLineMode::EXTERN_USER_PROTOTYPE:
    case CFuncLineMode::EXPORT_INTERMEDIATE_PROTOTYPE: {
      s << "extern " << returnTypeExportName << " " << userFuncName << "(";
      break;
    }
    case CFuncLineMode::EXTERN_INTERMEDIATE_BODY: {
      if (translatesToCVoid(globalState, prototype->returnType)) {
        s << userFuncName << "(";
      } else {
        if (abiUsingRetOutParam) {
          // User function will return it directly, but we need to stuff it into the out-ptr for the boundary
          s << "*__ret = " << userFuncName << "(";
        } else {
          // It's something like an int, just return it directly
          s << "return " << userFuncName << "(";
        }
      }
      break;
    }
    case CFuncLineMode::EXPORT_INTERMEDIATE_BODY: {
      if (translatesToCVoid(globalState, prototype->returnType)) {
        s << abiFuncName << "(";
      } else {
        if (abiUsingRetOutParam) {
          s << returnTypeExportName << " __ret = { 0 };" << std::endl;
          s << abiFuncName << "(";
        } else {
          // It's something like an int, just return it directly
          s << "return " << abiFuncName << "(";
        }
      }
      break;
    }
  }

  bool addedAnyParam = false;

  switch (lineMode) {
    case CFuncLineMode::EXPORT_USER_PROTOTYPE:
    case CFuncLineMode::EXTERN_INTERMEDIATE_PROTOTYPE: {
      if (abiUsingRetOutParam) {
        s << returnTypeExportName << "* __ret";
        addedAnyParam = true;
      }
      break;
    }
    case CFuncLineMode::EXPORT_INTERMEDIATE_PROTOTYPE:
    case CFuncLineMode::EXTERN_USER_PROTOTYPE:
      // Do nothing, user header has no return parameter nonsense
      break;
    case CFuncLineMode::EXTERN_INTERMEDIATE_BODY:
      // Do nothing, we're calling the user function now, and it has no return parameter
      break;
    case CFuncLineMode::EXPORT_INTERMEDIATE_BODY:
      if (abiUsingRetOutParam) {
        s << "&__ret";
        addedAnyParam = true;
      }
      break;
  }

  for (int i = 0; i < prototype->params.size(); i++) {
    if (addedAnyParam) {
      s << ", ";
    }
    auto paramTypeExportName =
        globalState->getRegion(prototype->params[i])->getExportName(package, prototype->params[i], true);
    auto abiUsesPointer = typeNeedsPointerParameter(globalState, prototype->params[i]);
    switch (lineMode) {
      case CFuncLineMode::EXTERN_INTERMEDIATE_PROTOTYPE:
      case CFuncLineMode::EXPORT_USER_PROTOTYPE:
        s << paramTypeExportName << (abiUsesPointer ? "*" : "") << " param" << i;
        break;
      case CFuncLineMode::EXTERN_USER_PROTOTYPE:
      case CFuncLineMode::EXPORT_INTERMEDIATE_PROTOTYPE:
        s << paramTypeExportName << " param" << i;
        break;
      case CFuncLineMode::EXTERN_INTERMEDIATE_BODY:
        s << (abiUsesPointer ? "*" : "") << " param" << i;
        break;
      case CFuncLineMode::EXPORT_INTERMEDIATE_BODY:
        s << (abiUsesPointer ? "&" : "") << " param" << i;
        break;
    }
    addedAnyParam = true;
  }
  for (int i = 0; i < prototype->params.size(); i++) {
    if (includeSizeParam(globalState, prototype, i)) {
      if (addedAnyParam) {
        s << ", ";
      }
      switch (lineMode) {
        case CFuncLineMode::EXTERN_INTERMEDIATE_PROTOTYPE:
        case CFuncLineMode::EXTERN_USER_PROTOTYPE:
        case CFuncLineMode::EXPORT_INTERMEDIATE_PROTOTYPE:
        case CFuncLineMode::EXPORT_USER_PROTOTYPE:
          s << "ValeInt param" << i << "size";
          break;
        case CFuncLineMode::EXTERN_INTERMEDIATE_BODY:
        case CFuncLineMode::EXPORT_INTERMEDIATE_BODY:
          s << "param" << i << "size";
          break;
      }
      addedAnyParam = true;
    }
  }
  s << ")";

  switch (lineMode) {
    case CFuncLineMode::EXPORT_USER_PROTOTYPE:
    case CFuncLineMode::EXTERN_INTERMEDIATE_PROTOTYPE:
    case CFuncLineMode::EXPORT_INTERMEDIATE_PROTOTYPE:
    case CFuncLineMode::EXTERN_USER_PROTOTYPE:
    case CFuncLineMode::EXTERN_INTERMEDIATE_BODY:
      // Do nothing, need no extra statements
      break;
    case CFuncLineMode::EXPORT_INTERMEDIATE_BODY:
      if (abiUsingRetOutParam) {
        s << ";  return __ret;" << std::endl;
      }
      break;
  }
  return s.str();
}

void generateExports(GlobalState* globalState, Prototype* mainM) {
  auto program = globalState->program;
  auto packageCoordToHeaderNameToC =
      std::unordered_map<
          PackageCoordinate*,
          std::unordered_map<std::string, std::stringstream>,
          AddressHasher<PackageCoordinate*>>(
      0, globalState->addressNumberer->makeHasher<PackageCoordinate*>());
  auto packageCoordToSourceNameToC =
      std::unordered_map<
          PackageCoordinate*,
          std::unordered_map<std::string, std::stringstream>,
          AddressHasher<PackageCoordinate*>>(
          0, globalState->addressNumberer->makeHasher<PackageCoordinate*>());

  for (auto[packageCoord, package] : program->packages) {
    for (auto[exportName, kind] : package->exportNameToKind) {
      auto& resultC = packageCoordToHeaderNameToC[packageCoord].emplace(exportName, std::stringstream()).first->second;

      if (auto structMT = dynamic_cast<StructKind*>(kind)) {
        auto structDefM = program->getStruct(structMT);
        if (structDefM->mutability == Mutability::IMMUTABLE) {
          for (auto member : structDefM->members) {
            auto kind = member->type->kind;
            if (dynamic_cast<Int *>(kind) ||
                dynamic_cast<Bool *>(kind) ||
                dynamic_cast<Float *>(kind) ||
                dynamic_cast<Str *>(kind)) {
              // Do nothing, no need to include anything for these
            } else {
              auto paramTypeExportName = package->getKindExportName(kind, true);
              if (ownershipToMutability(member->type->ownership) == Mutability::MUTABLE) {
                paramTypeExportName += "Ref";
              }
              resultC << "typedef struct " << paramTypeExportName << " " << paramTypeExportName << ";" << std::endl;
            }
          }
        }

        // can we think of this in terms of regions? it's kind of like we're
        // generating some stuff for the outside to point inside.
        auto region = (structDefM->mutability == Mutability::IMMUTABLE ? globalState->linearRegion : globalState->mutRegion);
        auto defString = region->generateStructDefsC(package, structDefM);
        resultC << defString;
      } else if (auto interfaceMT = dynamic_cast<InterfaceKind*>(kind)) {
        auto interfaceDefM = globalState->program->getInterface(interfaceMT);
        auto region = (interfaceDefM->mutability == Mutability::IMMUTABLE ? globalState->linearRegion : globalState->mutRegion);
        auto defString = region->generateInterfaceDefsC(package, interfaceDefM);
        resultC << defString;
      } else if (auto ssaMT = dynamic_cast<StaticSizedArrayT*>(kind)) {
        auto ssaDefM = globalState->program->getStaticSizedArray(ssaMT);

        if (ssaDefM->mutability == Mutability::IMMUTABLE) {
          auto kind = ssaDefM->elementType->kind;
          if (dynamic_cast<Int *>(kind) ||
              dynamic_cast<Bool *>(kind) ||
              dynamic_cast<Float *>(kind) ||
              dynamic_cast<Str *>(kind)) {
            // Do nothing, no need to include anything for these
          } else {
            auto paramTypeExportName = package->getKindExportName(kind, true);
            if (ownershipToMutability(ssaDefM->elementType->ownership) == Mutability::MUTABLE) {
              paramTypeExportName += "Ref";
            }
            resultC << "typedef struct " << paramTypeExportName << " " << paramTypeExportName << ";" << std::endl;
          }
        }

        // can we think of this in terms of regions? it's kind of like we're
        // generating some stuff for the outside to point inside.
        auto region = (ssaDefM->mutability == Mutability::IMMUTABLE ? globalState->linearRegion : globalState->mutRegion);
        auto defString = region->generateStaticSizedArrayDefsC(package, ssaDefM);
        resultC << defString;
      } else if (auto rsaMT = dynamic_cast<RuntimeSizedArrayT*>(kind)) {
        auto rsaDefM = globalState->program->getRuntimeSizedArray(rsaMT);

        if (rsaDefM->mutability == Mutability::IMMUTABLE) {
          auto kind = rsaDefM->elementType->kind;
          if (dynamic_cast<Int *>(kind) ||
              dynamic_cast<Bool *>(kind) ||
              dynamic_cast<Float *>(kind) ||
              dynamic_cast<Str *>(kind)) {
            // Do nothing, no need to include anything for these
          } else {
            auto paramTypeExportName = package->getKindExportName(kind, true);
            if (ownershipToMutability(rsaDefM->elementType->ownership) == Mutability::MUTABLE) {
              paramTypeExportName += "Ref";
            }
            resultC << "typedef struct " << paramTypeExportName << " " << paramTypeExportName << ";" << std::endl;
          }
        }

        // can we think of this in terms of regions? it's kind of like we're
        // generating some stuff for the outside to point inside.
        auto region = (rsaDefM->mutability == Mutability::IMMUTABLE ? globalState->linearRegion : globalState->mutRegion);
        auto defString = region->generateRuntimeSizedArrayDefsC(package, rsaDefM);
        resultC << defString;
      } else {
        std::cerr << "Unknown exportee: " << typeid(*kind).name() << std::endl;
        assert(false);
        exit(1);
      }
    }
  }
  for (auto[packageCoord, package] : program->packages) {
    for (auto[exportName, prototype] : package->exportNameToFunction) {
      bool skipExporting = exportName == "main";
      if (skipExporting)
        continue;

      auto* headerC = &packageCoordToHeaderNameToC[packageCoord].emplace(exportName, std::stringstream()).first->second;
      auto* sourceC = &packageCoordToSourceNameToC[packageCoord].emplace(exportName, std::stringstream()).first->second;
      makeExternOrExportFunction(globalState, headerC, sourceC, packageCoord, package, exportName, prototype, true);
    }
  }
  for (auto[packageCoord, package] : program->packages) {
    for (auto[externName, prototype] : package->externNameToFunction) {
      if (prototype->name->name.rfind("__vbi_", 0) == 0) {
        // Dont generate C code for built in externs
        continue;
      }
      auto* headerC = &packageCoordToHeaderNameToC[packageCoord].emplace(externName, std::stringstream()).first->second;
      auto* sourceC = &packageCoordToSourceNameToC[packageCoord].emplace(externName, std::stringstream()).first->second;
      makeExternOrExportFunction(globalState, headerC, sourceC, packageCoord, package, externName, prototype, false);
    }
  }
  if (globalState->opt->outputDir.empty()) {
    std::cerr << "Must specify --output-dir!" << std::endl;
    assert(false);
  }
  auto outputDir = globalState->opt->outputDir;


  for (auto& [packageCoord, headerNameToC] : packageCoordToHeaderNameToC) {
    for (auto& [headerName, headerCode] : headerNameToC) {
      std::string moduleIncludeDirectory = makeModuleIncludeDirectory(globalState, packageCoord);

      std::string filepath = moduleIncludeDirectory + "/" + headerName + ".h";
      std::ofstream out = makeCFile(filepath);
       //std::cout << "Writing " << filepath << std::endl;

      out << "#ifndef VALE_EXPORTS_" << headerName << "_H_" << std::endl;
      out << "#define VALE_EXPORTS_" << headerName << "_H_" << std::endl;
      out << "#include \"ValeBuiltins.h\"" << std::endl;
      out << headerCode.str();
      out << "#endif" << std::endl;
    }
  }
  for (auto& [packageCoord, sourceNameToC] : packageCoordToSourceNameToC) {
    for (auto& [sourceName, sourceCode] : sourceNameToC) {
      std::string moduleAbiDirectory = makeModuleAbiDirectory(globalState, packageCoord);

      std::string filepath = moduleAbiDirectory + "/" + sourceName + ".c";
      std::ofstream out = makeCFile(filepath);
      //std::cout << "Writing " << filepath << ", including " << packageCoord->projectName << "/" << sourceName << ".h " << std::endl;

      out << "#include \"" << packageCoord->projectName << "/" << sourceName << ".h\"" << std::endl;
      out << sourceCode.str();
    }
  }

  std::stringstream builtinExportsCode;
  builtinExportsCode << "#ifndef VALE_BUILTINS_H_" << std::endl;
  builtinExportsCode << "#define VALE_BUILTINS_H_" << std::endl;
  builtinExportsCode << "#include <stdint.h>" << std::endl;
  builtinExportsCode << "#include <stdlib.h>" << std::endl;
  builtinExportsCode << "#include <string.h>" << std::endl;
  builtinExportsCode << "typedef int32_t ValeInt;" << std::endl;
  builtinExportsCode << "typedef struct { ValeInt length; char chars[0]; } ValeStr;" << std::endl;
  builtinExportsCode << "ValeStr* ValeStrNew(ValeInt length);" << std::endl;
  builtinExportsCode << "ValeStr* ValeStrFrom(char* source);" << std::endl;
  builtinExportsCode << "#endif" << std::endl;

  std::string builtinsFilePath = makeIncludeDirectory(globalState) + "/ValeBuiltins.h";
  //std::cout << "Writing " << builtinsFilePath << std::endl;
  std::ofstream out = makeCFile(builtinsFilePath);
  out << builtinExportsCode.str();
}

void makeExternOrExportFunction(
    GlobalState *globalState,
    std::stringstream* headerC,
    std::stringstream* sourceC,
    const PackageCoordinate *packageCoord,
    Package *package,
    const std::string &externName,
    Prototype *prototype,
    bool isExport) {
  for (auto param : prototype->params) {
    auto kind = param->kind;
    if (translatesToCVoid(globalState, param) ||
        dynamic_cast<Int *>(kind) ||
        dynamic_cast<Bool *>(kind) ||
        dynamic_cast<Float *>(kind) ||
        dynamic_cast<Str *>(kind)) {
      // Do nothing, no need to include anything for these
    } else {
      auto paramTypeExportName = package->getKindExportName(kind, false);
//      if (ownershipToMutability(param->ownership) == Mutability::MUTABLE) {
//        paramTypeExportName += "Ref";
//      }
      (*headerC) << "#include \"" << packageCoord->projectName << "/" << paramTypeExportName << ".h\"" << std::endl;
    }
  }
  {
    auto kind = prototype->returnType->kind;
    if (translatesToCVoid(globalState, prototype->returnType) ||
        dynamic_cast<Int *>(kind) ||
        dynamic_cast<Bool *>(kind) ||
        dynamic_cast<Float *>(kind) ||
        dynamic_cast<Str *>(kind)) {
      // Do nothing, no need to include anything for these
    } else {
      // We need to include the actual header for interfaces, because the user func hands them around by value
      auto paramTypeExportName = package->getKindExportName(kind, false);
//      if (ownershipToMutability(prototype->returnType->ownership) == Mutability::MUTABLE) {
//        paramTypeExportName += "Ref";
//      }
      (*headerC) << "#include \"" << packageCoord->projectName << "/" << paramTypeExportName << ".h\"" << std::endl;
    }
  }
  auto userHeaderC = generateFunctionC(globalState, package, externName, prototype, isExport ? CFuncLineMode::EXPORT_USER_PROTOTYPE : CFuncLineMode::EXTERN_USER_PROTOTYPE, isExport);
  auto abiHeaderC = generateFunctionC(globalState, package, externName, prototype, isExport ? CFuncLineMode::EXPORT_INTERMEDIATE_PROTOTYPE : CFuncLineMode::EXTERN_INTERMEDIATE_PROTOTYPE, isExport);
  (*headerC) << userHeaderC << ";" << std::endl;
  (*headerC) << abiHeaderC << ";" << std::endl;

  auto userSourceC = std::stringstream{};
  userSourceC << "#include \"" << packageCoord->projectName << "/" << externName << ".h\"" << std::endl;
  userSourceC << generateFunctionC(globalState, package, externName, prototype, isExport ? CFuncLineMode::EXPORT_INTERMEDIATE_PROTOTYPE : CFuncLineMode::EXTERN_INTERMEDIATE_PROTOTYPE, isExport) << " {" << std::endl;
  userSourceC << "  " << generateFunctionC(globalState, package, externName, prototype, isExport ? CFuncLineMode::EXPORT_INTERMEDIATE_BODY : CFuncLineMode::EXTERN_INTERMEDIATE_BODY, isExport) << ";" << std::endl;
  userSourceC << "}" << std::endl;
  (*sourceC) << userSourceC.str();
}

std::ofstream makeCFile(const std::string &filepath) {
  std::ofstream out(filepath, std::ofstream::out);
  if (!out) {
    std::cerr << "Couldn't make file '" << filepath << std::endl;
    exit(1);
  }
  return out;
}

void makeDirectory(const std::string& dir, bool reuse) {
  try {
    if (std::filesystem::is_directory(std::filesystem::path(dir))) {
      if (reuse) {
        // Do nothing, just re-use it
      } else {
        std::cerr << "Couldn't make directory: " << dir << ", already exists!" << std::endl;
        exit(1);
      }
    } else {
      bool success = std::filesystem::create_directory(std::filesystem::path(dir));
      if (!success) {
        std::cerr << "Couldn't make directory: " << dir << " (unknown error)" << std::endl;
        exit(1);
      }
    }
  }
  catch (const std::filesystem::filesystem_error& err) {
    std::cerr << "Couldn't make directory: " << dir << " (" << err.what() << ")" << std::endl;
    exit(1);
  }
}

std::string makeIncludeDirectory(GlobalState* globalState) {
  return globalState->opt->outputDir + "/include";
}

std::string makeModuleIncludeDirectory(const GlobalState *globalState, PackageCoordinate *packageCoord) {
  std::string moduleExternsDirectory = globalState->opt->outputDir;
  auto includeDirectory = moduleExternsDirectory + "/include";
  makeDirectory(includeDirectory, true);
  auto moduleIncludeDirectory = includeDirectory + "/" + packageCoord->projectName;
  makeDirectory(moduleIncludeDirectory, true);
  return moduleIncludeDirectory;
}

std::string makeModuleAbiDirectory(const GlobalState *globalState, PackageCoordinate *packageCoord) {
  std::string moduleExternsDirectory = globalState->opt->outputDir;
  auto includeDirectory = moduleExternsDirectory + "/abi";
  makeDirectory(includeDirectory, true);
  auto moduleIncludeDirectory = includeDirectory + "/" + packageCoord->projectName;
  makeDirectory(moduleIncludeDirectory, true);
  return moduleIncludeDirectory;
}

void compileValeCode(GlobalState* globalState, std::vector<std::string>& inputFilepaths) {
  auto voidLT = LLVMVoidTypeInContext(globalState->context);
  auto int8LT = LLVMInt8TypeInContext(globalState->context);
  auto int64LT = LLVMInt64TypeInContext(globalState->context);
  auto int256LT = LLVMIntTypeInContext(globalState->context, 256);
  auto int32LT = LLVMInt32TypeInContext(globalState->context);
  auto int32PtrLT = LLVMPointerType(int32LT, 0);
  auto int8PtrLT = LLVMPointerType(int8LT, 0);

  {
    globalState->universalRefStructLT =
        std::make_unique<UniversalRefStructLT>(globalState->context, globalState->dataLayout);
    globalState->universalRefCompressedStructLT =
        LLVMStructCreateNamed(globalState->context, "__UniversalRefCompressed");
    LLVMStructSetBody(globalState->universalRefCompressedStructLT, &int256LT, 1, false);
  }

  switch (globalState->opt->regionOverride) {
//    case RegionOverride::ASSIST:
//      std::cout << "Region override: assist" << std::endl;
//      break;
    case RegionOverride::NAIVE_RC:
      std::cout << "Region override: naive-rc" << std::endl;
      break;
    case RegionOverride::FAST:
      std::cout << "Region override: fast" << std::endl;
      break;
    case RegionOverride::RESILIENT_V3:
      std::cout << "Region override: resilient-v3" << std::endl;
      break;
//    case RegionOverride::RESILIENT_V4:
//      std::cout << "Region override: resilient-v4" << std::endl;
//      break;
    default:
      assert(false);
      break;
  }

//  if (globalState->opt->regionOverride == RegionOverride::ASSIST) {
//    if (!globalState->opt->census) {
//      std::cout << "Warning: not using census when in assist mode!" << std::endl;
//    }
//    if (globalState->opt->fastCrash) {
//      std::cout << "Warning: using fastCrash when in assist mode!" << std::endl;
//    }
//  } else {
    if (globalState->opt->flares) {
      std::cout << "Warning: using flares outside of assist mode, will slow things down!" << std::endl;
    }
    if (globalState->opt->census) {
      std::cout << "Warning: using census outside of assist mode, will slow things down!" << std::endl;
    }
    if (!globalState->opt->fastCrash) {
      std::cout << "Warning: not using fastCrash, will slow things down!" << std::endl;
    }
//  }



  AddressNumberer addressNumberer;
  MetalCache metalCache(&addressNumberer);
  globalState->metalCache = &metalCache;

  Program program(
      std::unordered_map<PackageCoordinate*, Package*, AddressHasher<PackageCoordinate*>, std::equal_to<PackageCoordinate*>>(
          0,
          addressNumberer.makeHasher<PackageCoordinate*>(),
          std::equal_to<PackageCoordinate*>()));
  for (auto inputFilepath : inputFilepaths) {
    //std::cout << "Reading input file: " << inputFilepath << std::endl;
    auto stem = std::filesystem::path(inputFilepath).stem();
    auto package_coord_parts = split(stem.string(), '.');
    auto project_name = package_coord_parts[0];
    package_coord_parts.erase(package_coord_parts.begin());
    auto package_steps = package_coord_parts;

    auto package_coord = metalCache.getPackageCoordinate(project_name, package_steps);

    try {
      std::ifstream instream(inputFilepath);
      std::string str(std::istreambuf_iterator<char>{instream}, {});
      if (str.size() == 0) {
        std::cerr << "Nothing found in " << inputFilepath << std::endl;
        exit(1);
      }
      auto packageJ = json::parse(str.c_str());
      auto packageM = readPackage(&metalCache, packageJ);

      program.packages.emplace(package_coord, packageM);
    }
    catch (const nlohmann::detail::parse_error &error) {
      std::cerr << "Error while parsing json: " << error.what() << std::endl;
      exit(1);
    }
  }

  FuncPtrLE stringSetupFunctionL;
  LLVMBuilderRef stringConstantBuilder = nullptr;
  std::tie(stringSetupFunctionL, stringConstantBuilder) = makeStringSetupFunction(globalState);
  globalState->stringConstantBuilder = stringConstantBuilder;


  globalState->program = &program;

  globalState->serializeName = globalState->metalCache->getName(globalState->metalCache->builtinPackageCoord, "__vale_serialize");
  globalState->serializeThunkName = globalState->metalCache->getName(globalState->metalCache->builtinPackageCoord, "__vale_serialize_thunk");
  globalState->unserializeName = globalState->metalCache->getName(globalState->metalCache->builtinPackageCoord, "__vale_unserialize");
  globalState->unserializeThunkName = globalState->metalCache->getName(globalState->metalCache->builtinPackageCoord, "__vale_unserialize_thunk");
  globalState->freeName = globalState->metalCache->getName(globalState->metalCache->builtinPackageCoord, "__vale_free");
  globalState->freeThunkName = globalState->metalCache->getName(globalState->metalCache->builtinPackageCoord, "__vale_free_thunk");

  Externs externs(globalState->mod, globalState->context);
  globalState->externs = &externs;

//  globalState->stringConstantBuilder = entryBuilder;

  LLVMValueRef empty[1] = {};

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
  LLVMSetInitializer(globalState->neverPtr, LLVMConstArray(LLVMIntTypeInContext(globalState->context, NEVER_INT_BITS), empty, 0));

  globalState->sideStack = LLVMAddGlobal(globalState->mod, LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0), "__sideStack");
  LLVMSetInitializer(globalState->sideStack, LLVMConstNull(LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0)));

//  globalState->sideStackArgCalleeFuncPtrPtr = LLVMAddGlobal(globalState->mod, LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0), "sideStackArgCalleeFuncPtrPtr");
//  LLVMSetInitializer(globalState->sideStackArgCalleeFuncPtrPtr, LLVMConstNull(LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0)));

//  globalState->sideStackArgReturnDestPtr = LLVMAddGlobal(globalState->mod, LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0), "sideStackArgReturnDestPtr");
//  LLVMSetInitializer(globalState->sideStackArgReturnDestPtr, LLVMConstNull(LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0)));

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

  globalState->linearRegion = new Linear(globalState);
  globalState->regions.emplace(globalState->linearRegion->getRegionId(), globalState->linearRegion);


  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC:
      globalState->mutRegion = new NaiveRC(globalState, globalState->metalCache->mutRegionId);
      break;
    case RegionOverride::FAST:
      globalState->mutRegion = new Unsafe(globalState);
      break;
    case RegionOverride::RESILIENT_V3:
      globalState->mutRegion = new ResilientV3(globalState, globalState->metalCache->mutRegionId);
      break;
    default:
      assert(false);
      break;
  }
  globalState->regions.emplace(globalState->mutRegion->getRegionId(), globalState->mutRegion);

  Determinism determinism(globalState);
  globalState->determinism = &determinism;


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

  for (auto packageCoordAndPackage : program.packages) {
    auto[packageCoord, package] = packageCoordAndPackage;
    for (auto p : package->structs) {
      auto name = p.first;
      auto structM = p.second;
      auto region = globalState->getRegion(structM->regionId);
      region->declareStruct(structM);

      // std::cout << "Declaring struct " << packageCoord->projectName;
      // for (auto step : packageCoord->packageSteps) {
      //   std::cout << "." << step;
      // }
      // std::cout << "." << name;
      // std::cout << std::endl;

      if (structM->mutability == Mutability::IMMUTABLE) {
        // TODO: https://github.com/ValeLang/Vale/issues/479
        globalState->linearRegion->declareStruct(structM);
      }
    }
  }

  for (auto packageCoordAndPackage : program.packages) {
    auto[packageCoord, package] = packageCoordAndPackage;
    for (auto p : package->interfaces) {
      auto name = p.first;
      auto interfaceM = p.second;
      globalState->getRegion(interfaceM->regionId)->declareInterface(interfaceM);
      if (interfaceM->mutability == Mutability::IMMUTABLE) {
        // TODO: https://github.com/ValeLang/Vale/issues/479
        globalState->linearRegion->declareInterface(interfaceM);
      }
    }
  }

  for (auto packageCoordAndPackage : program.packages) {
    auto[packageCoord, package] = packageCoordAndPackage;
    for (auto p : package->staticSizedArrays) {
      auto name = p.first;
      auto arrayM = p.second;
      globalState->getRegion(arrayM->regionId)->declareStaticSizedArray(arrayM);
      if (arrayM->mutability == Mutability::IMMUTABLE) {
        globalState->linearRegion->declareStaticSizedArray(arrayM);
      }
    }
  }

  for (auto packageCoordAndPackage : program.packages) {
    auto[packageCoord, package] = packageCoordAndPackage;
    for (auto p : package->runtimeSizedArrays) {
      auto name = p.first;
      auto arrayM = p.second;
      globalState->getRegion(arrayM->regionId)->declareRuntimeSizedArray(arrayM);
      if (arrayM->mutability == Mutability::IMMUTABLE) {
        globalState->linearRegion->declareRuntimeSizedArray(arrayM);
      }
    }
  }

  for (auto packageCoordAndPackage : program.packages) {
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

  for (auto[packageCoord, package] : program.packages) {
    for (auto p : package->interfaces) {
      auto name = p.first;
      auto interfaceM = p.second;
      globalState->getRegion(interfaceM->regionId)->declareInterfaceExtraFunctions(interfaceM);
      if (interfaceM->mutability == Mutability::IMMUTABLE) {
        globalState->linearRegion->declareInterfaceExtraFunctions(interfaceM);
      }
    }
  }

  for (auto[packageCoord, package] : program.packages) {
    for (auto p : package->staticSizedArrays) {
      auto name = p.first;
      auto arrayM = p.second;
      globalState->getRegion(arrayM->regionId)->declareStaticSizedArrayExtraFunctions(arrayM);
      if (arrayM->mutability == Mutability::IMMUTABLE) {
        globalState->linearRegion->declareStaticSizedArrayExtraFunctions(arrayM);
      }
    }
  }

  for (auto[packageCoord, package] : program.packages) {
    for (auto p : package->runtimeSizedArrays) {
      auto name = p.first;
      auto arrayM = p.second;
      globalState->getRegion(arrayM->regionId)->declareRuntimeSizedArrayExtraFunctions(arrayM);
      if (arrayM->mutability == Mutability::IMMUTABLE) {
        globalState->linearRegion->declareRuntimeSizedArrayExtraFunctions(arrayM);
      }
    }
  }

  // This is here before any defines because:
  // 1. It has to be before we define any extra functions for structs etc because the linear
  //    region's extra functions need to know all the substructs for interfaces so it can number
  //    them, which is used in supporting its interface calling.
  // 2. Everything else is declared here too and it seems consistent
  for (auto[packageCoord, package] : program.packages) {
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

  for (auto[packageCoord, package] : program.packages) {
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
  for (auto[packageCoord, package] : program.packages) {
    for (auto p : package->interfaces) {
      auto name = p.first;
      auto interfaceM = p.second;
      globalState->getRegion(interfaceM->regionId)->defineInterface(interfaceM);
      if (interfaceM->mutability == Mutability::IMMUTABLE) {
        globalState->linearRegion->defineInterface(interfaceM);
      }
    }
  }

  for (auto[packageCoord, package] : program.packages) {
    for (auto p : package->staticSizedArrays) {
      auto name = p.first;
      auto arrayM = p.second;
      globalState->getRegion(arrayM->regionId)->defineStaticSizedArray(arrayM);
      if (arrayM->mutability == Mutability::IMMUTABLE) {
        globalState->linearRegion->defineStaticSizedArray(arrayM);
      }
    }
  }

  for (auto[packageCoord, package] : program.packages) {
    for (auto p : package->runtimeSizedArrays) {
      auto name = p.first;
      auto arrayM = p.second;
      globalState->getRegion(arrayM->regionId)->defineRuntimeSizedArray(arrayM);
      if (arrayM->mutability == Mutability::IMMUTABLE) {
        globalState->linearRegion->defineRuntimeSizedArray(arrayM);
      }
    }
  }

  // add functions for all the known structs and interfaces.
  // It also has to be after we *define* them, because they want to access members.
  // But it has to be before we translate interfaces, because thats when we manifest
  // the itable layouts.
  for (auto region : globalState->regions) {
    region.second->declareExtraFunctions();
  }

  for (auto[packageCoord, package] : program.packages) {
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

  for (auto[packageCoord, package] : program.packages) {
    for (auto p : package->staticSizedArrays) {
      auto name = p.first;
      auto arrayM = p.second;
      globalState->getRegion(arrayM->regionId)->defineStaticSizedArrayExtraFunctions(arrayM);
      if (arrayM->mutability == Mutability::IMMUTABLE) {
        globalState->linearRegion->defineStaticSizedArrayExtraFunctions(arrayM);
      }
    }
  }

  for (auto[packageCoord, package] : program.packages) {
    for (auto p : package->runtimeSizedArrays) {
      auto name = p.first;
      auto arrayM = p.second;
      globalState->getRegion(arrayM->regionId)->defineRuntimeSizedArrayExtraFunctions(arrayM);
      if (arrayM->mutability == Mutability::IMMUTABLE) {
        globalState->linearRegion->defineRuntimeSizedArrayExtraFunctions(arrayM);
      }
    }
  }

  // This keeps us from accidentally adding interfaces and interface methods after we've
  // started compiling interfaces.
  globalState->interfacesOpen = false;

  for (auto[packageCoord, package] : program.packages) {
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

  for (auto[packageCoord, package] : program.packages) {
    for (auto[externName, prototype] : package->externNameToFunction) {
      if (prototype->name->name.rfind("__vbi_", 0) == 0) {
        // Dont generate C code for built in externs
        continue;
      }
      declareExternFunction(globalState, package, prototype);
      determinism.registerFunction(prototype);
    }
  }

  for (auto[packageCoord, package] : program.packages) {
    for (auto[name, function] : package->functions) {
      declareFunction(globalState, function);
    }
  }

  for (auto[packageCoord, package] : program.packages) {
    for (auto[exportName, prototype] : package->exportNameToFunction) {
      bool skipExporting = exportName == "main";
      if (!skipExporting) {
        auto function = program.getFunction(prototype->name);
        exportFunction(globalState, package, function);
        determinism.registerFunction(prototype);
      }
    }
  }

  determinism.finalizeFunctionsMap();

  for (auto[packageCoord, package] : program.packages) {
    for (auto p : package->functions) {
      auto name = p.first;
      auto function = p.second;
      translateFunction(globalState, function);
    }
  }

  // We translate the edges after the functions are declared because the
  // functions have to exist for the itables to point to them.
  for (auto[packageCoord, package] : program.packages) {
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

//  globalState->coroutineEntryFunc = makeCoroutineEntryFunc(globalState);

  Prototype* mainM = nullptr;
  for (auto[packageCoord, package] : program.packages) {
    for (auto[exportName, prototype] : package->exportNameToFunction) {
      auto function = program.getFunction(prototype->name);
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

void createModule(std::vector<std::string>& inputFilepaths, GlobalState *globalState) {
  globalState->mod = LLVMModuleCreateWithNameInContext("build", globalState->context);
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
  compileValeCode(globalState, inputFilepaths);
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

  LLVMInitializeWebAssemblyTargetInfo();
  LLVMInitializeWebAssemblyTargetMC();
  LLVMInitializeWebAssemblyTarget();
  LLVMInitializeWebAssemblyAsmPrinter();
  LLVMInitializeWebAssemblyAsmParser();

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
void generateModule(std::vector<std::string>& inputFilepaths, GlobalState *globalState) {
  char *err;

  // Generate IR to LLVM IR
  createModule(inputFilepaths, globalState);

  // Serialize the LLVM IR, if requested
  if (globalState->opt->print_llvmir) {
    auto outputFilePath = fileMakePath(globalState->opt->outputDir.c_str(), "build", "ll");
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

  if (globalState->opt->release) {
    if (globalState->opt->flares) {
      std::cout << "Warning: Running release optimizations with flares enabled!" << std::endl;
    }
    std::cout << "Running release optimizations..." << std::endl;

    // Create the analysis managers.
    llvm::LoopAnalysisManager LAM;
    llvm::FunctionAnalysisManager FAM;
    llvm::CGSCCAnalysisManager CGAM;
    llvm::ModuleAnalysisManager MAM;

    // Create the new pass manager builder.
    // Take a look at the PassBuilder constructor parameters for more
    // customization, e.g. specifying a TargetMachine or various debugging
    // options.
    llvm::PassBuilder PB;

    // Register all the basic analyses with the managers.
    PB.registerModuleAnalyses(MAM);
    PB.registerCGSCCAnalyses(CGAM);
    PB.registerFunctionAnalyses(FAM);
    PB.registerLoopAnalyses(LAM);
    PB.crossRegisterProxies(LAM, FAM, CGAM, MAM);

    // Create the pass manager.
    // This one corresponds to a typical -O3 optimization pipeline.
    llvm::ModulePassManager MPM = PB.buildPerModuleDefaultPipeline(llvm::OptimizationLevel::O3);

    // Optimize the IR!
    MPM.run(*llvm::unwrap(globalState->mod), MAM);
  }
//
//  // Optimize the generated LLVM IR
//  LLVMPassManagerRef passmgr = LLVMCreatePassManager();
//
////  LLVMAddPromoteMemoryToRegisterPass(passmgr);     // Demote allocas to registers.
//////  LLVMAddInstructionCombiningPass(passmgr);        // Do simple "peephole" and bit-twiddling optimizations
////  LLVMAddReassociatePass(passmgr);                 // Reassociate expressions.
////  LLVMAddGVNPass(passmgr);                         // Eliminate common subexpressions.
////  LLVMAddCFGSimplificationPass(passmgr);           // Simplify the control flow graph
//
////  if (globalState->opt->release) {
////    LLVMAddFunctionInliningPass(passmgr);        // Function inlining
////  }
//
//  LLVMRunPassManager(passmgr, globalState->mod);
//  LLVMDisposePassManager(passmgr);

  // Serialize the LLVM IR, if requested
  if (globalState->opt->print_llvmir) {
    auto outputFilePath = fileMakePath(globalState->opt->outputDir.c_str(), "build", "opt.ll");
    std::cout << "Printing file " << outputFilePath << std::endl;
    if (LLVMPrintModuleToFile(globalState->mod, outputFilePath.c_str(), &err) != 0) {
      std::cerr << "Could not emit ir file: " << err << std::endl;
      LLVMDisposeMessage(err);
    }
  }

  // Transform IR to target's ASM and OBJ
  if (globalState->machine) {
    auto objpath =
        fileMakePath(globalState->opt->outputDir.c_str(), "build",
            globalState->opt->wasm ? "wasm" : objext);
    auto asmpath =
        fileMakePath(globalState->opt->outputDir.c_str(),
            "build",
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

  auto inputFilepaths = std::vector<std::string>{};
  for (int i = 1; i < argc; i++) {
    //std::cout << "Backend found file: " << argv[i] << std::endl;
    inputFilepaths.emplace_back(argv[i]);
  }

//  valeOptions.srcpath = argv[1];
//  valeOptions.srcDir = std::string(fileDirectory(valeOptions.srcpath));
//  valeOptions.srcNameNoExt = std::string(getFileNameNoExt(valeOptions.srcpath));
//  valeOptions.srcDirAndNameNoExt = std::string(valeOptions.srcDir + valeOptions.srcNameNoExt);

  // We set up generation early because we need target info, e.g.: pointer size
  AddressNumberer addressNumberer;
  GlobalState globalState(&addressNumberer);
  setup(&globalState, &valeOptions);

  // Parse source file, do semantic analysis, and generate code
//    ModuleNode *modnode = NULL;
//    if (!errors)
  generateModule(inputFilepaths, &globalState);

  closeGlobalState(&globalState);
//    errorSummary();
}
