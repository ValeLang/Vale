#ifndef METAL_CACHE_H_
#define METAL_CACHE_H_

#include <unordered_map>

#include "metal/types.h"
#include "metal/ast.h"
#include "instructions.h"

namespace std {
    template<>
    struct hash<Location> {
        inline size_t operator()(Location location) const {
            return (size_t)location;
        }
    };
    template<>
    struct hash<Ownership> {
        inline size_t operator()(Ownership ownership) const {
            return (size_t)ownership;
        }
    };
    template<>
    struct hash<Mutability> {
        inline size_t operator()(Mutability mutability) const {
            return (size_t)mutability;
        }
    };
}

template<typename K, typename V, typename H, typename E, typename F>
V& makeIfNotPresent(std::unordered_map<K, V, H, E>* map, const K& key, F&& makeElement) {
  auto iter = map->find(key);
  if (iter == map->end()) {
    auto p = map->emplace(key, makeElement());
    iter = p.first;
  }
  return iter->second;
}

struct HashRefVec {
  AddressHasher<Reference*> hasher;
  HashRefVec(AddressHasher<Reference*> hasher_) : hasher(hasher_) {}

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
  explicit MetalCache(AddressNumberer* addressNumberer_) :
      addressNumberer(addressNumberer_),
      structReferends(0, addressNumberer->makeHasher<Name*>()),
      interfaceReferends(0, addressNumberer->makeHasher<Name*>()),
      ints(0, addressNumberer->makeHasher<RegionId*>()),
      bools(0, addressNumberer->makeHasher<RegionId*>()),
      strs(0, addressNumberer->makeHasher<RegionId*>()),
      floats(0, addressNumberer->makeHasher<RegionId*>()),
      nevers(0, addressNumberer->makeHasher<RegionId*>()),
      rawArrays(0, addressNumberer->makeHasher<Reference*>()),
      runtimeSizedArrays(0, addressNumberer->makeHasher<Name*>()),
      staticSizedArrays(0, addressNumberer->makeHasher<Name*>()),
      unconvertedReferences(0, addressNumberer->makeHasher<Referend*>()),
      prototypes(0, addressNumberer->makeHasher<Name*>()),
      interfaceMethods(0, addressNumberer->makeHasher<Prototype*>()),
      locals(0, addressNumberer->makeHasher<VariableId*>()) {

    builtinPackageCoord = getPackageCoordinate("", {});
    rcImmRegionId = getRegionId(builtinPackageCoord, "rcimm");
    linearRegionId = getRegionId(builtinPackageCoord, "linear");
    unsafeRegionId = getRegionId(builtinPackageCoord, "unsafe");
    assistRegionId = getRegionId(builtinPackageCoord, "assist");
    naiveRcRegionId = getRegionId(builtinPackageCoord, "naiverc");
    resilientV3RegionId = getRegionId(builtinPackageCoord, "resilientv3");
    resilientV4RegionId = getRegionId(builtinPackageCoord, "resilientv4");

    innt = getInt(rcImmRegionId);
    intRef = getReference(Ownership::SHARE, Location::INLINE, innt);
    boool = getBool(rcImmRegionId);
    boolRef = getReference(Ownership::SHARE, Location::INLINE, boool);
    flooat = getFloat(rcImmRegionId);
    floatRef = getReference(Ownership::SHARE, Location::INLINE, flooat);
    str = getStr(rcImmRegionId);
    strRef = getReference(Ownership::SHARE, Location::YONDER, str);
    never = getNever(rcImmRegionId);
    neverRef = getReference(Ownership::SHARE, Location::INLINE, never);
//    regionReferend = getStructReferend(getName("__Region"));
  }

  PackageCoordinate* getPackageCoordinate(const std::string& projectName, const std::vector<std::string>& packageSteps) {


    return makeIfNotPresent(
        &packageCoords[projectName],
        packageSteps,
        [&](){ return new PackageCoordinate{projectName, packageSteps}; });
  }

