#include <globalstate.h>
#include <function/function.h>
#include <function/expressions/expressions.h>
#include <region/common/common.h>
#include "urefstructlt.h"

// Temporary, until we actually put the region pointer in the universal ref
// It ends in 0 to align to 16, for compression.
static constexpr int64_t universalRefRegionPtrConstant = 0x1300;
// Temporary, until we actually put the region gen in the universal ref
static constexpr int64_t universalRefRegionGenConstant = 0x1310;
// Only used for regions that dont have generations.
static constexpr int64_t universalRefObjectGenConstant = 0x1320;
// Temporary, until we actually put the type info in the region constant
// It ends in 0 to align to 16, for compression.
static constexpr int64_t universalRefTypeInfoPtrConstant = 0x1330;
// Temporary, until we support inline data and put the offset in the universal ref
static constexpr int64_t universalRefObjectPtrOffsetToGenOffsetConstant = 0x1340;
// Temporary, until we support inline data and put the offset in the universal ref
static constexpr int64_t universalRefScopeTetherMaskBitsConstant = 0x1350;

UniversalRefStructLT::UniversalRefStructLT(LLVMContextRef context, LLVMTargetDataRef dataLayout) {
  structLT =
      std::make_unique<StructLT<UniversalRefStructNumMembers, UniversalRefStructMember>>(
          context,
          "__UniversalRef",
          // See URSL for why things are laid out like this.
          std::array<LLVMTypeRef, UniversalRefStructNumMembers>{
              LLVMInt32TypeInContext(context), // object generation
              LLVMInt32TypeInContext(context), // region generation
              LLVMIntTypeInContext(context, 56), // object pointer
              LLVMIntTypeInContext(context, 52), // type info pointer
              LLVMIntTypeInContext(context, 52), // region pointer
              LLVMInt16TypeInContext(context), // offset to generation
              LLVMInt16TypeInContext(context), // scope tether bits mask
          });
  // The size of the above struct isn't necessarily 32 bytes, it's not compressed by LLVM.
  // Later, we use buildCompressStruct for that.
}

UniversalRefStructExplodedMembersLT UniversalRefStructLT::explodeForRegularConcrete(GlobalState* globalState, FunctionState* functionState, LLVMBuilderRef builder, LLVMValueRef urefLE) {
  UniversalRefStructExplodedMembersLT result = explodeInner(globalState, functionState, builder, urefLE);
  buildAssertIntEq(globalState, functionState, builder, result.objGenI32LE, constI32LE(globalState, universalRefObjectGenConstant), "Invalid reference in extern boundary! (og)");
  result.objGenI32LE = nullptr;
  buildAssertIntEq(globalState, functionState, builder, result.typeInfoPtrI64LE, constI64LE(globalState, universalRefTypeInfoPtrConstant), "Invalid reference in extern boundary! (t)");
  result.typeInfoPtrI64LE = nullptr;
  return result;
}

UniversalRefStructExplodedMembersLT UniversalRefStructLT::explodeForRegularInterface(GlobalState* globalState, FunctionState* functionState, LLVMBuilderRef builder, LLVMValueRef urefLE) {
  UniversalRefStructExplodedMembersLT result = explodeInner(globalState, functionState, builder, urefLE);
  buildAssertIntEq(globalState, functionState, builder, result.objGenI32LE, constI32LE(globalState, universalRefObjectGenConstant), "Invalid reference in extern boundary! (og)");
  result.objGenI32LE = nullptr;
  return result;
}

UniversalRefStructExplodedMembersLT UniversalRefStructLT::explodeForGenerationalConcrete(GlobalState* globalState, FunctionState* functionState, LLVMBuilderRef builder, LLVMValueRef urefLE) {
  UniversalRefStructExplodedMembersLT result = explodeInner(globalState, functionState, builder, urefLE);
  buildAssertIntEq(globalState, functionState, builder, result.typeInfoPtrI64LE, constI64LE(globalState, universalRefTypeInfoPtrConstant), "Invalid reference in extern boundary! (t)");
  result.typeInfoPtrI64LE = nullptr;
  return result;
}

UniversalRefStructExplodedMembersLT UniversalRefStructLT::explodeForGenerationalInterface(GlobalState* globalState, FunctionState* functionState, LLVMBuilderRef builder, LLVMValueRef urefLE) {
  UniversalRefStructExplodedMembersLT result = explodeInner(globalState, functionState, builder, urefLE);
  return result;
}

