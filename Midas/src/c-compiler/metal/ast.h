#ifndef VALE_AST_H_
#define VALE_AST_H_

#include "types.h"

#include <llvm-c/Core.h>
#include <llvm-c/DebugInfo.h>
#include <llvm-c/ExecutionEngine.h>
#include <llvm-c/Analysis.h>

#include <stdio.h>
#include <assert.h>
#include <string>
#include <vector>
#include <memory>
// #include <optional>

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

class Name {
public:
  std::string name;

  Name(const std::string& name_) : name(name_) {}
};

class Program {
public:
    std::unordered_map<std::string, InterfaceDefinition*> interfaces;
    std::unordered_map<std::string, StructDefinition*> structs;
    // Get rid of this; since there's no IDs anymore we can have a stable
    // hardcoded NameH("__Pack", Some(List()), None, None).
    StructReferend* emptyPackStructRef;
    std::unordered_map<std::string, Prototype*> externs;
    std::unordered_map<std::string, Function*> functions;

    Program(
      std::unordered_map<std::string, InterfaceDefinition*> interfaces_,
      std::unordered_map<std::string, StructDefinition*> structs_,
      StructReferend* emptyPackStructRef_,
      std::unordered_map<std::string, Prototype*> externs_,
      std::unordered_map<std::string, Function*> functions_) :
        interfaces(move(interfaces_)),
        structs(move(structs_)),
        emptyPackStructRef(emptyPackStructRef_),
        externs(move(externs_)),
        functions(move(functions_)) {}


  StructDefinition* getStruct(Name* name) {
    auto structIter = structs.find(name->name);
    assert(structIter != structs.end());
    return structIter->second;
  }
};

class StructDefinition {
public:
    Name* name;
    Mutability mutability;
    std::vector<Edge*> edges;
    std::vector<StructMember*> members;

    StructDefinition(
        Name* name_,
        Mutability mutability_,
        std::vector<Edge*> edges_,
        std::vector<StructMember*> members_) :
        name(name_),
        mutability(mutability_),
        edges(edges_),
        members(members_) {}
};

class StructMember {
public:
    std::string name;
    Variability variability;
    Reference* type;

    StructMember(
        std::string name_,
        Variability variability_,
        Reference* type_) :
        name(name_),
        variability(variability_),
        type(type_) {}
};


class InterfaceDefinition {
public:
    Name* name;
    Mutability* mutability;
    std::vector<Name*> superInterfaces;
    std::vector<Prototype*> prototypes;
};

// Represents how a struct implements an interface.
// Each edge has a vtable.
class Edge {
public:
    Name* structName;
    Name* interfaceName;
    std::vector<std::pair<Prototype*, Prototype*>>
        structPrototypesByInterfacePrototype;
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
    Reference* returnType_
        ) :
        name(name_),
        params(std::move(params_)),
        returnType(returnType_) {}
};

#endif