  Int* getInt(RegionId* regionId) {
    return makeIfNotPresent(
        &ints,
        regionId,
        [&](){ return new Int(regionId); });
  }

  Bool* getBool(RegionId* regionId) {
    return makeIfNotPresent(
        &bools,
        regionId,
        [&](){ return new Bool(regionId); });
  }

  Str* getStr(RegionId* regionId) {
    return makeIfNotPresent(
        &strs,
        regionId,
        [&](){ return new Str(regionId); });
  }

  Float* getFloat(RegionId* regionId) {
    return makeIfNotPresent(
        &floats,
        regionId,
        [&](){ return new Float(regionId); });
  }

  Never* getNever(RegionId* regionId) {
    return makeIfNotPresent(
        &nevers,
        regionId,
        [&](){ return new Never(regionId); });
  }

  StructReferend* getStructReferend(Name* structName) {
    return makeIfNotPresent(
        &structReferends,
        structName,
        [&]() { return new StructReferend(structName); });
  }

  InterfaceReferend* getInterfaceReferend(Name* structName) {
    return makeIfNotPresent(
        &interfaceReferends,
        structName,
        [&]() { return new InterfaceReferend(structName); });
  }

  RuntimeSizedArrayT* getRuntimeSizedArray(Name* name) {
    return makeIfNotPresent(
        &runtimeSizedArrays,
        name,
        [&](){ return new RuntimeSizedArrayT(name); });
  }

  StaticSizedArrayT* getStaticSizedArray(Name* name) {
    return makeIfNotPresent(
        &staticSizedArrays,
        name,
        [&](){ return new StaticSizedArrayT(name); });
  }

  Name* getName(PackageCoordinate* packageCoordinate, std::string nameStr) {
    return makeIfNotPresent(
        &names[packageCoordinate],
        nameStr,
        [&](){ return new Name(packageCoordinate, nameStr); });
  }

  RegionId* getRegionId(PackageCoordinate* packageCoordinate, std::string nameStr) {
    return makeIfNotPresent(
        &regionIds,
        nameStr,
        [&](){ return new RegionId(packageCoordinate, nameStr); });
  }

  Reference* getReference(Ownership ownership, Location location, Referend* referend) {
    return makeIfNotPresent<Location, Reference*>(
        &unconvertedReferences[referend][ownership],
        location,
        [&](){ return new Reference(ownership, location, referend); });
  }

  Prototype* getPrototype(Name* name, Reference* returnType, std::vector<Reference*> paramTypes) {
    return makeIfNotPresent(
        &makeIfNotPresent(
            &makeIfNotPresent(
                &prototypes,
                name,
                [&](){ return PrototypeByParamListByReturnTypeMap(0, AddressHasher<Reference*>(addressNumberer)); }),
            returnType,
            [&](){ return PrototypeByParamListMap(0, HashRefVec(addressNumberer)); }),
        paramTypes,
        [&](){ return new Prototype(name, paramTypes, returnType); });
  }

  InterfaceMethod* getInterfaceMethod(Prototype* prototype, int virtualParamIndex) {
    return makeIfNotPresent(
        &interfaceMethods[prototype],
        virtualParamIndex,
        [&](){ return new InterfaceMethod(prototype, virtualParamIndex); });
  }

  AddressNumberer* addressNumberer;

  std::unordered_map<std::string, RegionId*> regionIds;
  std::unordered_map<Name*, StructReferend*, AddressHasher<Name*>> structReferends;
  std::unordered_map<Name*, InterfaceReferend*, AddressHasher<Name*>> interfaceReferends;
  std::unordered_map<PackageCoordinate*, std::unordered_map<std::string, Name*>, AddressHasher<PackageCoordinate*>> names;