UniversalRefStructExplodedMembersLT UniversalRefStructLT::explodeInner(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef urefI256LE) {
  assert(LLVMTypeOf(urefI256LE) == LLVMIntTypeInContext(globalState->context, 256));
  auto urefLE = buildDecompressStruct(globalState, *structLT, builder, urefI256LE);
  auto regionPtrI52LE = structLT->extractMember(builder, urefLE, UniversalRefStructMember::REGION_PTR);
  auto regionPtrI64LE = decompressI52PtrToI64(globalState, builder, regionPtrI52LE);
  buildAssertIntEq(globalState, functionState, builder, regionPtrI64LE, constI64LE(globalState, universalRefRegionPtrConstant), "Invalid reference in extern boundary! (rp)");
  auto regionGenLE = structLT->extractMember(builder, urefLE, UniversalRefStructMember::REGION_GEN);
  buildAssertIntEq(globalState, functionState, builder, regionGenLE, constI32LE(globalState, universalRefRegionGenConstant), "Invalid reference in extern boundary! (rg)");
  auto typeInfoPtrI52LE = structLT->extractMember(builder, urefLE, UniversalRefStructMember::TYPE_INFO_PTR);
  auto typeInfoPtrI64LE = decompressI52PtrToI64(globalState, builder, typeInfoPtrI52LE);
  auto objectGenI32LE = structLT->extractMember(builder, urefLE, UniversalRefStructMember::OBJECT_GEN);
  auto objectPtrOffsetToGenLE = structLT->extractMember(builder, urefLE, UniversalRefStructMember::OBJECT_PTR_OFFSET_TO_GEN);
  buildAssertIntEq(globalState, functionState, builder, objectPtrOffsetToGenLE, constI16LE(globalState, universalRefObjectPtrOffsetToGenOffsetConstant), "Invalid reference in extern boundary! (oo)");
  auto objectPtrTetherMaskBitsLE = structLT->extractMember(builder, urefLE, UniversalRefStructMember::SCOPE_TETHER_BITS_MASK);
  buildAssertIntEq(globalState, functionState, builder, objectPtrTetherMaskBitsLE, constI16LE(globalState, universalRefScopeTetherMaskBitsConstant), "Invalid reference in extern boundary! (m)");
  auto objectPtrI56LE = structLT->extractMember(builder, urefLE, UniversalRefStructMember::OBJECT_PTR);
  auto objectPtrI64LE = decompressI56PtrToI64(globalState, builder, objectPtrI56LE);
  return UniversalRefStructExplodedMembersLT{objectPtrI64LE, objectGenI32LE, typeInfoPtrI64LE};
}

LLVMValueRef UniversalRefStructLT::implodeForRegularConcrete(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef objPtrI64LE) {
  auto int64LT = LLVMInt64TypeInContext(globalState->context);
  StructBuilderLT<UniversalRefStructNumMembers, UniversalRefStructMember> urefBuilder(structLT.get());
  auto objectGenLE = constI32LE(globalState, universalRefObjectGenConstant);
  urefBuilder.insertMember(builder, UniversalRefStructMember::OBJECT_GEN, objectGenLE);
  auto objPtrI56LE = compressI64PtrToI56(globalState, builder, objPtrI64LE);
  urefBuilder.insertMember(builder, UniversalRefStructMember::OBJECT_PTR, objPtrI56LE);
  auto typeInfoPtrI64LE = LLVMConstInt(int64LT, universalRefTypeInfoPtrConstant, false);
  auto typeInfoPtrI52LE = compressI64PtrToI52(globalState, builder, typeInfoPtrI64LE);
  urefBuilder.insertMember(builder, UniversalRefStructMember::TYPE_INFO_PTR, typeInfoPtrI52LE);
  fillUnusedFields(globalState, functionState, builder, &urefBuilder);
  auto structLE = urefBuilder.build();
  auto resultLE = buildCompressStruct(globalState, *structLT, builder, structLE);
  assert(LLVMSizeOfTypeInBits(globalState->dataLayout, LLVMTypeOf(resultLE)) == 256);
  return resultLE;
}

LLVMValueRef UniversalRefStructLT::implodeForGenerationalConcrete(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef objPtrI64LE,
    LLVMValueRef objGenI32LE) {
  auto int64LT = LLVMInt64TypeInContext(globalState->context);
  StructBuilderLT<UniversalRefStructNumMembers, UniversalRefStructMember> urefBuilder(structLT.get());
  urefBuilder.insertMember(builder, UniversalRefStructMember::OBJECT_GEN, objGenI32LE);
  auto objPtrI56LE = compressI64PtrToI56(globalState, builder, objPtrI64LE);
  urefBuilder.insertMember(builder, UniversalRefStructMember::OBJECT_PTR, objPtrI56LE);
  auto typeInfoPtrI64LE = LLVMConstInt(int64LT, universalRefTypeInfoPtrConstant, false);
  auto typeInfoPtrI52LE = compressI64PtrToI52(globalState, builder, typeInfoPtrI64LE);
  urefBuilder.insertMember(builder, UniversalRefStructMember::TYPE_INFO_PTR, typeInfoPtrI52LE);
  fillUnusedFields(globalState, functionState, builder, &urefBuilder);
  auto structLE = urefBuilder.build();
  auto resultLE = buildCompressStruct(globalState, *structLT, builder, structLE);
  assert(LLVMSizeOfTypeInBits(globalState->dataLayout, LLVMTypeOf(resultLE)) == 256);
  return resultLE;
}

