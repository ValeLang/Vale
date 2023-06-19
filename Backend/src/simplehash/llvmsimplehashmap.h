#ifndef SIMPLEHASH_LLVMSIMPLEHASHMAP_H
#define SIMPLEHASH_LLVMSIMPLEHASHMAP_H

#include <globalstate.h>
#include <function/expressions/expressions.h>
#include <function/expressions/shared/elements.h>
#include <utils/definefunction.h>
#include <utils/branch.h>
#include <utils/call.h>
#include <utils/llvm.h>
#include "cppsimplehashmap.h"

enum NodeMember {
  KEY = 0,
  VALUE = 1
};
constexpr int NodeNumMembers = 2;

enum MapMember {
  CAPACITY = 0,
  SIZE = 1,
  PRESENCES = 2,
  ENTRIES = 3,
  HASHER = 4,
  EQUATOR = 5
};
constexpr int MapNumMembers = 6;

class LlvmSimpleHashMap {
public:
  static LlvmSimpleHashMap create(
      GlobalState* globalState,
      const std::string& mapTypeName,
      LLVMTypeRef keyLT,
      LLVMTypeRef valueLT,
      LLVMTypeRef hasherLT,
      LLVMTypeRef equatorLT,
      RawFuncPtrLE hasherLF,
      RawFuncPtrLE equatorLF) {
    auto int8LT = LLVMInt8TypeInContext(globalState->context);
    auto voidPtrLT = LLVMPointerType(int8LT, 0);
    auto int64LT = LLVMInt64TypeInContext(globalState->context);
    auto int8PtrLT = LLVMPointerType(int8LT, 0);

    auto nodeStructLT =
        StructLT<NodeNumMembers, NodeMember>(
            globalState->context, mapTypeName + "_Node", { keyLT, valueLT });

    std::array<LLVMTypeRef, MapNumMembers> mapStructMembersLT = {
        int64LT, // capacity
        int64LT, // size
        int8PtrLT, // presences
        LLVMPointerType(nodeStructLT.getStructLT(), 0), // entries
        hasherLT, // hasher
        equatorLT // equator
    };
    auto mapStructLT =
        StructLT<MapNumMembers, MapMember>(
            globalState->context, mapTypeName, mapStructMembersLT);

    auto findIndexOfLF =
        addRawFunction(
            globalState->mod, mapTypeName + "_findIndexOf", int64LT,
            {LLVMPointerType(mapStructLT.getStructLT(), 0), keyLT});

    return LlvmSimpleHashMap(
        globalState,
        mapTypeName,
        keyLT,
        valueLT,
        hasherLT,
        equatorLT,
        nodeStructLT,
        mapStructLT,
        hasherLF,
        equatorLF,
        findIndexOfLF);
  }

  template<typename K, typename V, typename H, typename E>
  void setInitializerForGlobalConstSimpleHashMap(
      const CppSimpleHashMap<K, V, H, E>& cppMap,
      std::function<std::tuple<LLVMValueRef, LLVMValueRef>(const K &, const V &)> entryMapper,
      LLVMValueRef mapGlobalLE,
      const std::string& globalName,
      LLVMValueRef hasherLE,
      LLVMValueRef equatorLE
  ) {
    auto int8LT = LLVMInt8TypeInContext(globalState->context);
    auto int8PtrLT = LLVMPointerType(int8LT, 0);

    std::vector<LLVMValueRef> presencesElementsLE;
    std::vector<LLVMValueRef> nodesElementsLE;
    for (int i = 0; i < cppMap.capacity; i++) {
      std::vector<LLVMValueRef> nodeMembersLE;
      if (cppMap.presences[i]) {
        LLVMValueRef keyLE = nullptr, valueLE = nullptr;
        std::tie(keyLE, valueLE) = entryMapper(cppMap.entries[i].key, cppMap.entries[i].value);
        nodeMembersLE.push_back(keyLE);
        nodeMembersLE.push_back(valueLE);
      } else {
        nodeMembersLE.push_back(LLVMGetUndef(keyLT));
        nodeMembersLE.push_back(LLVMGetUndef(valueLT));
      }
      nodesElementsLE.push_back(LLVMConstNamedStruct(nodeStructLT.getStructLT(), nodeMembersLE.data(), nodeMembersLE.size()));
      presencesElementsLE.push_back(LLVMConstInt(int8LT, cppMap.presences[i], false));
    }

    std::string presencesGlobalName = globalName + "_presences";
    LLVMValueRef presencesGlobalLE =
        LLVMAddGlobal(globalState->mod, LLVMArrayType(int8LT, cppMap.capacity), presencesGlobalName.c_str());
    LLVMSetLinkage(presencesGlobalLE, LLVMExternalLinkage);
    LLVMSetInitializer(
        presencesGlobalLE, LLVMConstArray(int8LT, presencesElementsLE.data(), presencesElementsLE.size()));

    std::string nodesGlobalName = globalName + "_nodes";
    LLVMValueRef nodesGlobalLE =
        LLVMAddGlobal(globalState->mod, LLVMArrayType(nodeStructLT.getStructLT(), cppMap.capacity), presencesGlobalName.c_str());
    LLVMSetLinkage(nodesGlobalLE, LLVMExternalLinkage);
    LLVMSetInitializer(
        nodesGlobalLE, LLVMConstArray(nodeStructLT.getStructLT(), nodesElementsLE.data(), nodesElementsLE.size()));

    std::vector<LLVMValueRef> presencesIndices = {constI64LE(globalState->context, 0), constI64LE(globalState->context, 0) };
    auto presencesFirstPtrLE = LLVMConstGEP2(LLVMArrayType(int8LT, cppMap.capacity), presencesGlobalLE, presencesIndices.data(), presencesIndices.size());
    assert(LLVMTypeOf(presencesFirstPtrLE) == int8PtrLT);

    std::vector<LLVMValueRef> nodesIndices = {constI64LE(globalState->context, 0), constI64LE(globalState->context, 0) };
    auto nodesFirstPtrLE = LLVMConstGEP2(LLVMArrayType(nodeStructLT.getStructLT(), cppMap.capacity), nodesGlobalLE, nodesIndices.data(), nodesIndices.size());

    std::vector<LLVMValueRef> mapMembersLE = {
        constI64LE(globalState, cppMap.capacity),
        constI64LE(globalState, cppMap.size),
        presencesFirstPtrLE,
        nodesFirstPtrLE,
        hasherLE,
        equatorLE
    };
    LLVMSetInitializer(
        mapGlobalLE,
        LLVMConstNamedStruct(mapStructLT.getStructLT(), mapMembersLE.data(), mapMembersLE.size()));
  }

