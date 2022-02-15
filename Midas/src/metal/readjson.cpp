#include <iostream>
#include <sstream>

#include "readjson.h"
#include "instructions.h"
#include "ast.h"
#include "metalcache.h"

// for convenience
using json = nlohmann::json;

Reference* readReference(MetalCache* cache, const json& reference);
Ownership readUnconvertedOwnership(MetalCache* cache, const json& ownership);
Location readLocation(MetalCache* cache, const json& location);
Mutability readMutability(const json& mutability);
Variability readVariability(const json& variability);
Name* readName(MetalCache* cache, const json& name);

//template<typename T>
//concept ReturnsVec = requires(T a) {
//  { std::hash<T>{}(a) } -> std::convertible_to<std::size_t>;
//};
template<
    typename F,
    typename T = decltype((*(const F*)nullptr)(nullptr, *(const json*)nullptr))>
std::vector<T> readArray(MetalCache* cache, const json& j, const F& f) {
  assert(j.is_array());
  auto vec = std::vector<T>{};
  for (const auto& element : j) {
    vec.push_back(f(cache, element));
  }
  return vec;
}
// F should return pair<key, value>
template<typename K, typename V, typename H, typename E, typename F>
std::unordered_map<K, V, H, E> readArrayIntoMap(MetalCache* cache, H h, E e, const json& j, const F& f) {
  assert(j.is_array());
  std::unordered_map<K, V, H, E> map(0, move(h), move(e));
  map.reserve(j.size());
  for (const auto& element : j) {
    std::pair<K, V> p = f(cache, element);
    assert(map.find(p.first) == map.end());
    map.emplace(move(p.first), move(p.second));
  }
  return map;
}

std::string readString(MetalCache* cache, const json& name) {
  assert(name.is_string());
  auto nameStr = name.get<std::string>();
  return nameStr;
}

int64_t readI64(MetalCache* cache, const json& name) {
  if (name.is_number_integer()) {
    int64_t i = name;
    return i;
  } else if (name.is_string()) {
    std::string str = name;
    int64_t l = std::stoull(str);
    assert(std::to_string(l) == str);
    return l;
  } else {
    std::cerr << "Couldn't read number in json!" << std::endl;
    assert(false);
    exit(1);
  }
}

PackageCoordinate* readPackageCoordinate(MetalCache* cache, const json& packageCoord) {
  assert(packageCoord["__type"] == "PackageCoordinate");
  auto moduleName = readString(cache, packageCoord["project"]);//.get<std::string>();
  auto packageSteps = readArray(cache, packageCoord["packageSteps"], readString);
  return cache->getPackageCoordinate(moduleName, packageSteps);
}

Name* readName(MetalCache* cache, const json& name) {
  assert(name.is_object());
  auto packageCoord = readPackageCoordinate(cache, name["packageCoordinate"]);
  auto readableName = readString(cache, name["readableName"]);
  int id = name["id"];
  auto parts = readArray(cache, name["parts"], readString);

  std::stringstream nameStrBuilder;
  nameStrBuilder << readableName;
  if (id >= 0) {
    nameStrBuilder << "_" << id;
  }
  return cache->getName(packageCoord, nameStrBuilder.str());
}

StructKind* readStructKind(MetalCache* cache, const json& kind) {
  assert(kind["__type"] == "StructId");

  auto structName = readName(cache, kind["name"]);

  auto result = cache->getStructKind(structName);

  return result;
}

InterfaceKind* readInterfaceKind(MetalCache* cache, const json& kind) {
  assert(kind["__type"] == "InterfaceId");

  auto interfaceName = readName(cache, kind["name"]);

  return cache->getInterfaceKind(interfaceName);
}

RuntimeSizedArrayT* readRuntimeSizedArray(MetalCache* cache, const json& kind) {
  auto name = readName(cache, kind["name"]);

  return cache->getRuntimeSizedArray(name);
}

