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
  StructReferend* emptyTupleStructRef;
  std::unordered_map<std::string, Prototype*> externs;
  std::unordered_map<std::string, Function*> functions;
  std::unordered_map<Referend*, Prototype*, AddressHasher<Referend*>> immDestructorsByKind;

  std::unordered_map<std::string, Name*> exportNameToFullName;
  std::unordered_map<Name*, std::string, AddressHasher<Name*>> fullNameToExportName;


  StructDefinition* getStruct(Name* name) {
    auto iter = structs.find(name->name);
    if (iter == structs.end()) {
      std::cerr << "Couldn't find struct: " << name->name << std::endl;
      exit(1);
    }
    return iter->second;
  }
  InterfaceDefinition* getInterface(Name* name) {
    auto iter = interfaces.find(name->name);
    assert(iter != interfaces.end());
    return iter->second;
  }
  StaticSizedArrayDefinitionT* getStaticSizedArray(Name* name) {
    auto iter = staticSizedArrays.find(name->name);
    assert(iter != staticSizedArrays.end());
    return iter->second;
  }
  RuntimeSizedArrayDefinitionT* getRuntimeSizedArray(Name* name) {
    auto iter = runtimeSizedArrays.find(name->name);
    assert(iter != runtimeSizedArrays.end());
    return iter->second;
  }
  Prototype* getImmDestructor(Referend* referend) {
    auto iter = immDestructorsByKind.find(referend);
    assert(iter != immDestructorsByKind.end());
    return iter->second;
  }
  bool isExported(Name* name) {
    auto exportedNameI = fullNameToExportName.find(name);
    return exportedNameI != fullNameToExportName.end();
  }
  std::string getExportedName(Name* name) {
    auto exportedNameI = fullNameToExportName.find(name);
    if (exportedNameI == fullNameToExportName.end()) {
      std::cerr << "No exported name for " << name->name << std::endl;
      assert(false);
    }
    return exportedNameI->second;
  }

  bool isExportedAs(Name* name, const std::string& exportName) {
    auto exportedNamesI = fullNameToExportName.find(name);
    if (exportedNamesI == fullNameToExportName.end()) {
      return false;
    }
    return exportedNamesI->second == exportName;
  }
};

class Program {
public:
  std::unordered_map<PackageCoordinate*, Package*, PackageCoordinate::Hasher, PackageCoordinate::Equator> packages;

  Package* getPackage(PackageCoordinate* packageCoord) {
    auto iter = packages.find(packageCoord);
    assert(iter != packages.end());
    return iter->second;
  }

  StructDefinition* getStruct(Name* name) {
    return getPackage(name->packageCoord)->getStruct(name);
  }
  InterfaceDefinition* getInterface(Name* name) {
    return getPackage(name->packageCoord)->getInterface(name);
  }
  StaticSizedArrayDefinitionT* getStaticSizedArray(Name* name) {
    return getPackage(name->packageCoord)->getStaticSizedArray(name);
  }
  RuntimeSizedArrayDefinitionT* getRuntimeSizedArray(Name* name) {
    return getPackage(name->packageCoord)->getRuntimeSizedArray(name);
  }
  Prototype* getImmDestructor(Referend* referend) {
    return getPackage(referend->getPackageCoordinate())->getImmDestructor(referend);
  }
  bool isExported(Name* name) {
    return getPackage(name->packageCoord)->isExported(name);
  }
//  std::vector<std::string> getExportedNames(Name* name) {
//    auto exportedNameI = fullNameToExportedNames.find(name);
//    if (exportedNameI == fullNameToExportedNames.end()) {
//      std::cerr << "No exported name for " << name->name << std::endl;
//      assert(false);
//    }
//    return exportedNameI->second;
//  }

  bool isExportedAs(Name* name, const std::string& exportName) {
    return getPackage(name->packageCoord)->isExportedAs(name, exportName);
  }
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
  StructReferend* structName;
  InterfaceReferend* interfaceName;
  std::vector<std::pair<InterfaceMethod*, Prototype*>> structPrototypesByInterfaceMethod;

  Edge(
      StructReferend* structName_,
      InterfaceReferend* interfaceName_,
      std::vector<std::pair<InterfaceMethod*, Prototype*>> structPrototypesByInterfaceMethod_) :
      structName(structName_),
      interfaceName(interfaceName_),
      structPrototypesByInterfaceMethod(structPrototypesByInterfaceMethod_) {}
};

class StructDefinition {
public:
    Name* name;
    StructReferend* referend;
    RegionId* regionId;
    Mutability mutability;
    std::vector<Edge*> edges;
    std::vector<StructMember*> members;
    Weakability weakability;

    StructDefinition(
        Name* name_,
        StructReferend* referend_,
        RegionId* regionId_,
        Mutability mutability_,
        std::vector<Edge*> edges_,
        std::vector<StructMember*> members_,
        Weakability weakable_) :
        name(name_),
        referend(referend_),
        regionId(regionId_),
        mutability(mutability_),
        edges(edges_),
        members(members_),
        weakability(weakable_) {}

    Edge* getEdgeForInterface(Name* interfaceName) {
      for (auto e : edges) {
        if (e->interfaceName->fullName == interfaceName)
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
    InterfaceReferend* referend;
    RegionId* regionId;
    Mutability mutability;
    std::vector<Name*> superInterfaces;
    std::vector<InterfaceMethod*> methods;
    Weakability weakability;

    InterfaceDefinition(
        Name* name_,
        InterfaceReferend* referend_,
        RegionId* regionId_,
        Mutability mutability_,
        const std::vector<Name*>& superInterfaces_,
        const std::vector<InterfaceMethod*>& methods_,
        Weakability weakable_) :
      name(name_),
      referend(referend_),
      regionId(regionId_),
      mutability(mutability_),
      superInterfaces(superInterfaces_),
      methods(methods_),
      weakability(weakable_) {
      assert((uint64_t)name > 0x10000);
      assert((uint64_t)referend > 0x10000);
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