  // Returns -1 if not found.
  LLVMValueRef buildFindIndexOf(LLVMBuilderRef builder, LLVMValueRef mapPtrLE, LLVMValueRef keyLE) {
    return findIndexOfLF.call(builder, {mapPtrLE, keyLE}, "index");
  }

  // This does no checking on whether something's actually there, and could return garbage if
  // given a wrong index. Only use this immediately after buildFindIndexOf and a check that its
  // index isnt -1.
  LLVMValueRef buildGetAtIndex(
      LLVMBuilderRef builder,
      LLVMValueRef mapPtrLE,
      LLVMValueRef indexInTableLE) {
    auto entriesPtrLE = mapStructLT.getMember(builder, mapPtrLE, MapMember::ENTRIES);
    auto entryLE = subscript(builder, nodeStructLT.getStructLT(), entriesPtrLE, indexInTableLE, "entry");
    assert(LLVMTypeOf(entryLE) == nodeStructLT.getStructLT());
    return entryLE;
  }

  // This does no checking on whether something's actually there, and could return garbage if
  // given a wrong index. Only use this immediately after buildFindIndexOf and a check that its
  // index isnt -1.
  LLVMValueRef buildGetValueAtIndex(
      LLVMBuilderRef builder,
      LLVMValueRef mapPtrLE,
      LLVMValueRef indexInTableLE) {
    auto entryLE = buildGetAtIndex(builder, mapPtrLE, indexInTableLE);
    return LLVMBuildExtractValue(builder, entryLE, NodeMember::VALUE, "value");
  }

  LLVMTypeRef getMapType() { return mapStructLT.getStructLT(); }
  LLVMTypeRef getNodeType() { return nodeStructLT.getStructLT(); }

private:
  LlvmSimpleHashMap(
      GlobalState* globalState,
      const std::string& mapTypeName,
      LLVMTypeRef keyLT,
      LLVMTypeRef valueLT,
      LLVMTypeRef hasherLT,
      LLVMTypeRef equatorLT,
      StructLT<NodeNumMembers, NodeMember> nodeStructLT,
      StructLT<MapNumMembers, MapMember> mapStructLT,
      RawFuncPtrLE hasherLF,
      RawFuncPtrLE equatorLF,
      RawFuncPtrLE findIndexOfLF) :
    globalState(globalState),
    mapTypeName(mapTypeName),
    keyLT(keyLT),
    valueLT(valueLT),
    hasherLT(hasherLT),
    equatorLT(equatorLT),
    nodeStructLT(std::move(nodeStructLT)),
    mapStructLT(std::move(mapStructLT)),
    hasherLF(hasherLF),
    equatorLF(equatorLF),
    findIndexOfLF(findIndexOfLF) {

    defineFindIndexOf();
  }