RuntimeSizedArrayDefinitionT* readRuntimeSizedArrayDefinition(MetalCache* cache, const json& rsa) {
  auto name = readName(cache, rsa["name"]);
  auto kind = readRuntimeSizedArray(cache, rsa["kind"]);
  auto mutability = readMutability(rsa["mutability"]);
  auto elementType = readReference(cache, rsa["elementType"]);
  auto regionId = mutability == Mutability::IMMUTABLE ? cache->rcImmRegionId : cache->mutRegionId;

  return new RuntimeSizedArrayDefinitionT(name, kind, regionId, mutability, elementType);
}

StaticSizedArrayT* readStaticSizedArray(MetalCache* cache, const json& kind) {
  auto name = readName(cache, kind["name"]);

  return makeIfNotPresent(
      &cache->staticSizedArrays,
      name,
      [&](){ return new StaticSizedArrayT(name); });
}

StaticSizedArrayDefinitionT* readStaticSizedArrayDefinition(MetalCache* cache, const json& ssa) {
  auto name = readName(cache, ssa["name"]);
  auto kind = readStaticSizedArray(cache, ssa["kind"]);
  auto mutability = readMutability(ssa["mutability"]);
  auto variability = readVariability(ssa["variability"]);
  auto elementType = readReference(cache, ssa["elementType"]);
  auto regionId = mutability == Mutability::IMMUTABLE ? cache->rcImmRegionId : cache->mutRegionId;

  auto size = ssa["size"].get<int>();

  return new StaticSizedArrayDefinitionT(name, kind, size, regionId, mutability, variability, elementType);
}

Kind* readKind(MetalCache* cache, const json& kind) {
  assert(kind.is_object());
  if (kind["__type"] == "Int") {
    int bits = kind["bits"];
    return cache->getInt(cache->rcImmRegionId, bits);
  } else if (kind["__type"] == "Void") {
    return cache->vooid;
  } else if (kind["__type"] == "Bool") {
    return cache->boool;
  } else if (kind["__type"] == "Float") {
    return cache->flooat;
  } else if (kind["__type"] == "Str") {
    return cache->str;
  } else if (kind["__type"] == "StructId") {
    return readStructKind(cache, kind);
  } else if (kind["__type"] == "Never") {
    return cache->never;
  } else if (kind["__type"] == "RuntimeSizedArray") {
    return readRuntimeSizedArray(cache, kind);
  } else if (kind["__type"] == "StaticSizedArray") {
    return readStaticSizedArray(cache, kind);
  } else if (kind["__type"] == "InterfaceId") {
    return readInterfaceKind(cache, kind);
  } else {
    std::cerr << "Unrecognized kind: " << kind["__type"] << std::endl;
    assert(false);
  }
}

Reference* readReference(MetalCache* cache, const json& reference) {
  assert(reference.is_object());
  assert(reference["__type"] == "Ref");

  auto ownership = readUnconvertedOwnership(cache, reference["ownership"]);
  auto location = readLocation(cache, reference["location"]);
  auto kind = readKind(cache, reference["kind"]);
//  std::string debugStr = reference["debugStr"];

  return cache->getReference(
      ownership,
      location,
      kind);
}

Mutability readMutability(const json& mutability) {
  assert(mutability.is_object());
  if (mutability["__type"].get<std::string>() == "Mutable") {
    return Mutability::MUTABLE;
  } else if (mutability["__type"].get<std::string>() == "Immutable") {
    return Mutability::IMMUTABLE;
  } else {
    assert(false);
  }
}

Variability readVariability(const json& variability) {
  assert(variability.is_object());
  if (variability["__type"].get<std::string>() == "Varying") {
    return Variability::VARYING;
  } else if (variability["__type"].get<std::string>() == "Final") {
    return Variability::FINAL;
  } else {
    assert(false);
  }
}

