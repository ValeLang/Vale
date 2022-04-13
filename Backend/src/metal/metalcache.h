#ifndef METAL_CACHE_H_
#define METAL_CACHE_H_

#include <unordered_map>

#include "types.h"
#include "ast.h"
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
      structKinds(0, addressNumberer->makeHasher<Name*>()),
      interfaceKinds(0, addressNumberer->makeHasher<Name*>()),
      names(0, addressNumberer->makeHasher<PackageCoordinate*>()),
      ints(0, addressNumberer->makeHasher<RegionId*>()),
      bools(0, addressNumberer->makeHasher<RegionId*>()),
      strs(0, addressNumberer->makeHasher<RegionId*>()),
      floats(0, addressNumberer->makeHasher<RegionId*>()),
      nevers(0, addressNumberer->makeHasher<RegionId*>()),
      voids(0, addressNumberer->makeHasher<RegionId*>()),
      runtimeSizedArrays(0, addressNumberer->makeHasher<Name*>()),
      staticSizedArrays(0, addressNumberer->makeHasher<Name*>()),
      unconvertedReferences(0, addressNumberer->makeHasher<Kind*>()),
      prototypes(0, addressNumberer->makeHasher<Name*>()),
      interfaceMethods(0, addressNumberer->makeHasher<Prototype*>()),
      locals(0, addressNumberer->makeHasher<VariableId*>()) {

    builtinPackageCoord = getPackageCoordinate(BUILTIN_PROJECT_NAME, {});
    rcImmRegionId = getRegionId(builtinPackageCoord, "rcimm");
    linearRegionId = getRegionId(builtinPackageCoord, "linear");
    mutRegionId = getRegionId(builtinPackageCoord, "mut");

    i32 = getInt(rcImmRegionId, 32);
    i32Ref = getReference(Ownership::SHARE, Location::INLINE, i32);
    i64 = getInt(rcImmRegionId, 64);
    i64Ref = getReference(Ownership::SHARE, Location::INLINE, i64);
    boool = getBool(rcImmRegionId);
    boolRef = getReference(Ownership::SHARE, Location::INLINE, boool);
    flooat = getFloat(rcImmRegionId);
    floatRef = getReference(Ownership::SHARE, Location::INLINE, flooat);
    str = getStr(rcImmRegionId);
    strRef = getReference(Ownership::SHARE, Location::YONDER, str);
    never = getNever(rcImmRegionId);
    neverRef = getReference(Ownership::SHARE, Location::INLINE, never);
    vooid = getVoid(rcImmRegionId);
    voidRef = getReference(Ownership::SHARE, Location::INLINE, vooid);
//    regionKind = getStructKind(getName("__Region"));
  }

  PackageCoordinate* getPackageCoordinate(const std::string& projectName, const std::vector<std::string>& packageSteps) {


    return makeIfNotPresent(
        &packageCoords[projectName],
        packageSteps,
        [&](){ return new PackageCoordinate{projectName, packageSteps}; });
  }

  Int* getInt(RegionId* regionId, int bits) {
    return makeIfNotPresent(
        &ints[regionId],
        bits,
        [&](){ return new Int(regionId, bits); });
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

  Void* getVoid(RegionId* regionId) {
    return makeIfNotPresent(
        &voids,
        regionId,
        [&](){ return new Void(regionId); });
  }

  Never* getNever(RegionId* regionId) {
    return makeIfNotPresent(
        &nevers,
        regionId,
        [&](){ return new Never(regionId); });
  }

  StructKind* getStructKind(Name* structName) {
    return makeIfNotPresent(
        &structKinds,
        structName,
        [&]() { return new StructKind(structName); });
  }

  InterfaceKind* getInterfaceKind(Name* structName) {
    return makeIfNotPresent(
        &interfaceKinds,
        structName,
        [&]() { return new InterfaceKind(structName); });
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

  Reference* getReference(Ownership ownership, Location location, Kind* kind) {
    return makeIfNotPresent<Location, Reference*>(
        &unconvertedReferences[kind][ownership],
        location,
        [&](){ return new Reference(ownership, location, kind); });
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
  std::unordered_map<Name*, StructKind*, AddressHasher<Name*>> structKinds;
  std::unordered_map<Name*, InterfaceKind*, AddressHasher<Name*>> interfaceKinds;
  std::unordered_map<PackageCoordinate*, std::unordered_map<std::string, Name*>, AddressHasher<PackageCoordinate*>> names;

  std::unordered_map<std::string, std::unordered_map<std::vector<std::string>, PackageCoordinate*, PackageCoordinate::StringVectorHasher, PackageCoordinate::StringVectorEquator>> packageCoords;
  std::unordered_map<RegionId*, std::unordered_map<int, Int*>, AddressHasher<RegionId*>> ints;
  std::unordered_map<RegionId*, Bool*, AddressHasher<RegionId*>> bools;
  std::unordered_map<RegionId*, Str*, AddressHasher<RegionId*>> strs;
  std::unordered_map<RegionId*, Float*, AddressHasher<RegionId*>> floats;
  std::unordered_map<RegionId*, Void*, AddressHasher<RegionId*>> voids;
  std::unordered_map<RegionId*, Never*, AddressHasher<RegionId*>> nevers;

  std::unordered_map<Name*, RuntimeSizedArrayT*, AddressHasher<Name*>> runtimeSizedArrays;
  std::unordered_map<Name*, StaticSizedArrayT*, AddressHasher<Name*>> staticSizedArrays;
  std::unordered_map<
      Kind*,
      std::unordered_map<
          Ownership,
          std::unordered_map<
              Location,
              Reference*>>,
      AddressHasher<Kind*>> unconvertedReferences;


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
  RegionId* mutRegionId = nullptr;

//  I8* i8 = new I8();
//  Reference* i8Ref = nullptr;
  PackageCoordinate* builtinPackageCoord = nullptr;
  Int* i32 = nullptr;
  Reference* i32Ref = nullptr;
  Int* i64 = nullptr;
  Reference* i64Ref = nullptr;
  Bool* boool = nullptr;
  Reference* boolRef = nullptr;
  Float* flooat = nullptr;
  Reference* floatRef = nullptr;
  Str* str = nullptr;
  Reference* strRef = nullptr;
  Never* never = nullptr;
  Reference* neverRef = nullptr;
  Void* vooid = nullptr;
  Reference* voidRef = nullptr;
  // This is a central kind that holds a region's data.
  // These will hold for example the bump pointer for an arena region,
  // or a free list pointer for HGM.
  // We hand these in to methods like allocate, deallocate, etc.
  // Right now we just use it to hold the bump pointer for linear regions.
  // Otherwise, for now, we're just handing in Nevers.
//  StructKind* regionKind = nullptr;
};


#endif