  void defineFindIndexOf() {
    auto voidLT = LLVMVoidTypeInContext(globalState->context);
    auto int1LT = LLVMInt1TypeInContext(globalState->context);
    auto int8LT = LLVMInt8TypeInContext(globalState->context);
    auto int32LT = LLVMInt32TypeInContext(globalState->context);
    auto int64LT = LLVMInt64TypeInContext(globalState->context);
    defineRawFunctionBody(
        globalState->context, findIndexOfLF.ptrLE, int64LT, mapTypeName + "_findIndexOf",
        [this, int1LT, int8LT, int32LT, int64LT, voidLT](FunctionState* functionState, LLVMBuilderRef builder){
          auto mapPtrLE = LLVMGetParam(functionState->containingFuncL, 0);
          auto keyLE = LLVMGetParam(functionState->containingFuncL, 1);
          // if (!entries) {
          //   return -1;
          // }
          auto entriesPtrLE = mapStructLT.getMember(builder, mapPtrLE, MapMember::ENTRIES);
          auto entriesNullLE = ptrIsNull(globalState->context, builder, entriesPtrLE);
          buildIfReturn(
              globalState, functionState->containingFuncL, builder, entriesNullLE,
              [this](LLVMBuilderRef builder){
                return constI64LE(globalState, -1);
              });
          // int64_t startIndex = hasher(key) % capacity;
          auto capacityLE = mapStructLT.getMember(builder, mapPtrLE, MapMember::CAPACITY);
          auto hasherPtrLE = mapStructLT.getMemberPtr(builder, mapPtrLE, MapMember::HASHER);
          auto equatorPtrLE = mapStructLT.getMemberPtr(builder, mapPtrLE, MapMember::EQUATOR);
          auto presencesPtrLE = mapStructLT.getMember(builder, mapPtrLE, MapMember::PRESENCES);
          auto hashLE = hasherLF.call(builder, {hasherPtrLE, keyLE}, "hash");
          auto startIndexLE = LLVMBuildURem(builder, hashLE, capacityLE, "startIndex");
          // for (int64_t i = 0; i < capacity; i++) {
          auto capacityI32LE = LLVMBuildTrunc(builder, capacityLE, int32LT, "capacityI32");
          intRangeLoop(
              globalState, functionState, builder, capacityI32LE,
              [this, functionState, int1LT, int8LT, int64LT, startIndexLE, entriesPtrLE, capacityLE, equatorPtrLE, presencesPtrLE, keyLE](
                  LLVMValueRef indexI32LE, LLVMBuilderRef builder){
                auto indexLE = LLVMBuildZExt(builder, indexI32LE, int64LT, "index");
                // int64_t indexInTable = (startIndex + i) % capacity;
                auto startIndexPlusILE = LLVMBuildAdd(builder, startIndexLE, indexLE, "");
                auto indexInTableLE = LLVMBuildURem(builder, startIndexPlusILE, capacityLE, "indexInTable");
                // if (!presences[indexInTable]) {
                auto presenceI8LE = subscript(builder, int8LT, presencesPtrLE, indexInTableLE, "presenceI8");
                auto presenceLE = LLVMBuildTrunc(builder, presenceI8LE, int1LT, "presence");
                auto notPresent = LLVMBuildNot(builder, presenceLE, "notPresent");
                buildIfReturn(
                    globalState, functionState->containingFuncL, builder, notPresent,
                    [this](LLVMBuilderRef builder){
                      // return -1;
                      return constI64LE(globalState, -1);
                    });
                // if (equator(entries[indexInTable], key)) {
                auto entryPtrLE = subscriptForPtr(builder, nodeStructLT.getStructLT(), entriesPtrLE, indexInTableLE, "entry");
                auto entryKeyLE = nodeStructLT.getMember(builder, entryPtrLE, NodeMember::KEY, "entryKey");
                auto equalI8LE = equatorLF.call(builder, {equatorPtrLE, entryKeyLE, keyLE}, "equalI8");
                auto equalLE = LLVMBuildTrunc(builder, equalI8LE, int1LT, "equal");
                buildIfReturn(
                    globalState, functionState->containingFuncL, builder, equalLE,
                    [this, indexInTableLE](LLVMBuilderRef builder){
                      // return indexInTable;
                      return indexInTableLE;
                    });
              });
          buildPrintToStderr(globalState, builder, "Unreachable!\n");
          // exit(1); // We shouldnt get here, it would mean the table is full.
          globalState->externs->exit.call(builder, {constI64LE(globalState, -1)}, "");
          LLVMBuildUnreachable(builder);
        });
  }

  GlobalState* globalState;
  std::string mapTypeName;
  LLVMTypeRef keyLT; // Equivalent to CppSimpleHashMap's K
  LLVMTypeRef valueLT; // Equivalent to CppSimpleHashMap's V
  LLVMTypeRef hasherLT; // Equivalent to CppSimpleHashMap's H
  LLVMTypeRef equatorLT; // Equivalent to CppSimpleHashMap's E
  StructLT<NodeNumMembers, NodeMember> nodeStructLT; // Equivalent to CppSimpleHashMap's CppSimpleHashMapNode<K, V>
  StructLT<MapNumMembers, MapMember> mapStructLT; // Equivalent to CppSimpleHashMap's CppSimpleHashMap<K, V, H, E>
  RawFuncPtrLE hasherLF;
  RawFuncPtrLE equatorLF;

  RawFuncPtrLE findIndexOfLF;
};

#endif //SIMPLEHASH_LLVMSIMPLEHASHMAP_H