Ownership readUnconvertedOwnership(MetalCache* cache, const json& ownership) {
  assert(ownership.is_object());
//  std::cout << ownership.type() << std::endl;
  if (ownership["__type"].get<std::string>() == "Own") {
    return Ownership::OWN;
  } else if (ownership["__type"].get<std::string>() == "Pointer") {
    return Ownership::BORROW;
  } else if (ownership["__type"].get<std::string>() == "Borrow") {
    return Ownership::BORROW;
  } else if (ownership["__type"].get<std::string>() == "Weak") {
    return Ownership::WEAK;
  } else if (ownership["__type"].get<std::string>() == "Share") {
    return Ownership::SHARE;
  } else {
    assert(false);
  }
}

Location readLocation(MetalCache* cache, const json& location) {
  assert(location.is_object());
//  std::cout << location.type() << std::endl;
  if (location["__type"].get<std::string>() == "Inline") {
    return Location::INLINE;
  } else if (location["__type"].get<std::string>() == "Yonder") {
    return Location::YONDER;
  } else {
    assert(false);
  }
}

Prototype* readPrototype(MetalCache* cache, const json& prototype) {
  assert(prototype.is_object());
  assert(prototype["__type"] == "Prototype");

  auto name = readName(cache, prototype["name"]);
  auto params = readArray(cache, prototype["params"], readReference);
  auto retuurn = readReference(cache, prototype["return"]);

  return cache->getPrototype(name, retuurn, params);
}

VariableId* readVariableId(MetalCache* cache, const json& variable) {
  assert(variable.is_object());
  assert(variable["__type"] == "VariableId");

  int number = variable["number"];
  int height = variable["height"];
  std::string maybeName;
  if (variable["optName"]["__type"] == "Some") {
    maybeName = readName(cache, variable["optName"]["value"])->name;
  }

  return makeIfNotPresent(
      &cache->variableIds[number],
      maybeName,
      [&](){ return new VariableId(number, height, maybeName); });
}

Local* readLocal(MetalCache* cache, const json& local) {
  assert(local.is_object());
  assert(local["__type"] == "Local");
  auto varId = readVariableId(cache, local["id"]);
  auto ref = readReference(cache, local["type"]);
  bool keepAlive = local["keepAlive"];

  return makeIfNotPresent(
      &makeIfNotPresent(
          &makeIfNotPresent(
              &cache->locals,
              varId,
              [&](){ return MetalCache::LocalByKeepAliveByReferenceMap (0, cache->addressNumberer->makeHasher<Reference*>()); }),
          ref,
          [&](){ return MetalCache::LocalByKeepAliveMap(); }),
      keepAlive,
      [&](){ return new Local(varId, ref, keepAlive); });
}

