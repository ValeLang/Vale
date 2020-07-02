#include <iostream>

#include "readjson.h"
#include "metal/instructions.h"
#include "metal/ast.h"

// for convenience
using json = nlohmann::json;

struct HashRefVec {
  size_t operator()(const std::vector<Reference *> &refs) const {
    size_t result = 1337;
    for (auto el : refs) {
      result += (size_t) el;
    }
    return result;
  }
};
struct RefVecEquals {
  bool operator()(
      const std::vector<Reference *> &a,
      const std::vector<Reference *> &b) const {
    if (a.size() != b.size())
      return false;
    for (size_t i = 0; i < a.size(); i++) {
      if (a[i] != b[i])
        return false;
    }
    return true;
  }
};

class Cache {
public:
  Int* innt = new Int();
  Bool* boool = new Bool();
  Str* str = new Str();
  Void* vooid = new Void();
  Never* never = new Never();

  std::unordered_map<Name*, StructReferend*> structReferends;
  std::unordered_map<std::string, Name*> names;

  // This is conceptually a map<[Reference*, Mutability], RawArrayT*>.
  std::unordered_map<Reference*, std::unordered_map<Mutability, RawArrayT*>> rawArrays;
  std::unordered_map<Name*, UnknownSizeArrayT*> unknownSizeArrays;
  std::unordered_map<Name*, KnownSizeArrayT*> knownSizeArrays;
  std::unordered_map<Referend*, std::unordered_map<Ownership, Reference*>> references;
  std::unordered_map<Name*, std::unordered_map<Reference*, std::unordered_map<std::vector<Reference*>, Prototype*, HashRefVec, RefVecEquals>>> prototypes;
  std::unordered_map<int, VariableId*> variableIds;
  std::unordered_map<VariableId*, std::unordered_map<Reference*, Local*>> locals;
};

Reference* readReference(Cache* cache, const json& reference);
Ownership readOwnership(Cache* cache, const json& ownership);
Mutability readMutability(const json& mutability);

//template<typename T>
//concept ReturnsVec = requires(T a) {
//  { std::hash<T>{}(a) } -> std::convertible_to<std::size_t>;
//};
template<
    typename F,
    typename T = decltype((*(const F*)nullptr)(nullptr, *(const json*)nullptr))>
std::vector<T> readArray(Cache* cache, const json& j, const F& f) {
  assert(j.is_array());
  auto vec = std::vector<T>{};
  for (const auto& element : j) {
    vec.push_back(f(cache, element));
  }
  return vec;
}
// F should return pair<key, value>
template<typename K, typename V, typename F>
std::unordered_map<K, V> readArrayIntoMap(Cache* cache, const json& j, const F& f) {
  assert(j.is_array());
  auto map = std::unordered_map<K, V>{};
  map.reserve(j.size());
  for (const auto& element : j) {
    std::pair<K, V> p = f(cache, element);
    map.emplace(move(p.first), move(p.second));
  }
  return map;
}

template<typename K, typename V, typename H, typename E, typename F>
V makeIfNotPresent(std::unordered_map<K, V, H, E>* map, const K& key, F&& makeElement) {
  auto iter = map->find(key);
  if (iter == map->end()) {
    auto p = map->emplace(key, makeElement());
    iter = p.first;
  }
  return iter->second;
}

Name* readName(Cache* cache, const json& name) {
  assert(name.is_string());
  auto nameStr = name.get<std::string>();

  return makeIfNotPresent(
      &cache->names,
      nameStr,
      [&](){ return new Name(nameStr); });
}

StructReferend* readStructReferend(Cache* cache, const json& referend) {
  assert(referend[""] == "StructId");

  auto structName = readName(cache, referend["name"]);

  return makeIfNotPresent(
      &cache->structReferends,
      structName,
      [&](){ return new StructReferend(structName); });
}

RawArrayT* readRawArray(Cache* cache, const json& rawArray) {
  assert(rawArray[""] == "Array");

  auto mutability = readMutability(rawArray["mutability"]);
  auto elementType = readReference(cache, rawArray["elementType"]);

  return makeIfNotPresent(
      &cache->rawArrays[elementType],
      mutability,
      [&](){ return new RawArrayT(mutability, elementType); });
}

