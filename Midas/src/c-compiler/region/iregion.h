#ifndef REGION_IREGION_H_
#define REGION_IREGION_H_

#include <llvm-c/Core.h>
#include <function/expressions/shared/afl.h>
#include <function/expressions/shared/ref.h>
#include <metal/types.h>
#include <metal/ast.h>

class FunctionState;
class BlockState;
class GlobalState;
// TODO remove once refactor is done
class ControlBlock;
class IReferendStructsSource;
class IWeakRefStructsSource;

class IRegion {
public:
  virtual ~IRegion() = default;

  virtual Ref allocate(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* desiredReference,
      const std::vector<Ref>& membersLE) = 0;


  virtual WrapperPtrLE lockWeakRef(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refM,
      Ref weakRefLE) = 0;

  virtual void alias(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceRef,
      Ref expr) = 0;

  virtual void dealias(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      BlockState* blockState,
      LLVMBuilderRef builder,
      Reference* sourceMT,
      Ref sourceRef) = 0;

  virtual void storeMember(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* structRefMT,
      Ref structRef,
      int memberIndex,
      const std::string& memberName,
      LLVMValueRef newValueLE) = 0;

  virtual Ref loadMember(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* structRefMT,
      Ref structRef,
      int memberIndex,
      Reference* expectedMemberType,
      Reference* targetMemberType,
      const std::string& memberName) = 0;

  virtual Ref upcastWeak(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      WeakFatPtrLE sourceRefLE,
      StructReferend* sourceStructReferendM,
      Reference* sourceStructTypeM,
      InterfaceReferend* targetInterfaceReferendM,
      Reference* targetInterfaceTypeM) = 0;

  virtual Ref upcast(
      FunctionState* functionState,
      LLVMBuilderRef builder,

      Reference* sourceStructMT,
      StructReferend* sourceStructReferendM,
      Ref sourceRefLE,

      Reference* targetInterfaceTypeM,
      InterfaceReferend* targetInterfaceReferendM) = 0;

  virtual Ref lockWeak(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      bool thenResultIsNever,
      bool elseResultIsNever,
      Reference* resultOptTypeM,
      Reference* constraintRefM,
      Reference* sourceWeakRefMT,
      Ref sourceWeakRefLE,
      std::function<Ref(LLVMBuilderRef, Ref)> buildThen,
      std::function<Ref(LLVMBuilderRef)> buildElse) = 0;

  virtual Ref constructKnownSizeArray(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* referenceM,
      KnownSizeArrayT* referendM,
      const std::vector<Ref>& membersLE) = 0;

  virtual Ref getUnknownSizeArrayLength(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* usaRefMT,
      Ref arrayRef) = 0;

  virtual LLVMValueRef checkValidReference(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refM,
      Ref refLE) = 0;

  virtual LLVMTypeRef translateType(Reference* referenceM) = 0;

  virtual void translateKnownSizeArray(
      KnownSizeArrayT* knownSizeArrayMT) = 0;

  virtual void declareKnownSizeArray(
      KnownSizeArrayT* knownSizeArrayMT) = 0;

  virtual void declareUnknownSizeArray(
      UnknownSizeArrayT* unknownSizeArrayMT) = 0;

  virtual void translateUnknownSizeArray(
      UnknownSizeArrayT* unknownSizeArrayMT) = 0;


  virtual void declareEdge(
      Edge* edge) = 0;

  virtual void translateEdge(
      Edge* edge) = 0;

  virtual void translateStruct(
      StructDefinition* structM) = 0;

  virtual void declareStruct(
      StructDefinition* structM) = 0;

  virtual void translateInterface(
      InterfaceDefinition* interfaceM) = 0;

  virtual void declareInterface(
      InterfaceDefinition* interfaceM) = 0;

  virtual Ref weakAlias(FunctionState* functionState, LLVMBuilderRef builder, Reference* sourceRefMT, Reference* targetRefMT, Ref sourceRef) = 0;


  virtual void discardOwningRef(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      BlockState* blockState,
      LLVMBuilderRef builder,
      Reference* sourceMT,
      Ref sourceRef) = 0;

  virtual void noteWeakableDestroyed(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refM,
      ControlBlockPtrLE controlBlockPtrLE) = 0;