Expression* readExpression(MetalCache* cache, const json& expression) {
  assert(expression.is_object());
  std::string type = expression["__type"];
  if (type == "ConstantInt") {
    return new ConstantInt(
        readI64(cache, expression["value"]),
        expression["bits"]);
  } else if (type == "ConstantVoid") {
    return new ConstantVoid();
  } else if (type == "ConstantBool") {
    return new ConstantBool(
        expression["value"]);
  } else if (type == "Return") {
    return new Return(
        readExpression(cache, expression["sourceExpr"]),
        readReference(cache, expression["sourceType"]));
  } else if (type == "Break") {
    return new Break();
  } else if (type == "Stackify") {
    return new Stackify(
        readExpression(cache, expression["sourceExpr"]),
        readLocal(cache, expression["local"]),
        expression["knownLive"],
        "");
  } else if (type == "LocalStore") {
    return new LocalStore(
        readLocal(cache, expression["local"]),
        readExpression(cache, expression["sourceExpr"]),
        readName(cache, expression["localName"])->name,
        expression["knownLive"]);
  } else if (type == "MemberStore") {
    return new MemberStore(
        readExpression(cache, expression["structExpr"]),
        readReference(cache, expression["structType"]),
        expression["structKnownLive"],
        expression["memberIndex"],
        readExpression(cache, expression["sourceExpr"]),
        readReference(cache, expression["resultType"]),
        readName(cache, expression["memberName"])->name);
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
        readUnconvertedOwnership(cache, expression["targetOwnership"]),
        readName(cache, expression["localName"])->name);
  } else if (type == "BorrowToWeak" || type == "PointerToWeak") {
    return new WeakAlias(
        readExpression(cache, expression["sourceExpr"]),
        readReference(cache, expression["sourceType"]),
        readKind(cache, expression["sourceKind"]),
        readReference(cache, expression["resultType"]));
  } else if (type == "BorrowToPointer") {
    return new BorrowToPointer(
        readExpression(cache, expression["sourceExpr"]),
        readReference(cache, expression["resultType"]));
  } else if (type == "PointerToBorrow") {
    return new PointerToBorrow(
        readExpression(cache, expression["sourceExpr"]),
        readReference(cache, expression["resultType"]));
  } else if (type == "NarrowPermission") {
    return new NarrowPermission(
        readExpression(cache, expression["sourceExpr"]));
  } else if (type == "Call") {
    return new Call(
        readPrototype(cache, expression["function"]),
        readArray(cache, expression["argExprs"], readExpression));
  } else if (type == "ExternCall") {
    return new ExternCall(
        readPrototype(cache, expression["function"]),
        readArray(cache, expression["argExprs"], readExpression),
        readArray(cache, expression["argTypes"], readReference));
  } else if (type == "Consecutor") {
    return new Consecutor(
        readArray(cache, expression["exprs"], readExpression));
  } else if (type == "Block") {
    return new Block(
        readExpression(cache, expression["innerExpr"]),
        readReference(cache, expression["innerType"]));
  } else if (type == "If") {
    return new If(
        readExpression(cache, expression["conditionBlock"]),
        readExpression(cache, expression["thenBlock"]),
        readReference(cache, expression["thenResultType"]),
        readExpression(cache, expression["elseBlock"]),
        readReference(cache, expression["elseResultType"]),
        readReference(cache, expression["commonSupertype"]));
  } else if (type == "While") {
    return new While(
        readExpression(cache, expression["bodyBlock"]));
  } else if (type == "NewStruct") {
    return new NewStruct(
        readArray(cache, expression["sourceExprs"], readExpression),
        readReference(cache, expression["resultType"]));
  } else if (type == "Destroy") {
    return new Destroy(
        readExpression(cache, expression["structExpr"]),
        readReference(cache, expression["structType"]),
        readArray(cache, expression["localTypes"], readReference),
        readArray(cache, expression["localIndices"], readLocal),
        readArray(cache, expression["localsKnownLives"], [](MetalCache*, const json& j) -> bool { return j; }));
  } else if (type == "MemberLoad") {
    return new MemberLoad(
        readExpression(cache, expression["structExpr"]),
        readStructKind(cache, expression["structId"]),
        readReference(cache, expression["structType"]),
        expression["structKnownLive"],
        expression["memberIndex"],
        readUnconvertedOwnership(cache, expression["targetOwnership"]),
        readReference(cache, expression["expectedMemberType"]),
        readReference(cache, expression["expectedResultType"]),
        readName(cache, expression["memberName"])->name);
  } else if (type == "NewArrayFromValues") {
    return new NewArrayFromValues(
        readArray(cache, expression["sourceExprs"], readExpression),
        readReference(cache, expression["resultType"]),
        readStaticSizedArray(cache, expression["resultKind"]));
  } else if (type == "StaticSizedArrayLoad") {
    return new StaticSizedArrayLoad(
        readExpression(cache, expression["arrayExpr"]),
        readReference(cache, expression["arrayType"]),
        readStaticSizedArray(cache, expression["arrayKind"]),
        expression["arrayKnownLive"],
        readExpression(cache, expression["indexExpr"]),
        readReference(cache, expression["resultType"]),
        readUnconvertedOwnership(cache, expression["targetOwnership"]),
        readReference(cache, expression["expectedElementType"]),
        expression["arraySize"]);
  } else if (type == "RuntimeSizedArrayLoad") {
    return new RuntimeSizedArrayLoad(
        readExpression(cache, expression["arrayExpr"]),
        readReference(cache, expression["arrayType"]),
        readRuntimeSizedArray(cache, expression["arrayKind"]),
        expression["arrayKnownLive"],
        readExpression(cache, expression["indexExpr"]),
        readReference(cache, expression["indexType"]),
        readKind(cache, expression["indexKind"]),
        readReference(cache, expression["resultType"]),
        readUnconvertedOwnership(cache, expression["targetOwnership"]),
        readReference(cache, expression["expectedElementType"]));
  } else if (type == "RuntimeSizedArrayStore") {
    return new RuntimeSizedArrayStore(
        readExpression(cache, expression["arrayExpr"]),
        readReference(cache, expression["arrayType"]),
        readRuntimeSizedArray(cache, expression["arrayKind"]),
        expression["arrayKnownLive"],
        readExpression(cache, expression["indexExpr"]),
        readReference(cache, expression["indexType"]),
        readKind(cache, expression["indexKind"]),
        readExpression(cache, expression["sourceExpr"]),
        readReference(cache, expression["sourceType"]),
        readKind(cache, expression["sourceKind"]));
  } else if (type == "NewImmRuntimeSizedArray") {
    return new NewImmRuntimeSizedArray(
        readExpression(cache, expression["sizeExpr"]),
        readReference(cache, expression["sizeType"]),
        readKind(cache, expression["sizeKind"]),
        readExpression(cache, expression["generatorExpr"]),
        readReference(cache, expression["generatorType"]),
        readKind(cache, expression["generatorKind"]),
        readPrototype(cache, expression["generatorMethod"]),
        expression["generatorKnownLive"],
        readReference(cache, expression["resultType"]),
        readReference(cache, expression["elementType"]));
  } else if (type == "NewMutRuntimeSizedArray") {
    return new NewMutRuntimeSizedArray(
        readExpression(cache, expression["capacityExpr"]),
        readReference(cache, expression["capacityType"]),
        readKind(cache, expression["capacityKind"]),
        readReference(cache, expression["resultType"]),
        readReference(cache, expression["elementType"]));
  } else if (type == "StaticArrayFromCallable") {
    return new StaticArrayFromCallable(
        readExpression(cache, expression["generatorExpr"]),
        readReference(cache, expression["generatorType"]),
        readKind(cache, expression["generatorKind"]),
        readPrototype(cache, expression["generatorMethod"]),
        expression["generatorKnownLive"],
        readReference(cache, expression["resultType"]),
        readReference(cache, expression["elementType"]));
  } else if (type == "DestroyMutRuntimeSizedArray") {
    return new DestroyMutRuntimeSizedArray(
        readExpression(cache, expression["arrayExpr"]),
        readReference(cache, expression["arrayType"]),
        readRuntimeSizedArray(cache, expression["arrayKind"]));
  } else if (type == "DestroyImmRuntimeSizedArray") {
    return new DestroyImmRuntimeSizedArray(
        readExpression(cache, expression["arrayExpr"]),
        readReference(cache, expression["arrayType"]),
        readRuntimeSizedArray(cache, expression["arrayKind"]),
        readExpression(cache, expression["consumerExpr"]),
        readReference(cache, expression["consumerType"]),
        readKind(cache, expression["consumerKind"]),
        readPrototype(cache, expression["consumerMethod"]),
        expression["consumerKnownLive"]);
  } else if (type == "PushRuntimeSizedArray") {
    return new PushRuntimeSizedArray(
        readExpression(cache, expression["arrayExpr"]),
        readReference(cache, expression["arrayType"]),
        readKind(cache, expression["arrayKind"]),
        readExpression(cache, expression["newcomerExpr"]),
        readReference(cache, expression["newcomerType"]),
        readKind(cache, expression["newcomerKind"]));
  } else if (type == "PopRuntimeSizedArray") {
    return new PopRuntimeSizedArray(
        readExpression(cache, expression["arrayExpr"]),
        readReference(cache, expression["arrayType"]),
        readKind(cache, expression["arrayKind"]));
  } else if (type == "ArrayLength") {
    return new ArrayLength(
        readExpression(cache, expression["sourceExpr"]),
        readReference(cache, expression["sourceType"]),
        expression["sourceKnownLive"]);
  } else if (type == "ArrayCapacity") {
    return new ArrayCapacity(
        readExpression(cache, expression["sourceExpr"]),
        readReference(cache, expression["sourceType"]),
        expression["sourceKnownLive"]);
  } else if (type == "StructToInterfaceUpcast") {
    return new StructToInterfaceUpcast(
        readExpression(cache, expression["sourceExpr"]),
        readReference(cache, expression["sourceStructType"]),
        readStructKind(cache, expression["sourceStructKind"]),
        readReference(cache, expression["targetInterfaceType"]),
        readInterfaceKind(cache, expression["targetInterfaceKind"]));
  } else if (type == "DestroyStaticSizedArrayIntoFunction") {
    return new DestroyStaticSizedArrayIntoFunction(
        readExpression(cache, expression["arrayExpr"]),
        readReference(cache, expression["arrayType"]),
        readStaticSizedArray(cache, expression["arrayKind"]),
        readExpression(cache, expression["consumerExpr"]),
        readReference(cache, expression["consumerType"]),
        readPrototype(cache, expression["consumerMethod"]),
        expression["consumerKnownLive"],
        readReference(cache, expression["arrayElementType"]),
        expression["arraySize"]);
  } else if (type == "InterfaceCall") {
    return new InterfaceCall(
        readArray(cache, expression["argExprs"], readExpression),
        expression["virtualParamIndex"],
        readInterfaceKind(cache, expression["interfaceRef"]),
        expression["indexInEdge"],
        readPrototype(cache, expression["functionType"]));
  } else if (type == "ConstantStr") {
    return new ConstantStr(
        expression["value"]);
  } else if (type == "ConstantF64") {
    return new ConstantF64(expression["value"]);
  } else if (type == "LockWeak") {
    return new LockWeak(
        readExpression(cache, expression["sourceExpr"]),
        readReference(cache, expression["sourceType"]),
        expression["sourceKnownLive"],
        readPrototype(cache, expression["someConstructor"]),
        readReference(cache, expression["someType"]),
        readStructKind(cache, expression["someKind"]),
        readPrototype(cache, expression["noneConstructor"]),
        readReference(cache, expression["noneType"]),
        readStructKind(cache, expression["noneKind"]),
        readReference(cache, expression["resultOptType"]),
        readInterfaceKind(cache, expression["resultOptKind"]));
  } else if (type == "AsSubtype") {
    return new AsSubtype(
        readExpression(cache, expression["sourceExpr"]),
        readReference(cache, expression["sourceType"]),
        expression["sourceKnownLive"],
        readKind(cache, expression["targetKind"]),
        readPrototype(cache, expression["okConstructor"]),
        readReference(cache, expression["okType"]),
        readStructKind(cache, expression["okKind"]),
        readPrototype(cache, expression["errConstructor"]),
        readReference(cache, expression["errType"]),
        readStructKind(cache, expression["errKind"]),
        readReference(cache, expression["resultResultType"]),
        readInterfaceKind(cache, expression["resultResultKind"]));
  } else {
    std::cerr << "Unexpected instruction: " << type << std::endl;
    assert(false);
  }
}

