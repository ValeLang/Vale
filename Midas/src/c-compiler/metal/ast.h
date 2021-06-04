#ifndef VALE_AST_H_
#define VALE_AST_H_

#include "name.h"
#include "types.h"
#include "addresshasher.h"

#include <llvm-c/Core.h>
#include <llvm-c/DebugInfo.h>
#include <llvm-c/ExecutionEngine.h>
#include <llvm-c/Analysis.h>

#include <stdio.h>
#include <assert.h>
#include <string>
#include <vector>
#include <algorithm>
#include <memory>
#include <unordered_map>
#include <iostream>

using std::move;

// Defined elsewhere
class Block;
class Expression;

// Defined in this file
class Program;
class StructDefinition;
class StructMember;
class InterfaceDefinition;
class Edge;
class Function;
class Prototype;
class Name;

class Package {
public:
  std::unordered_map<std::string, InterfaceDefinition*> interfaces;
  std::unordered_map<std::string, StructDefinition*> structs;
  std::unordered_map<std::string, StaticSizedArrayDefinitionT*> staticSizedArrays;
  std::unordered_map<std::string, RuntimeSizedArrayDefinitionT*> runtimeSizedArrays;
  // Get rid of this; since there's no IDs anymore we can have a stable
  // hardcoded NameH("__Pack", Some(List()), None, None).
  StructKind* emptyTupleStructRef;
//  std::unordered_map<std::string, Prototype*> externs;
  std::unordered_map<std::string, Function*> functions;
  std::unordered_map<Kind*, Prototype*, AddressHasher<Kind*>> immDestructorsByKind;

  // This only contains exports defined in this package. Though, the things they're exporting can
  // be defined somewhere else.
  std::unordered_map<std::string, Prototype*> exportNameToFunction;
  std::unordered_map<std::string, Kind*> exportNameToKind;
  std::unordered_map<std::string, Prototype*> externNameToFunction;
  std::unordered_map<std::string, Kind*> externNameToKind;
  // These are inverses of the above maps
  std::unordered_map<Prototype*, std::string, AddressHasher<Prototype*>> functionToExportName;
  std::unordered_map<Kind*, std::string, AddressHasher<Kind*>> kindToExportName;
  std::unordered_map<Prototype*, std::string, AddressHasher<Prototype*>> functionToExternName;
  std::unordered_map<Kind*, std::string, AddressHasher<Kind*>> kindToExternName;

  Package(
    AddressNumberer* addressNumberer,
    std::unordered_map<std::string, InterfaceDefinition*> interfaces_,
    std::unordered_map<std::string, StructDefinition*> structs_,
    std::unordered_map<std::string, StaticSizedArrayDefinitionT*> staticSizedArrays_,
    std::unordered_map<std::string, RuntimeSizedArrayDefinitionT*> runtimeSizedArrays_,
    StructKind* emptyTupleStructRef_,
//    std::unordered_map<std::string, Prototype*> externs_,
    std::unordered_map<std::string, Function*> functions_,
    std::unordered_map<Kind*, Prototype*, AddressHasher<Kind*>> immDestructorsByKind_,
    std::unordered_map<std::string, Prototype*> exportNameToFunction_,
    std::unordered_map<std::string, Kind*> exportNameToKind_,
    std::unordered_map<std::string, Prototype*> externNameToFunction_,
    std::unordered_map<std::string, Kind*> externNameToKind_) :
      interfaces(std::move(interfaces_)),
      structs(std::move(structs_)),
      staticSizedArrays(std::move(staticSizedArrays_)),
      runtimeSizedArrays(std::move(runtimeSizedArrays_)),
      emptyTupleStructRef(std::move(emptyTupleStructRef_)),
//      externs(std::move(externs_)),
      functions(std::move(functions_)),
      immDestructorsByKind(std::move(immDestructorsByKind_)),
      exportNameToFunction(std::move(exportNameToFunction_)),
      exportNameToKind(std::move(exportNameToKind_)),
      externNameToFunction(std::move(externNameToFunction_)),
      externNameToKind(std::move(externNameToKind_)),
      functionToExportName(0, addressNumberer->makeHasher<Prototype*>()),
      kindToExportName(0, addressNumberer->makeHasher<Kind*>()),
      functionToExternName(0, addressNumberer->makeHasher<Prototype*>()),
      kindToExternName(0, addressNumberer->makeHasher<Kind*>()) {
    for (auto [exportName, prototype] : exportNameToFunction) {
      assert(functionToExportName.count(prototype) == 0);
      functionToExportName[prototype] = exportName;
    }
    for (auto [exportName, kind] : exportNameToKind) {
      assert(kindToExportName.count(kind) == 0);
      kindToExportName[kind] = exportName;
    }
    for (auto [externName, prototype] : externNameToFunction) {
      assert(functionToExternName.count(prototype) == 0);
      functionToExternName[prototype] = externName;
    }
    for (auto [externName, kind] : externNameToKind) {
      assert(kindToExternName.count(kind) == 0);
      kindToExternName[kind] = externName;
    }
  }