LLVMValueRef UniversalRefStructLT::implodeForRegularInterface(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef typeInfoPtrI64LE,
    LLVMValueRef objPtrI64LE) {
  StructBuilderLT<UniversalRefStructNumMembers, UniversalRefStructMember> urefBuilder(structLT.get());
  auto objectGenLE = constI32LE(globalState, universalRefObjectGenConstant);
  urefBuilder.insertMember(builder, UniversalRefStructMember::OBJECT_GEN, objectGenLE);
  auto objPtrI56LE = compressI64PtrToI56(globalState, builder, objPtrI64LE);
  urefBuilder.insertMember(builder, UniversalRefStructMember::OBJECT_PTR, objPtrI56LE);
  auto typeInfoPtrI52LE = compressI64PtrToI52(globalState, builder, typeInfoPtrI64LE);
  urefBuilder.insertMember(builder, UniversalRefStructMember::TYPE_INFO_PTR, typeInfoPtrI52LE);
  fillUnusedFields(globalState, functionState, builder, &urefBuilder);
  auto structLE = urefBuilder.build();
  auto resultLE = buildCompressStruct(globalState, *structLT, builder, structLE);
  assert(LLVMSizeOfTypeInBits(globalState->dataLayout, LLVMTypeOf(resultLE)) == 256);
  return resultLE;
}

LLVMValueRef UniversalRefStructLT::implodeForGenerationalInterface(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef typeInfoPtrI64LE,
    LLVMValueRef objPtrI64LE,
    LLVMValueRef objGenI32LE) {
  StructBuilderLT<UniversalRefStructNumMembers, UniversalRefStructMember> urefBuilder(structLT.get());
  urefBuilder.insertMember(builder, UniversalRefStructMember::OBJECT_GEN, objGenI32LE);
  auto objPtrI56LE = compressI64PtrToI56(globalState, builder, objPtrI64LE);
  urefBuilder.insertMember(builder, UniversalRefStructMember::OBJECT_PTR, objPtrI56LE);
  auto typeInfoPtrI52LE = compressI64PtrToI52(globalState, builder, typeInfoPtrI64LE);
  urefBuilder.insertMember(builder, UniversalRefStructMember::TYPE_INFO_PTR, typeInfoPtrI52LE);
  fillUnusedFields(globalState, functionState, builder, &urefBuilder);
  auto structLE = urefBuilder.build();
  auto resultLE = buildCompressStruct(globalState, *structLT, builder, structLE);
  assert(LLVMSizeOfTypeInBits(globalState->dataLayout, LLVMTypeOf(resultLE)) == 256);
  return resultLE;
}

void UniversalRefStructLT::fillUnusedFields(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    StructBuilderLT<UniversalRefStructNumMembers, UniversalRefStructMember>* urefBuilder) {
  auto int64LT = LLVMInt64TypeInContext(globalState->context);
  auto regionPtrI64LE = LLVMConstInt(int64LT, universalRefRegionPtrConstant, false);
  auto regionPtrI52LE = compressI64PtrToI52(globalState, builder, regionPtrI64LE);
  urefBuilder->insertMember(builder, UniversalRefStructMember::REGION_PTR, regionPtrI52LE);
  auto regionGenLE = constI32LE(globalState, universalRefRegionGenConstant);
  urefBuilder->insertMember(builder, UniversalRefStructMember::REGION_GEN, regionGenLE);
  auto objectPtrOffsetToGenLE = constI16LE(globalState, universalRefObjectPtrOffsetToGenOffsetConstant);
  urefBuilder->insertMember(builder, UniversalRefStructMember::OBJECT_PTR_OFFSET_TO_GEN, objectPtrOffsetToGenLE);
  auto tetherMaskBitsLE = constI16LE(globalState, universalRefScopeTetherMaskBitsConstant);
  urefBuilder->insertMember(builder, UniversalRefStructMember::SCOPE_TETHER_BITS_MASK, tetherMaskBitsLE);
}