StructMember* readStructMember(MetalCache* cache, const json& struuct) {
  assert(struuct.is_object());
  assert(struuct["__type"] == "StructMember");
  return new StructMember(
      readName(cache, struuct["fullName"])->name,
      struuct["name"],
      readVariability(struuct["variability"]),
      readReference(cache, struuct["type"]));
}

InterfaceMethod* readInterfaceMethod(MetalCache* cache, const json& struuct) {
  assert(struuct.is_object());
  assert(struuct["__type"] == "InterfaceMethod");
  return cache->getInterfaceMethod(
      readPrototype(cache, struuct["prototype"]),
      struuct["virtualParamIndex"]);
}

std::pair<InterfaceMethod*, Prototype*> readInterfaceMethodAndPrototypeEntry(MetalCache* cache, const json& edge) {
  assert(edge.is_object());
  assert(edge["__type"] == "Entry");
  return std::make_pair(
      readInterfaceMethod(cache, edge["method"]),
      readPrototype(cache, edge["override"]));
}

Edge* readEdge(MetalCache* cache, const json& edge) {
  assert(edge.is_object());
  assert(edge["__type"] == "Edge");
  return new Edge(
      readStructKind(cache, edge["structName"]),
      readInterfaceKind(cache, edge["interfaceName"]),
      readArray(cache, edge["methods"], readInterfaceMethodAndPrototypeEntry));
}