  Function* getFunction(Name* name) {
    auto iter = functions.find(name->name);
    if (iter == functions.end()) {
      std::cerr << "Couldn't find struct: " << name->name << std::endl;
      exit(1);
    }
    return iter->second;
  }
  std::optional<Function*> getMaybeFunction(Name* name) {
    auto iter = functions.find(name->name);
    if (iter == functions.end()) {
      return std::nullopt;
    }
    return std::optional(iter->second);
  }
  StructDefinition* getStruct(StructKind* structMT) {
    auto iter = structs.find(structMT->fullName->name);
    if (iter == structs.end()) {
      std::cerr << "Couldn't find struct: " << structMT->fullName->name << std::endl;
      exit(1);
    }
    return iter->second;
  }
  std::optional<StructDefinition*> getMaybeStruct(Name* name) {
    auto iter = structs.find(name->name);
    if (iter == structs.end()) {
      return std::nullopt;
    }
    return std::optional(iter->second);
  }
  InterfaceDefinition* getInterface(InterfaceKind* interfaceMT) {
    auto iter = interfaces.find(interfaceMT->fullName->name);
    assert(iter != interfaces.end());
    return iter->second;
  }
  std::optional<InterfaceDefinition*> getMaybeInterface(InterfaceKind* interfaceMT) {
    auto iter = interfaces.find(interfaceMT->fullName->name);
    if (iter == interfaces.end()) {
      return std::nullopt;
    }
    return std::optional(iter->second);
  }
  StaticSizedArrayDefinitionT* getStaticSizedArray(StaticSizedArrayT* ssaMT) {
    auto iter = staticSizedArrays.find(ssaMT->name->name);
    assert(iter != staticSizedArrays.end());
    return iter->second;
  }
  std::optional<StaticSizedArrayDefinitionT*> getMaybeStaticSizedArray(Name* name) {
    auto iter = staticSizedArrays.find(name->name);
    if (iter == staticSizedArrays.end()) {
      return std::nullopt;
    }
    return std::optional(iter->second);
  }
  RuntimeSizedArrayDefinitionT* getRuntimeSizedArray(RuntimeSizedArrayT* rsaMT) {
    auto iter = runtimeSizedArrays.find(rsaMT->name->name);
    assert(iter != runtimeSizedArrays.end());
    return iter->second;
  }
  std::optional<RuntimeSizedArrayDefinitionT*> getMaybeRuntimeSizedArray(Name* name) {
    auto iter = runtimeSizedArrays.find(name->name);
    if (iter == runtimeSizedArrays.end()) {
      return std::nullopt;
    }
    return std::optional(iter->second);
  }
  Prototype* getImmDestructor(Kind* kind) {
    auto iter = immDestructorsByKind.find(kind);
    assert(iter != immDestructorsByKind.end());
    return iter->second;
  }

