#ifndef METAL_CACHE_H_
#define METAL_CACHE_H_

#include <unordered_map>

#include "metal/types.h"
#include "metal/ast.h"
#include "instructions.h"

template<typename K, typename V, typename H, typename E, typename F>
V makeIfNotPresent(std::unordered_map<K, V, H, E>* map, const K& key, F&& makeElement) {
  auto iter = map->find(key);
  if (iter == map->end()) {
    auto p = map->emplace(key, makeElement());
    iter = p.first;
  }
  return iter->second;
}

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

class MetalCache {
public:
  MetalCache() {
    intRef = getReference(Ownership::SHARE, Location::INLINE, innt);
    floatRef = getReference(Ownership::SHARE, Location::INLINE, flooat);
    boolRef = getReference(Ownership::SHARE, Location::INLINE, boool);
    strRef = getReference(Ownership::SHARE, Location::YONDER, str);
    neverRef = getReference(Ownership::SHARE, Location::INLINE, never);
  }

  Int* innt = new Int();
  Reference* intRef = nullptr;
  Bool* boool = new Bool();
  Reference* boolRef = nullptr;
  Float* flooat = new Float();
  Reference* floatRef = nullptr;
  Str* str = new Str();
  Reference* strRef = nullptr;
  Never* never = new Never();
  Reference* neverRef = nullptr;
  StructReferend* emptyTupleStruct = nullptr;
  Reference* emptyTupleStructRef = nullptr;

  std::unordered_map<Name*, StructReferend*> structReferends;
  std::unordered_map<Name*, InterfaceReferend*> interfaceReferends;
  std::unordered_map<std::string, Name*> names;

  // This is conceptually a map<[Reference*, Mutability], RawArrayT*>.
  std::unordered_map<Reference*, std::unordered_map<Mutability, RawArrayT*>> rawArrays;
  std::unordered_map<Name*, UnknownSizeArrayT*> unknownSizeArrays;
  std::unordered_map<Name*, KnownSizeArrayT*> knownSizeArrays;
  std::unordered_map<Referend*, std::unordered_map<Ownership, std::unordered_map<Location, Reference*>>> unconvertedReferences;
  std::unordered_map<Name*, std::unordered_map<Reference*, std::unordered_map<std::vector<Reference*>, Prototype*, HashRefVec, RefVecEquals>>> prototypes;
  std::unordered_map<int, std::unordered_map<std::string, VariableId*>> variableIds;
  std::unordered_map<VariableId*, std::unordered_map<Reference*, Local*>> locals;

  Reference* getReference(Ownership ownership, Location location, Referend* referend) {
    return makeIfNotPresent(
        &unconvertedReferences[referend][ownership],
        location,
        [&](){ return new Reference(ownership, location, referend); });
  }
};

#endif
