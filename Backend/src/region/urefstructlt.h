#ifndef REGION_UREFSTRUCTLT_H_
#define REGION_UREFSTRUCTLT_H_

#include <llvm-c/Core.h>
#include <utils/structlt.h>

class GlobalState;

enum class UniversalRefStructMember {
  OBJECT_GEN,
  REGION_GEN,
  OBJECT_PTR,
  TYPE_INFO_PTR,
  REGION_PTR,
  OBJECT_PTR_OFFSET_TO_GEN,
  SCOPE_TETHER_BITS_MASK
};
constexpr int UniversalRefStructNumMembers = 7;

struct UniversalRefStructExplodedMembersLT {
  LLVMValueRef objPtrI64LE;
  LLVMValueRef objGenI32LE;
  LLVMValueRef typeInfoPtrI64LE;

  UniversalRefStructExplodedMembersLT() = delete;

  UniversalRefStructExplodedMembersLT(
      LLVMValueRef objPtrI64LE_,
      LLVMValueRef objGenI32LE_,
      LLVMValueRef typeInfoPtrI64LE_) :
      objPtrI64LE(objPtrI64LE_),
      objGenI32LE(objGenI32LE_),
      typeInfoPtrI64LE(typeInfoPtrI64LE_) {}
};

struct UniversalRefStructLT {
  explicit UniversalRefStructLT(LLVMContextRef context, LLVMTargetDataRef dataLayout);

  UniversalRefStructExplodedMembersLT explodeForRegularConcrete(
      GlobalState* globalState, FunctionState* functionState, LLVMBuilderRef builder, LLVMValueRef urefLE);

  UniversalRefStructExplodedMembersLT explodeForRegularInterface(
      GlobalState* globalState, FunctionState* functionState, LLVMBuilderRef builder, LLVMValueRef urefLE);

  UniversalRefStructExplodedMembersLT explodeForGenerationalConcrete(
      GlobalState* globalState, FunctionState* functionState, LLVMBuilderRef builder, LLVMValueRef urefLE);

  UniversalRefStructExplodedMembersLT explodeForGenerationalInterface(
      GlobalState* globalState, FunctionState* functionState, LLVMBuilderRef builder, LLVMValueRef urefLE);

  LLVMValueRef implodeForRegularConcrete(
      GlobalState* globalState,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      LLVMValueRef objPtrI64LE);
  LLVMValueRef implodeForGenerationalConcrete(
      GlobalState* globalState,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      LLVMValueRef objPtrI64LE,
      LLVMValueRef objGenI32LE);
  LLVMValueRef implodeForRegularInterface(
      GlobalState* globalState,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      LLVMValueRef typeInfoPtrI64LE,
      LLVMValueRef objPtrI64LE);
  LLVMValueRef implodeForGenerationalInterface(
      GlobalState* globalState,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      LLVMValueRef typeInfoPtrI64LE,
      LLVMValueRef objPtrI64LE,
      LLVMValueRef objGenI32LE);


  [[nodiscard]] LLVMTypeRef getStructLT() const { return structLT->getStructLT(); }

private:
  UniversalRefStructExplodedMembersLT explodeInner(
      GlobalState* globalState,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      LLVMValueRef urefLE);
  void fillUnusedFields(
      GlobalState* globalState,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      StructBuilderLT<UniversalRefStructNumMembers, UniversalRefStructMember>* urefBuilder);

  std::unique_ptr<StructLT<UniversalRefStructNumMembers, UniversalRefStructMember>> structLT;
};

#endif