UnknownSizeArrayT* readUnknownSizeArray(Cache* cache, const json& referend) {
  auto name = readName(cache, referend["name"]);
  auto rawArray = readRawArray(cache, referend["array"]);

  return makeIfNotPresent(
      &cache->unknownSizeArrays,
      name,
      [&](){ return new UnknownSizeArrayT(name, rawArray); });
}

KnownSizeArrayT* readKnownSizeArray(Cache* cache, const json& referend) {
  auto name = readName(cache, referend["name"]);
  auto rawArray = readRawArray(cache, referend["array"]);
  auto size = referend["size"].get<int>();

  return makeIfNotPresent(
      &cache->knownSizeArrays,
      name,
      [&](){ return new KnownSizeArrayT(name, size, rawArray); });
}

Referend* readReferend(Cache* cache, const json& referend) {
  assert(referend.is_object());
  if (referend[""] == "Int") {
    return cache->innt;
  } else if (referend[""] == "Bool") {
    return cache->boool;
  } else if (referend[""] == "Str") {
    return cache->str;
  } else if (referend[""] == "Void") {
    return cache->vooid;
  } else if (referend[""] == "StructId") {
    return readStructReferend(cache, referend);
  } else if (referend[""] == "Never") {
    return cache->never;
  } else if (referend[""] == "UnknownSizeArray") {
    return readUnknownSizeArray(cache, referend);
  } else if (referend[""] == "KnownSizeArray") {
    return readKnownSizeArray(cache, referend);
  } else {
    std::cerr << "Unrecognized referend: " << referend[""] << std::endl;
    assert(false);
  }
}

