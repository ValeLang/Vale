#include <iostream>

#include "readjson.h"
#include "metal/instructions.h"
#include "metal/ast.h"

// for convenience
using json = nlohmann::json;

//template<typename T>
//concept ReturnsVec = requires(T a) {
//  { std::hash<T>{}(a) } -> std::convertible_to<std::size_t>;
//};
template<
    typename F,
    typename T = decltype((*(const F*)nullptr)(*(const json*)nullptr))>
std::vector<T> readArray(const json& j, const F& f) {
  assert(j.is_array());
  auto vec = std::vector<T>{};
  for (const auto& element : j) {
    vec.push_back(f(element));
  }
  return vec;
}
// F should return pair<key, value>
template<typename K, typename V, typename F>
std::unordered_map<K, V> readArrayIntoMap(const json& j, const F& f) {
  assert(j.is_array());
  auto map = std::unordered_map<K, V>{};
  map.reserve(j.size());
  for (const auto& element : j) {
    std::pair<K, V> p = f(element);
    map.emplace(move(p.first), move(p.second));
  }
  return map;
}

Name* readName(const json& name) {
  assert(name.is_string());
  return new Name(
      name.get<std::string>());
}

StructReferend* readStructReferend(const json& referend) {
  assert(referend[""] == "StructId");
  return new StructReferend(
      readName(referend["name"]));
}

Referend* readReferend(const json& referend) {
  assert(referend.is_object());
  if (referend[""] == "Int") {
    return new Int();
  } else if (referend[""] == "Bool") {
    return new Bool();
  } else if (referend[""] == "Str") {
    return new Str();
  } else if (referend[""] == "Void") {
    return new Void();
  } else if (referend[""] == "StructId") {
    return readStructReferend(referend);
  } else if (referend[""] == "Never") {
    return new Never();
  } else {
    std::cerr << "Unrecognized referend: " << referend[""] << std::endl;
    assert(false);
  }
}

Mutability readMutability(const json& mutability) {
  assert(mutability.is_object());
  if (mutability[""].get<std::string>() == "Mutable") {
    return Mutability::MUTABLE;
  } else if (mutability[""].get<std::string>() == "Immutable") {
    return Mutability::IMMUTABLE;
  } else {
    assert(false);
  }
}

Variability readVariability(const json& variability) {
  assert(variability.is_object());
  if (variability[""].get<std::string>() == "Varying") {
    return Variability::VARYING;
  } else if (variability[""].get<std::string>() == "Final") {
    return Variability::FINAL;
  } else {
    assert(false);
  }
}

Ownership readOwnership(const json& ownership) {
  assert(ownership.is_object());
//  std::cout << ownership.type() << std::endl;
  if (ownership[""].get<std::string>() == "Own") {
    return Ownership::OWN;
  } else if (ownership[""].get<std::string>() == "Borrow") {
    return Ownership::BORROW;
  } else if (ownership[""].get<std::string>() == "Share") {
    return Ownership::SHARE;
  } else {
    assert(false);
  }
}

Reference* readReference(const json& reference) {
  assert(reference.is_object());
  assert(reference[""] == "Ref");
  return new Reference(
      readOwnership(reference["ownership"]),
      readReferend(reference["referend"]));
}

Prototype* readPrototype(const json& prototype) {
  assert(prototype.is_object());
  assert(prototype[""] == "Prototype");
  return new Prototype(
      readName(prototype["name"]),
      readArray(prototype["params"], readReference),
      readReference(prototype["return"]));
}

VariableId* readVariableId(const json& local) {
  assert(local.is_object());
  assert(local[""] == "VariableId");
  return new VariableId(
      local["number"],
      "");
}

Local* readLocal(const json& local) {
  assert(local.is_object());
  assert(local[""] == "Local");
  return new Local(
      readVariableId(local["id"]),
      readReference(local["type"]));
}