  // Gets the itable PTR and the new value that we should put into the virtual param's slot
  // (such as a void* or a weak void ref)
  virtual std::tuple<LLVMValueRef, LLVMValueRef> explodeInterfaceRef(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* virtualParamMT,
      Ref virtualArgRef) = 0;

  virtual void aliasWeakRef(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* weakRefMT,
      Ref weakRef) = 0;

  virtual void discardWeakRef(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* weakRefMT,
      Ref weakRef) = 0;

  virtual Ref getIsAliveFromWeakRef(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* weakRefM,
      Ref weakRef) = 0;

  virtual Ref loadElementFromKSAWithUpgrade(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* ksaRefMT,
      KnownSizeArrayT* ksaMT,
      Ref arrayRef,
      Ref indexRef,
      Reference* targetType) = 0;
  virtual Ref loadElementFromKSAWithoutUpgrade(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* ksaRefMT,
      KnownSizeArrayT* ksaMT,
      Ref arrayRef,
      Ref indexRef) = 0;
  virtual Ref loadElementFromUSAWithUpgrade(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* usaRefMT,
      UnknownSizeArrayT* usaMT,
      Ref arrayRef,
      Ref indexRef,
      Reference* targetType) = 0;

  virtual Ref loadElementFromUSAWithoutUpgrade(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* usaRefMT,
      UnknownSizeArrayT* usaMT,
      Ref arrayRef,
      Ref indexRef) = 0;

  virtual Ref storeElementInUSA(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* usaRefMT,
      UnknownSizeArrayT* usaMT,
      Ref arrayRef,
      Ref indexRef,
      Ref elementRef) = 0;


  virtual void deallocate(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refMT,
      Ref refLE) = 0;


  virtual Ref constructUnknownSizeArrayCountedStruct(
      FunctionState* functionState,
      BlockState* blockState,
      LLVMBuilderRef builder,
      Reference* usaMT,
      UnknownSizeArrayT* unknownSizeArrayT,
      Reference* generatorType,
      Prototype* generatorMethod,
      Ref generatorRef,
      LLVMTypeRef usaWrapperPtrLT,
      LLVMTypeRef usaElementLT,
      Ref sizeRef,
      const std::string& typeName) = 0;

  virtual LLVMValueRef getStringBytesPtr(FunctionState* functionState, LLVMBuilderRef builder, Ref ref) = 0;
  virtual LLVMValueRef getStringLen(FunctionState* functionState, LLVMBuilderRef builder, Ref ref) = 0;

  // TODO Get rid of these once refactor is done
//  virtual InterfaceFatPtrLE makeInterfaceFatPtr(Reference* referenceM_, LLVMValueRef ptrLE) = 0;
//  virtual ControlBlockPtrLE makeControlBlockPtr(Referend* referendM_, LLVMValueRef ptrLE) = 0;
//  virtual WrapperPtrLE makeWrapperPtr(Reference* referenceM_, LLVMValueRef ptrLE) = 0;
  virtual ControlBlock* getControlBlock(Referend* referend) = 0;
  virtual IReferendStructsSource* getReferendStructsSource() = 0;
  virtual IWeakRefStructsSource* getWeakRefStructsSource() = 0;
  virtual LLVMTypeRef getWeakRefHeaderStruct() = 0;
  virtual LLVMTypeRef getWeakVoidRefStruct() = 0;
  virtual Ref upgradeLoadResultToRefWithTargetOwnership(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceType,
      Reference* targetType,
      Ref sourceRef) = 0;
  virtual void fillControlBlock(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Referend* referendM,
      Mutability mutability,
      ControlBlockPtrLE controlBlockPtrLE,
      const std::string& typeName) = 0;

  virtual WrapperPtrLE mallocStr(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      LLVMValueRef lengthLE) = 0;

  virtual LLVMValueRef mallocKnownSize(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Location location,
      LLVMTypeRef referendLT) = 0;

  virtual LLVMValueRef mallocUnknownSizeArray(
      LLVMBuilderRef builder,
      LLVMTypeRef usaWrapperLT,
      LLVMTypeRef usaElementLT,
      LLVMValueRef lengthLE) = 0;
};

#endif