StructDefinition* readStruct(MetalCache* cache, const json& struuct) {
  assert(struuct.is_object());
  assert(struuct["__type"] == "Struct");
  auto mutability = readMutability(struuct["mutability"]);
  auto result =
      new StructDefinition(
          readName(cache, struuct["name"]),
          readStructKind(cache, struuct["kind"]),
          mutability == Mutability::IMMUTABLE ? cache->rcImmRegionId : cache->mutRegionId,
          mutability,
          readArray(cache, struuct["edges"], readEdge),
          readArray(cache, struuct["members"], readStructMember),
          struuct["weakable"] ? Weakability::WEAKABLE : Weakability::NON_WEAKABLE);

  return result;
}

InterfaceDefinition* readInterface(MetalCache* cache, const json& interface) {
  assert(interface.is_object());
  assert(interface["__type"] == "Interface");
  auto mutability = readMutability(interface["mutability"]);
  return new InterfaceDefinition(
      readName(cache, interface["name"]),
      readInterfaceKind(cache, interface["kind"]),
      mutability == Mutability::IMMUTABLE ? cache->rcImmRegionId : cache->mutRegionId,
      mutability,
      {},
      readArray(cache, interface["methods"], readInterfaceMethod),
      interface["weakable"] ? Weakability::WEAKABLE : Weakability::NON_WEAKABLE);
}