  std::unordered_map<std::string, std::unordered_map<std::vector<std::string>, PackageCoordinate*, PackageCoordinate::StringVectorHasher, PackageCoordinate::StringVectorEquator>> packageCoords;
  std::unordered_map<RegionId*, Int*, AddressHasher<RegionId*>> ints;
  std::unordered_map<RegionId*, Bool*, AddressHasher<RegionId*>> bools;
  std::unordered_map<RegionId*, Str*, AddressHasher<RegionId*>> strs;
  std::unordered_map<RegionId*, Float*, AddressHasher<RegionId*>> floats;
  std::unordered_map<RegionId*, Never*, AddressHasher<RegionId*>> nevers;

  // This is conceptually a map<[Reference*, Mutability], RawArrayT*>.
  std::unordered_map<
      Reference*,
      std::unordered_map<
          RegionId*,
          std::unordered_map<
              Mutability,
              RawArrayT*>,
          AddressHasher<RegionId*>>,
      AddressHasher<Reference*>> rawArrays;
  std::unordered_map<Name*, RuntimeSizedArrayT*, AddressHasher<Name*>> runtimeSizedArrays;
  std::unordered_map<Name*, StaticSizedArrayT*, AddressHasher<Name*>> staticSizedArrays;
  std::unordered_map<
      Referend*,
      std::unordered_map<
          Ownership,
          std::unordered_map<
              Location,
              Reference*>>,
      AddressHasher<Referend*>> unconvertedReferences;


  using PrototypeByParamListMap =
      std::unordered_map<std::vector<Reference*>, Prototype*, HashRefVec, RefVecEquals>;
  using PrototypeByParamListByReturnTypeMap =
      std::unordered_map<Reference*, PrototypeByParamListMap, AddressHasher<Reference*>>;
  using PrototypeByParamListByReturnTypeByNameMap =
      std::unordered_map<Name*, PrototypeByParamListByReturnTypeMap, AddressHasher<Name*>>;
  PrototypeByParamListByReturnTypeByNameMap prototypes;

  std::unordered_map<Prototype*, std::unordered_map<int, InterfaceMethod*>, AddressHasher<Prototype*>> interfaceMethods;

  std::unordered_map<int, std::unordered_map<std::string, VariableId*>> variableIds;
  using LocalByKeepAliveMap = std::unordered_map<bool, Local*>;
  using LocalByKeepAliveByReferenceMap = std::unordered_map<Reference*, LocalByKeepAliveMap, AddressHasher<Reference*>>;
  using LocalByKeepAliveByReferenceByVariableIdMap = std::unordered_map<VariableId*, LocalByKeepAliveByReferenceMap, AddressHasher<VariableId*>>;
  LocalByKeepAliveByReferenceByVariableIdMap locals;

  RegionId* rcImmRegionId = nullptr;
  RegionId* linearRegionId = nullptr;
  RegionId* unsafeRegionId = nullptr;
  RegionId* naiveRcRegionId = nullptr;
  RegionId* resilientV3RegionId = nullptr;
  RegionId* resilientV4RegionId = nullptr;
  RegionId* assistRegionId = nullptr;
  // This is temporary, until we can get valestrom to properly fill in coords' regions
  RegionId* mutRegionId = nullptr;

//  I8* i8 = new I8();
//  Reference* i8Ref = nullptr;
  PackageCoordinate* builtinPackageCoord = nullptr;
  Int* innt = nullptr;
  Reference* intRef = nullptr;
  Bool* boool = nullptr;
  Reference* boolRef = nullptr;
  Float* flooat = nullptr;
  Reference* floatRef = nullptr;
  Str* str = nullptr;
  Reference* strRef = nullptr;
  Never* never = nullptr;
  Reference* neverRef = nullptr;
  StructReferend* emptyTupleStruct = nullptr;
  Reference* emptyTupleStructRef = nullptr;
  // This is a central referend that holds a region's data.
  // These will hold for example the bump pointer for an arena region,
  // or a free list pointer for HGM.
  // We hand these in to methods like allocate, deallocate, etc.
  // Right now we just use it to hold the bump pointer for linear regions.
  // Otherwise, for now, we're just handing in Nevers.
//  StructReferend* regionReferend = nullptr;
};


#endif