  std::string getKindExportName(Kind* kind) const {
    if (dynamic_cast<Int *>(kind)) {
      return "int64_t";
    } else if (dynamic_cast<Bool *>(kind)) {
      return "int8_t";
    } else if (dynamic_cast<Float *>(kind)) {
      return "double";
    } else if (dynamic_cast<Str *>(kind)) {
      return "ValeStr*";
    } else {
      auto iter = kindToExportName.find(kind);
      assert(iter != kindToExportName.end());
      return iter->second;
    }
  }
  std::string getFunctionExportName(Prototype* kind) const {
    auto iter = functionToExportName.find(kind);
    assert(iter != functionToExportName.end());
    return iter->second;
  }
  std::string getKindExternName(Kind* kind) const {
    auto iter = kindToExternName.find(kind);
    assert(iter != kindToExternName.end());
    return iter->second;
  }
  std::string getFunctionExternName(Prototype* kind) const {
    auto iter = functionToExternName.find(kind);
    assert(iter != functionToExternName.end());
    return iter->second;
  }
//  bool isExported(Name* name) {
//    auto exportedNameI = fullNameToExportName.find(name);
//    return exportedNameI != fullNameToExportName.end();
//  }
//  std::string getExportedName(Name* name) {
//    auto exportedNameI = fullNameToExportName.find(name);
//    if (exportedNameI == fullNameToExportName.end()) {
//      std::cerr << "No exported name for " << name->name << std::endl;
//      assert(false);
//    }
//    return exportedNameI->second;
//  }
//  bool isExportedAs(Name* name, const std::string& exportName) {
//    auto exportedNamesI = fullNameToExportName.find(name);
//    if (exportedNamesI == fullNameToExportName.end()) {
//      return false;
//    }
//    return exportedNamesI->second == exportName;
//  }
};

class Program {
public:
  std::unordered_map<PackageCoordinate*, Package*, AddressHasher<PackageCoordinate*>, std::equal_to<PackageCoordinate*>> packages;

  Program(
      std::unordered_map<PackageCoordinate*, Package*, AddressHasher<PackageCoordinate*>, std::equal_to<PackageCoordinate*>> packages_) :
      packages(std::move(packages_)) {}

  Package* getPackage(PackageCoordinate* packageCoord) {
    auto iter = packages.find(packageCoord);
    assert(iter != packages.end());
    return iter->second;
  }

  Function* getFunction(Name* name) {
    return getPackage(name->packageCoord)->getFunction(name);
  }
  std::optional<Function*> getMaybeFunction(Name* name) {
    return getPackage(name->packageCoord)->getMaybeFunction(name);
  }
  StructDefinition* getStruct(StructKind* structMT) {
    return getPackage(structMT->fullName->packageCoord)->getStruct(structMT);
  }
  std::optional<StructDefinition*> getMaybeStruct(StructKind* structMT) {
    return getPackage(structMT->fullName->packageCoord)->getStruct(structMT);
  }
  InterfaceDefinition* getInterface(InterfaceKind* interfaceMT) {
    return getPackage(interfaceMT->fullName->packageCoord)->getInterface(interfaceMT);
  }
  std::optional<InterfaceDefinition*> getMaybeInterface(InterfaceKind* interfaceMT) {
    return getPackage(interfaceMT->fullName->packageCoord)->getMaybeInterface(interfaceMT);
  }
  StaticSizedArrayDefinitionT* getStaticSizedArray(StaticSizedArrayT* ssaMT) {
    return getPackage(ssaMT->name->packageCoord)->getStaticSizedArray(ssaMT);
  }
  RuntimeSizedArrayDefinitionT* getRuntimeSizedArray(RuntimeSizedArrayT* rsaMT) {
    return getPackage(rsaMT->name->packageCoord)->getRuntimeSizedArray(rsaMT);
  }
  Prototype* getImmDestructor(Kind* kind) {
    return getPackage(kind->getPackageCoordinate())->getImmDestructor(kind);
  }
//  bool isExported(Name* name) {
//    return getPackage(name->packageCoord)->isExported(name);
//  }
//  std::vector<std::string> getExportedNames(Name* name) {
//    auto exportedNameI = fullNameToExportedNames.find(name);
//    if (exportedNameI == fullNameToExportedNames.end()) {
//      std::cerr << "No exported name for " << name->name << std::endl;
//      assert(false);
//    }
//    return exportedNameI->second;
//  }
//  bool isExportedAs(Name* name, const std::string& exportName) {
//    return getPackage(name->packageCoord)->isExportedAs(name, exportName);
//  }
};