Function* readFunction(MetalCache* cache, const json& function) {
  assert(function.is_object());
  assert(function["__type"] == "Function");
  return new Function(
      readPrototype(cache, function["prototype"]),
      readExpression(cache, function["block"]));
}

std::pair<Kind*, Prototype*> readKindAndPrototypeEntry(MetalCache* cache, const json& edge) {
  assert(edge.is_object());
  assert(edge["__type"] == "Entry");
  return std::make_pair(
      readKind(cache, edge["kind"]),
      readPrototype(cache, edge["destructor"]));
}

Package* readPackage(MetalCache* cache, const json& program) {
  assert(program.is_object());
  assert(program["__type"] == "Package");
  return new Package(
      cache->addressNumberer,
      readPackageCoordinate(cache, program["packageCoordinate"]),
      readArrayIntoMap<std::string, InterfaceDefinition*>(
          cache,
          std::hash<std::string>(),
          std::equal_to<std::string>(),
          program["interfaces"],
          [](MetalCache* cache, json j){
            auto s = readInterface(cache, j);
            return std::make_pair(s->name->name, s);
          }),
      readArrayIntoMap<std::string, StructDefinition*>(
          cache,
          std::hash<std::string>(),
          std::equal_to<std::string>(),
          program["structs"],
          [](MetalCache* cache, json j){
            auto s = readStruct(cache, j);
            return std::make_pair(s->name->name, s);
          }),
      readArrayIntoMap<std::string, StaticSizedArrayDefinitionT*>(
          cache,
          std::hash<std::string>(),
          std::equal_to<std::string>(),
          program["staticSizedArrays"],
          [](MetalCache* cache, json j){
            auto s = readStaticSizedArrayDefinition(cache, j);
            return std::make_pair(s->name->name, s);
          }),
      readArrayIntoMap<std::string, RuntimeSizedArrayDefinitionT*>(
          cache,
          std::hash<std::string>(),
          std::equal_to<std::string>(),
          program["runtimeSizedArrays"],
          [](MetalCache* cache, json j){
            auto s = readRuntimeSizedArrayDefinition(cache, j);
            return std::make_pair(s->name->name, s);
          }),
//      readArrayIntoMap<std::string, Prototype*>(
//          cache,
//          std::hash<std::string>(),
//          std::equal_to<std::string>(),
//          program["externFunctions"],
//          [](MetalCache* cache, json j){
//            auto f = readPrototype(cache, j);
//            return std::make_pair(f->name->name, f);
//          }),
      readArrayIntoMap<std::string, Function*>(
          cache,
          std::hash<std::string>(),
          std::equal_to<std::string>(),
          program["functions"],
          [](MetalCache* cache, json j){
            auto f = readFunction(cache, j);
            return std::make_pair(f->prototype->name->name, f);
          }),
      readArrayIntoMap<Kind*, Prototype*>(
          cache,
          AddressHasher<Kind*>(cache->addressNumberer),
          std::equal_to<Kind*>(),
          program["immDestructorsByKind"],
          readKindAndPrototypeEntry),
      readArrayIntoMap<std::string, Prototype*>(
          cache,
          std::hash<std::string>(),
          std::equal_to<std::string>(),
          program["exportNameToFunction"],
          [](MetalCache* cache, json entryJ){
            auto exportName = readString(cache, entryJ["exportName"]);
            auto prototype = readPrototype(cache, entryJ["prototype"]);
            return std::make_pair(exportName, prototype);
          }),
      readArrayIntoMap<std::string, Kind*>(
          cache,
          std::hash<std::string>(),
          std::equal_to<std::string>(),
          program["exportNameToKind"],
          [](MetalCache* cache, json entryJ){
            auto exportName = readString(cache, entryJ["exportName"]);
            auto kind = readKind(cache, entryJ["kind"]);
            return std::make_pair(exportName, kind);
          }),
      readArrayIntoMap<std::string, Prototype*>(
          cache,
          std::hash<std::string>(),
          std::equal_to<std::string>(),
          program["externNameToFunction"],
          [](MetalCache* cache, json entryJ){
            auto externName = readString(cache, entryJ["externName"]);
            auto prototype = readPrototype(cache, entryJ["prototype"]);
            return std::make_pair(externName, prototype);
          }),
      readArrayIntoMap<std::string, Kind*>(
          cache,
          std::hash<std::string>(),
          std::equal_to<std::string>(),
          program["externNameToKind"],
          [](MetalCache* cache, json entryJ){
            auto externName = readString(cache, entryJ["externName"]);
            auto kind = readKind(cache, entryJ["kind"]);
            return std::make_pair(externName, kind);
          }));
}

std::pair<PackageCoordinate*, Package*> readPackageCoordinateAndPackageEntry(MetalCache* cache, const json& edge) {
  assert(edge.is_object());
  assert(edge["__type"] == "Entry");
  return std::make_pair<PackageCoordinate*, Package*>(
      readPackageCoordinate(cache, edge["packageCoordinate"]),
      readPackage(cache, edge["package"]));
}

//Program* readProgram(MetalCache* cache, const json& program) {
//  assert(program.is_object());
//  assert(program["__type"] == "Program");
//  return new Program(
//      readArrayIntoMap<PackageCoordinate*, Package*, AddressHasher<PackageCoordinate*>, std::equal_to<PackageCoordinate*>>(
//          cache,
//          cache->addressNumberer->makeHasher<PackageCoordinate*>(),
//          std::equal_to<PackageCoordinate*>(),
//          program["packages"],
//          [](MetalCache* cache, json j){
//            return readPackageCoordinateAndPackageEntry(cache, j);
//          }));
//}
