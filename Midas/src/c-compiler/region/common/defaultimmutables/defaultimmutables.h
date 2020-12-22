#ifndef REGION_COMMON_DEFAULTIMMUTABLES_DEFAULTIMMUTABLES_H_
#define REGION_COMMON_DEFAULTIMMUTABLES_DEFAULTIMMUTABLES_H_

#include <llvm-c/Types.h>
#include <globalstate.h>
#include <iostream>
#include <region/common/primitives.h>
#include <function/expressions/shared/afl.h>
#include <function/function.h>
#include <region/common/defaultlayout/structs.h>

ControlBlock makeImmControlBlock(GlobalState* globalState);

class DefaultImmutables {
public:
  DefaultImmutables(
      GlobalState* globalState_,
      ReferendStructs* wrappedStructs_);

  void discard(
      AreaAndFileAndLine from,
      GlobalState* globalState,
      FunctionState* functionState,
      BlockState* blockState,
      LLVMBuilderRef builder,
      Reference* sourceMT,
      Ref sourceRef);

  LLVMTypeRef translateType(GlobalState* globalState, Reference* referenceM);

  LLVMTypeRef getControlBlockStruct(Referend* referend);

  ControlBlock* getControlBlock(Referend* referend);


  Ref loadMember(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* structRefMT,
      Ref structRef,
      int memberIndex,
      Reference* expectedMemberType,
      Reference* targetType,
      const std::string& memberName);

  void checkValidReference(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      IReferendStructsSource* referendStructs,
      Reference* refM,
      LLVMValueRef refLE);

  std::string getRefNameC(Reference* refMT);
  void generateStructDefsC(std::unordered_map<std::string, std::string>* cByExportedName, StructDefinition* refMT);
  void generateInterfaceDefsC(std::unordered_map<std::string, std::string>* cByExportedName, InterfaceDefinition* refMT);

  LLVMValueRef externalify(FunctionState *functionState, LLVMBuilderRef builder, Reference *refMT, Ref ref);
  Ref internalify(FunctionState *functionState, LLVMBuilderRef builder, Reference *refMT, LLVMValueRef ref);

  LLVMTypeRef getExternalType(
      Reference* refMT);

private:
  GlobalState* globalState;

  ReferendStructs* referendStructs;

  DefaultPrimitives primitives;

  // Contains all the structs for immutables that we'll be sending over the C boundary.
  // For example:
  // - str would map to struct { uint64_t len; char bytes[0]; }
  // - ImmArray<Vec2> would map to struct { uint64_t len; Vec2 entries[0]; }
  // - Vec2 would map to struct { int32_t x; int32_t y; }
  // We don't need to store a corresponding map for mutables because their representations
  // are the same in the vale world and C world, we can generate that C code on the fly.
  std::unordered_map<Referend*, LLVMTypeRef> externalStructLByReferend;
};

#endif