Reference* readReference(Cache* cache, const json& reference) {
  assert(reference.is_object());
  assert(reference[""] == "Ref");

  auto ownership =readOwnership(cache, reference["ownership"]);
  auto referend = readReferend(cache, reference["referend"]);

  return makeIfNotPresent(
      &cache->references[referend],
      ownership,
      [&](){ return new Reference(ownership, referend); });
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

Ownership readOwnership(Cache* cache, const json& ownership) {
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

Prototype* readPrototype(Cache* cache, const json& prototype) {
  assert(prototype.is_object());
  assert(prototype[""] == "Prototype");

  auto name = readName(cache, prototype["name"]);
  auto params = readArray(cache, prototype["params"], readReference);
  auto retuurn = readReference(cache, prototype["return"]);

  return makeIfNotPresent(
      &cache->prototypes[name][retuurn],
      params,
      [&](){ return new Prototype(name, params, retuurn); });
}

VariableId* readVariableId(Cache* cache, const json& variable) {
  assert(variable.is_object());
  assert(variable[""] == "VariableId");

  int number = variable["number"];

  return makeIfNotPresent(
      &cache->variableIds,
      number,
      [&](){ return new VariableId(number, ""); });
}

Local* readLocal(Cache* cache, const json& local) {
  assert(local.is_object());
  assert(local[""] == "Local");
  auto varId = readVariableId(cache, local["id"]);
  auto ref = readReference(cache, local["type"]);

  return makeIfNotPresent(
      &cache->locals[varId],
      ref,
      [&](){ return new Local(varId, ref); });
}

Expression* readExpression(Cache* cache, const json& expression) {
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
        readExpression(cache, expression["sourceExpr"]));
  } else if (type == "Stackify") {
    return new Stackify(
        readExpression(cache, expression["sourceExpr"]),
        readLocal(cache, expression["local"]),
        "");
  } else if (type == "LocalStore") {
    return new LocalStore(
        readLocal(cache, expression["local"]),
        readExpression(cache, expression["sourceExpr"]),
        expression["localName"]);
  } else if (type == "Discard") {
    return new Discard(
        readExpression(cache, expression["sourceExpr"]),
        readReference(cache, expression["sourceResultType"]));
  } else if (type == "Argument") {
    return new Argument(
        readReference(cache, expression["resultType"]),
        expression["argumentIndex"]);
  } else if (type == "Unstackify") {
    return new Unstackify(
        readLocal(cache, expression["local"]));
  } else if (type == "LocalLoad") {
    return new LocalLoad(
        readLocal(cache, expression["local"]),
        readOwnership(cache, expression["targetOwnership"]),
        expression["localName"]);
  } else if (type == "Call") {
    return new Call(
        readPrototype(cache, expression["function"]),
        readArray(cache, expression["argExprs"], readExpression));
  } else if (type == "ExternCall") {
    return new ExternCall(
        readPrototype(cache, expression["function"]),
        readArray(cache, expression["argExprs"], readExpression));
  } else if (type == "Block") {
    return new Block(
        readArray(cache, expression["exprs"], readExpression));
  } else if (type == "If") {
    return new If(
        readExpression(cache, expression["conditionBlock"]),
        readExpression(cache, expression["thenBlock"]),
        readExpression(cache, expression["elseBlock"]),
        readReference(cache, expression["commonSupertype"]));
  } else if (type == "While") {
    return new While(
        readExpression(cache, expression["bodyBlock"]));
  } else if (type == "NewStruct") {
    return new NewStruct(
        readArray(cache, expression["sourceExprs"], readExpression),
        readReference(cache, expression["resultType"]));
  } else if (type == "Destructure") {
    return new Destructure(
        readExpression(cache, expression["structExpr"]),
        readReference(cache, expression["structType"]),
        readArray(cache, expression["localTypes"], readReference),
        readArray(cache, expression["localIndices"], readLocal));
  } else if (type == "MemberLoad") {
    return new MemberLoad(
        readExpression(cache, expression["structExpr"]),
        readStructReferend(cache, expression["structId"]),
        readReference(cache, expression["structType"]),
        expression["memberIndex"],
        readOwnership(cache, expression["targetOwnership"]),
        readReference(cache, expression["expectedMemberType"]),
        readReference(cache, expression["expectedResultType"]),
        expression["memberName"]);
  } else if (type == "NewArrayFromValuesH") {
    return new NewArrayFromValues(
        readArray(cache, expression["sourceExprs"], readExpression),
        readReference(cache, expression["resultType"]));
  } else if (type == "KnownSizeArrayLoadH") {
    return new KnownSizeArrayLoad(
        readExpression(cache, expression["arrayExpr"]),
        readReference(cache, expression["arrayType"]),
        readExpression(cache, expression["indexExpr"]),
        readReference(cache, expression["resultType"]),
        readOwnership(cache, expression["targetOwnership"]));
  } else {
    std::cerr << "Unexpected instruction: " << type << std::endl;
    assert(false);
  }
}

StructMember* readStructMember(Cache* cache, const json& struuct) {
  assert(struuct.is_object());
  assert(struuct[""] == "StructMember");
  return new StructMember(
      struuct["name"],
      readVariability(struuct["variability"]),
      readReference(cache, struuct["type"]));
}

StructDefinition* readStruct(Cache* cache, const json& struuct) {
  assert(struuct.is_object());
  assert(struuct[""] == "Struct");
  return new StructDefinition(
      readName(cache, struuct["name"]),
      readMutability(struuct["mutability"]),
      {},
      readArray(cache, struuct["members"], readStructMember));
}

Function* readFunction(Cache* cache, const json& function) {
  assert(function.is_object());
  assert(function[""] == "Function");
  return new Function(
      readPrototype(cache, function["prototype"]),
      readExpression(cache, function["block"]));
}

Program* readProgram(const json& program) {
  assert(program.is_object());
  assert(program[""] == "Program");
  Cache cache;
  return new Program(
      {},//readArray<readInterface>(program["interfaces"]),
      readArrayIntoMap<std::string, StructDefinition*>(
          &cache,
          program["structs"],
          [](Cache* cache, json j){
            auto s = readStruct(cache, j);
            return std::make_pair(s->name->name, s);
          }),
      nullptr,//readStructName(program["emptyPackStructRef"]),
      {},//readArray<readExtern>(program["externs"]),
      readArrayIntoMap<std::string, Function*>(
          &cache,
          program["functions"],
          [](Cache* cache, json j){
              auto f = readFunction(cache, j);
              return std::make_pair(f->prototype->name->name, f);
          }));
}
