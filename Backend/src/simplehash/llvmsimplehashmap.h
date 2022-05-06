#ifndef SIMPLEHASH_LLVMSIMPLEHASHMAP_H
#define SIMPLEHASH_LLVMSIMPLEHASHMAP_H

#include <globalstate.h>
#include <function/expressions/expressions.h>
#include "cppsimplehashmap.h"

class LlvmSimpleHashMap {
private:
  GlobalState* globalState;
  LLVMTypeRef keyLT; // Equivalent to CppSimpleHashMap's K
  LLVMTypeRef valueLT; // Equivalent to CppSimpleHashMap's V
  LLVMTypeRef hasherLT; // Equivalent to CppSimpleHashMap's H
  LLVMTypeRef equatorLT; // Equivalent to CppSimpleHashMap's E
  LLVMTypeRef nodeStructLT; // Equivalent to CppSimpleHashMap's CppSimpleHashMapNode<K, V>
  LLVMTypeRef mapStructLT; // Equivalent to CppSimpleHashMap's CppSimpleHashMap<K, V, H, E>
  LLVMValueRef hasherLF;
  LLVMValueRef equatorLF;

public:
  LlvmSimpleHashMap(
      GlobalState* globalState,
      LLVMTypeRef keyLT,
      LLVMTypeRef valueLT,
      LLVMTypeRef hasherLT,
      LLVMTypeRef equatorLT,
      LLVMValueRef hasherLF,
      LLVMValueRef equatorLF) :
    globalState(globalState),
    keyLT(keyLT),
    valueLT(valueLT),
    hasherLT(hasherLT),
    equatorLT(equatorLT),
    hasherLF(hasherLF),
    equatorLF(equatorLF) {

    auto int8LT = LLVMInt8TypeInContext(globalState->context);
    auto voidPtrLT = LLVMPointerType(int8LT, 0);
    auto int64LT = LLVMInt64TypeInContext(globalState->context);
    auto int8PtrLT = LLVMPointerType(int8LT, 0);

    std::vector<LLVMTypeRef> nodeStructMembersLT = { keyLT, valueLT };
    nodeStructLT =
        LLVMStructTypeInContext(
            globalState->context, nodeStructMembersLT.data(), nodeStructMembersLT.size(), false);

    std::vector<LLVMTypeRef> mapStructMembersLT = {
        int64LT, // capacity
        int64LT, // size
        int8PtrLT, // presences
        LLVMPointerType(nodeStructLT, 0), // entries
        hasherLT, // hasher
        equatorLT // equator
    };
    mapStructLT =
        LLVMStructTypeInContext(
            globalState->context, mapStructMembersLT.data(), mapStructMembersLT.size(), false);
  }

  template<typename K, typename V, typename H, typename E>
  LLVMValueRef makeGlobalConstSimpleHashMap(
      const CppSimpleHashMap<K, V, H, E>& cppMap,
      std::function<std::tuple<LLVMValueRef, LLVMValueRef>(const K &, const V &)> entryMapper,
      const std::string& globalName,
      LLVMValueRef hasherLE,
      LLVMValueRef equatorLE
  ) {
    auto int8LT = LLVMInt8TypeInContext(globalState->context);

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
      nodesElementsLE.push_back(LLVMConstNamedStruct(nodeStructLT, nodeMembersLE.data(), nodeMembersLE.size()));
      presencesElementsLE.push_back(LLVMConstInt(int8LT, cppMap.presences[i], false));
    }

    std::string boolsGlobalName = globalName + "_isFilled";
    LLVMValueRef presencesGlobalLE =
        LLVMAddGlobal(globalState->mod, LLVMArrayType(int8LT, cppMap.capacity), boolsGlobalName.c_str());
    LLVMSetLinkage(presencesGlobalLE, LLVMExternalLinkage);
    LLVMSetInitializer(
        presencesGlobalLE, LLVMConstArray(int8LT, presencesElementsLE.data(), presencesElementsLE.size()));

    std::string nodesGlobalName = globalName + "_nodes";
    LLVMValueRef nodesGlobalLE =
        LLVMAddGlobal(globalState->mod, LLVMArrayType(nodeStructLT, cppMap.capacity), boolsGlobalName.c_str());
    LLVMSetLinkage(nodesGlobalLE, LLVMExternalLinkage);
    LLVMSetInitializer(
        nodesGlobalLE, LLVMConstArray(nodeStructLT, nodesElementsLE.data(), nodesElementsLE.size()));

    std::vector<LLVMValueRef> mapMembersLE = {
        constI64LE(globalState, cppMap.capacity),
        constI64LE(globalState, cppMap.size),
        presencesGlobalLE,
        nodesGlobalLE,
        hasherLE,
        equatorLE
    };
    LLVMValueRef mapLE =
        LLVMAddGlobal(globalState->mod, mapStructLT, boolsGlobalName.c_str());
    LLVMSetLinkage(mapLE, LLVMExternalLinkage);
    LLVMSetInitializer(
        mapLE,
        LLVMConstNamedStruct(mapStructLT, mapMembersLE.data(), mapMembersLE.size()));
    return mapLE;
  }

  LLVMValueRef buildGetAtIndex(LLVMValueRef key) {
    assert(false);
  }

  // Returns -1 if not found.
  LLVMValueRef buildFindIndexOf(LLVMValueRef key) {
    assert(false);

//    // Returns -1 if not found.
//    int64_t findIndexOf(K key) {
//      if (!entries) {
//        return -1;
//      }
//      int64_t startIndex = hasher(key) % capacity;
//      for (int64_t i = 0; i < capacity; i++) {
//        int64_t indexInTable = (startIndex + i) % capacity;
//        if (equator(entries[indexInTable], key)) {
//          return indexInTable;
//        }
//        if (!presences[indexInTable]) {
//          return -1;
//        }
//      }
//      exit(1); // We shouldnt get here, it would mean the table is full.
//    }
  }
};

#endif //SIMPLEHASH_LLVMSIMPLEHASHMAP_H