Expression* readExpression(const json& expression) {
  assert(expression.is_object());
  std::string type = expression[""];
  if (type == "ConstantI64") {
    return new ConstantI64(
        expression["value"]);
  } else if (type == "ConstantBool") {
    return new ConstantBool(
        expression["value"]);
  } else if (type == "Return") {
    return new Return(
        readExpression(expression["sourceExpr"]));
  } else if (type == "Stackify") {
    return new Stackify(
        readExpression(expression["sourceExpr"]),
        readLocal(expression["local"]),
        "");
  } else if (type == "LocalStore") {
    return new LocalStore(
        readLocal(expression["local"]),
        readExpression(expression["sourceExpr"]),
        expression["localName"]);
  } else if (type == "Discard") {
    return new Discard(
        readExpression(expression["sourceExpr"]),
        readReference(expression["sourceResultType"]));
  } else if (type == "Argument") {
    return new Argument(
        readReference(expression["resultType"]),
        expression["argumentIndex"]);
  } else if (type == "Unstackify") {
    return new Unstackify(
        readLocal(expression["local"]));
  } else if (type == "LocalLoad") {
    return new LocalLoad(
        readLocal(expression["local"]),
        readOwnership(expression["targetOwnership"]),
        expression["localName"]);
  } else if (type == "Call") {
    return new Call(
        readPrototype(expression["function"]),
        readArray(expression["argExprs"], readExpression));
  } else if (type == "ExternCall") {
    return new ExternCall(
        readPrototype(expression["function"]),
        readArray(expression["argExprs"], readExpression));
  } else if (type == "Block") {
    return new Block(
        readArray(expression["exprs"], readExpression));
  } else if (type == "If") {
    return new If(
        readExpression(expression["conditionBlock"]),
        readExpression(expression["thenBlock"]),
        readExpression(expression["elseBlock"]),
        readReference(expression["commonSupertype"]));
  } else if (type == "While") {
    return new While(
        readExpression(expression["bodyBlock"]));
  } else if (type == "NewStruct") {
    return new NewStruct(
        readArray(expression["sourceExprs"], readExpression),
        readReference(expression["resultType"]));
  } else if (type == "Destructure") {
    return new Destructure(
        readExpression(expression["structExpr"]),
        readReference(expression["structType"]),
        readArray(expression["localTypes"], readReference),
        readArray(expression["localIndices"], readLocal));
  } else if (type == "MemberLoad") {
    return new MemberLoad(
        readExpression(expression["structExpr"]),
        readStructReferend(expression["structId"]),
        readReference(expression["structType"]),
        expression["memberIndex"],
        readOwnership(expression["targetOwnership"]),
        readReference(expression["expectedMemberType"]),
        readReference(expression["expectedResultType"]),
        expression["memberName"]);
  } else {
    std::cerr << "Unexpected instruction: " << type << std::endl;
    assert(false);
  }
}

Block* readBlock(const json& block) {
  assert(block.is_object());
  return new Block(
      readArray(block["exprs"], readExpression));
}

StructMember* readStructMember(const json& struuct) {
  assert(struuct.is_object());
  assert(struuct[""] == "StructMember");
  return new StructMember(
      struuct["name"],
      readVariability(struuct["variability"]),
      readReference(struuct["type"]));
}

StructDefinition* readStruct(const json& struuct) {
  assert(struuct.is_object());
  assert(struuct[""] == "Struct");
  return new StructDefinition(
      readName(struuct["name"]),
      readMutability(struuct["mutability"]),
      {},
      readArray(struuct["members"], readStructMember));
}

Function* readFunction(const json& function) {
  assert(function.is_object());
  assert(function[""] == "Function");
  return new Function(
      readPrototype(function["prototype"]),
      readExpression(function["block"]));
}

Program* readProgram(const json& program) {
  assert(program.is_object());
  assert(program[""] == "Program");
  return new Program(
      {},//readArray<readInterface>(program["interfaces"]),
      readArrayIntoMap<std::string, StructDefinition*>(
          program["structs"],
          [](json j){
            auto s = readStruct(j);
            return std::make_pair(s->name->name, s);
          }),
      nullptr,//readStructName(program["emptyPackStructRef"]),
      {},//readArray<readExtern>(program["externs"]),
      readArrayIntoMap<std::string, Function*>(
          program["functions"],
          [](json j){
              auto f = readFunction(j);
              return std::make_pair(f->prototype->name->name, f);
          }));
}