class InterfaceMethod {
public:
  Prototype* prototype;
  int virtualParamIndex;

  InterfaceMethod(
      Prototype* prototype_,
      int virtualParamIndex_) :
      prototype(prototype_),
      virtualParamIndex(virtualParamIndex_) {}
};

// Represents how a struct implements an interface.
// Each edge has a vtable.
class Edge {
public:
  StructKind* structName;
  InterfaceKind* interfaceName;
  std::vector<std::pair<InterfaceMethod*, Prototype*>> structPrototypesByInterfaceMethod;

  Edge(
      StructKind* structName_,
      InterfaceKind* interfaceName_,
      std::vector<std::pair<InterfaceMethod*, Prototype*>> structPrototypesByInterfaceMethod_) :
      structName(structName_),
      interfaceName(interfaceName_),
      structPrototypesByInterfaceMethod(structPrototypesByInterfaceMethod_) {}
};

class StructDefinition {
public:
    Name* name;
    StructKind* kind;
    RegionId* regionId;
    Mutability mutability;
    std::vector<Edge*> edges;
    std::vector<StructMember*> members;
    Weakability weakability;

    StructDefinition(
        Name* name_,
        StructKind* kind_,
        RegionId* regionId_,
        Mutability mutability_,
        std::vector<Edge*> edges_,
        std::vector<StructMember*> members_,
        Weakability weakable_) :
        name(name_),
        kind(kind_),
        regionId(regionId_),
        mutability(mutability_),
        edges(edges_),
        members(members_),
        weakability(weakable_) {}

    Edge* getEdgeForInterface(InterfaceKind* interfaceMT) {
      for (auto e : edges) {
        if (e->interfaceName == interfaceMT)
          return e;
      }
      assert(false);
      return nullptr;
    }
};

class StructMember {
public:
    std::string fullName;
    std::string name;
    Variability variability;
    Reference* type;

    StructMember(
        std::string fullName_,
        std::string name_,
        Variability variability_,
        Reference* type_) :
        fullName(fullName_),
        name(name_),
        variability(variability_),
        type(type_) {}
};


class InterfaceDefinition {
public:
    Name* name;
    InterfaceKind* kind;
    RegionId* regionId;
    Mutability mutability;
    std::vector<Name*> superInterfaces;
    std::vector<InterfaceMethod*> methods;
    Weakability weakability;

    InterfaceDefinition(
        Name* name_,
        InterfaceKind* kind_,
        RegionId* regionId_,
        Mutability mutability_,
        const std::vector<Name*>& superInterfaces_,
        const std::vector<InterfaceMethod*>& methods_,
        Weakability weakable_) :
      name(name_),
      kind(kind_),
      regionId(regionId_),
      mutability(mutability_),
      superInterfaces(superInterfaces_),
      methods(methods_),
      weakability(weakable_) {
      assert((uint64_t)name > 0x10000);
      assert((uint64_t)kind > 0x10000);
    }
};

class Function {
public:
    Prototype* prototype;
    Expression* block;

    Function(

        Prototype* prototype_,
    Expression* block_
        ) :
        prototype(prototype_),
        block(block_) {}
};

// Interned
class Prototype {
public:
    Name* name;
    std::vector<Reference*> params;
    Reference* returnType;

    Prototype(
        Name* name_,
        std::vector<Reference*> params_,
        Reference* returnType_) :
      name(name_),
      params(std::move(params_)),
      returnType(returnType_) {}
};

// Interned
class VariableId {
public:
  int number;
  int height;
  std::string maybeName;

  VariableId(
      int number_,
      int height_,
      std::string maybeName_) :
      number(number_),
      height(height_),
      maybeName(maybeName_) {}
};

// Interned
class Local {
public:
  VariableId* id;
  Reference* type;
  bool keepAlive;

  Local(
      VariableId* id_,
      Reference* type_,
      bool keepAlive_) :
      id(id_),
      type(type_),
      keepAlive(keepAlive_) {}
};

#endif
