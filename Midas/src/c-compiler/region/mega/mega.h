#ifndef REGION_ASSIST_MEGA_H_
#define REGION_ASSIST_MEGA_H_

#include <llvm-c/Core.h>
#include <function/expressions/shared/afl.h>
#include <region/common/defaultimmutables/defaultimmutables.h>
#include <region/common/defaultlayout/structsrouter.h>
#include <region/common/wrcweaks/wrcweaks.h>
#include <region/common/lgtweaks/lgtweaks.h>
#include <region/common/hgm/hgm.h>
#include "globalstate.h"
#include "function/function.h"
#include "../iregion.h"

class Mega : public IRegion {
public:
  Mega(GlobalState* globalState);
  ~Mega() override = default;

  Ref allocate(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* desiredReference,
      const std::vector<Ref>& membersLE) override;

  void alias(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceRef,
      Ref expr) override;

  void dealias(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      BlockState* blockState,
      LLVMBuilderRef builder,
      Reference* sourceMT,
      Ref sourceRef) override;
//
//  Ref loadMember(
//      AreaAndFileAndLine from,
//      FunctionState* functionState,
//      LLVMBuilderRef builder,
//      Reference* structRefM,
//      Ref structExpr,
//      Mutability mutability,
//      Reference* memberType,
//      int memberIndex,
//      const std::string& memberName) override {
//    assert(false);
//  }
//
//  Ref storeMember(
//      AreaAndFileAndLine from,
//      FunctionState* functionState,
//      BlockState* blockState,
//      LLVMBuilderRef builder,
//      Reference* structRefM,
//      Ref structExpr,
//      Mutability mutability,
//      Reference* memberType,
//      int memberIndex,
//      const std::string& memberName,
//      Ref sourceLE) override {
//    assert(false);
//  }

  std::vector<Ref> destructure(
      FunctionState* functionState,
      BlockState* blockState,
      LLVMBuilderRef builder,
      Reference* structType,
      Ref structLE) override {
    assert(false);
    exit(1);
  }

  // Suitable for passing in to an interface method
  LLVMValueRef getConcreteRefFromInterfaceRef(
      LLVMBuilderRef builder,
      LLVMValueRef refLE) override {
    assert(false);
    exit(1);
  }


  Ref upcastWeak(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      WeakFatPtrLE sourceRefLE,
      StructReferend* sourceStructReferendM,
      Reference* sourceStructTypeM,
      InterfaceReferend* targetInterfaceReferendM,
      Reference* targetInterfaceTypeM) override;

  Ref upcast(
      FunctionState* functionState,
      LLVMBuilderRef builder,

      Reference* sourceStructMT,
      StructReferend* sourceStructReferendM,
      Ref sourceRefLE,

      Reference* targetInterfaceTypeM,
      InterfaceReferend* targetInterfaceReferendM);


  void translateKnownSizeArray(
      KnownSizeArrayT* knownSizeArrayMT) override;

  void declareKnownSizeArray(
      KnownSizeArrayT* knownSizeArrayMT) override;

  void declareUnknownSizeArray(
      UnknownSizeArrayT* unknownSizeArrayMT) override;

  void translateUnknownSizeArray(
      UnknownSizeArrayT* unknownSizeArrayMT) override;

  WrapperPtrLE lockWeakRef(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refM,
      Ref weakRefLE) override;

  Ref lockWeak(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      bool thenResultIsNever,
      bool elseResultIsNever,
      Reference* resultOptTypeM,
//      LLVMTypeRef resultOptTypeL,
      Reference* constraintRefM,
      Reference* sourceWeakRefMT,
      Ref sourceWeakRefLE,
      std::function<Ref(LLVMBuilderRef, Ref)> buildThen,
      std::function<Ref(LLVMBuilderRef)> buildElse) override;

  // Returns a LLVMValueRef for a ref to the string object.
  // The caller should then use getStringBytesPtr to then fill the string's contents.
  Ref constructString(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Ref lengthLE) override {
    assert(false);
    exit(1);
  }

  // Returns a LLVMValueRef for a pointer to the strings contents bytes
  Ref getStringBytesPtr(
      LLVMBuilderRef builder,
      Ref stringRefLE) override {
    assert(false);
    exit(1);
  }

  Ref getStringLength(
      LLVMBuilderRef builder,
      Ref stringRefLE) override {
    assert(false);
    exit(1);
  }

  // Returns a LLVMValueRef for a ref to the string object.
  // The caller should then use getStringBytesPtr to then fill the string's contents.
  Ref constructKnownSizeArray(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* referenceM,
      KnownSizeArrayT* referendM,
      const std::vector<Ref>& membersLE) override;

  // Returns a LLVMValueRef for a ref to the string object.
  // The caller should then use getStringBytesPtr to then fill the string's contents.
  Ref constructUnknownSizeArray(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* usaMT,
      Ref sizeLE,
      const std::string& typeName) override {
    assert(false);
    exit(1);
  }

  // should expose a dereference thing instead
//  LLVMValueRef getKnownSizeArrayElementsPtr(
//      LLVMBuilderRef builder,
//      LLVMValueRef knownSizeArrayWrapperPtrLE) override;
//  LLVMValueRef getUnknownSizeArrayElementsPtr(
//      LLVMBuilderRef builder,
//      LLVMValueRef unknownSizeArrayWrapperPtrLE) override;

  Ref getUnknownSizeArrayLength(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* usaRefMT,
      Ref arrayRef) override;

  void destroyArray(
      FunctionState* functionState,
      BlockState* blockState,
      LLVMBuilderRef builder,
      Reference* arrayType,
      Ref arrayWrapperLE) override {
    assert(false);
    exit(1);
  }

  LLVMTypeRef getKnownSizeArrayRefType(
      Reference* referenceM,
      KnownSizeArrayT* knownSizeArrayMT) override {
    assert(false);
    exit(1);
  }

  LLVMTypeRef getUnknownSizeArrayRefType(
      Reference* referenceM,
      UnknownSizeArrayT* unknownSizeArrayMT) override {
    assert(false);
    exit(1);
  }

  LLVMValueRef checkValidReference(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refM,
      Ref refLE) override;

  Ref loadElement(
      FunctionState* functionState,
      BlockState* blockState,
      LLVMBuilderRef builder,
      Reference* structRefM,
      Reference* elementRefM,
      Ref sizeIntLE,
      Ref arrayCRefLE,
      Mutability mutability,
      Ref indexIntLE) override {
    assert(false);
    exit(1);
  }


  LLVMTypeRef translateType(Reference* referenceM) override;


  void declareEdge(
      Edge* edge) override;

  void translateEdge(
      Edge* edge) override;

  LLVMTypeRef getStructRefType(
      Reference* refM,
      StructReferend* structReferendM) override {
    assert(false);
    exit(1);
  }

  void translateStruct(
      StructDefinition* structM) override;

  void declareStruct(
      StructDefinition* structM) override;

  void translateInterface(
      InterfaceDefinition* interfaceM) override;


  void declareInterface(
      InterfaceDefinition* interfaceM) override;

  LLVMTypeRef getStringRefType() const override {
    assert(false);
    exit(1);
  }

  Ref weakAlias(FunctionState* functionState, LLVMBuilderRef builder, Reference* sourceRefMT, Reference* targetRefMT, Ref sourceRef) override;

// Transmutes a weak ref of one ownership (such as borrow) to another ownership (such as weak).
  Ref transmuteWeakRef(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceWeakRefMT,
      Reference* targetWeakRefMT,
      Ref sourceWeakRef);


  void discardOwningRef(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      BlockState* blockState,
      LLVMBuilderRef builder,
      Reference* sourceMT,
      Ref sourceRef) override;

  void noteWeakableDestroyed(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refM,
      ControlBlockPtrLE controlBlockPtrLE) override;

  void storeMember(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* structRefMT,
      Ref structRef,
      int memberIndex,
      const std::string& memberName,
      LLVMValueRef newValueLE) override;

  Ref loadMember(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* structRefMT,
      Ref structRef,
      int memberIndex,
      Reference* expectedMemberType,
      Reference* targetType,
      const std::string& memberName) override;


  // Gets the itable PTR and the new value that we should put into the virtual param's slot
  // (such as a void* or a weak void ref)
  std::tuple<LLVMValueRef, LLVMValueRef> explodeInterfaceRef(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* virtualParamMT,
      Ref virtualArgRef) override;


  // TODO maybe combine with alias/acquireReference?
  // After we load from a local, member, or element, we can feed the result through this
  // function to turn it into a desired ownership.
  // Example:
  // - Can load from an owning ref member to get a constraint ref.
  // - Can load from a constraint ref member to get a weak ref.
  Ref upgradeLoadResultToRefWithTargetOwnership(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceType,
      Reference* targetType,
      Ref sourceRef) override;

  void aliasWeakRef(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* weakRefMT,
      Ref weakRef) override;

  void discardWeakRef(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* weakRefMT,
      Ref weakRef) override;

  Ref getIsAliveFromWeakRef(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* weakRefM,
      Ref weakRef) override;

  Ref loadElementFromKSAWithUpgrade(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* ksaRefMT,
      KnownSizeArrayT* ksaMT,
      Ref arrayRef,
      Ref indexRef,
      Reference* targetType) override;
  Ref loadElementFromKSAWithoutUpgrade(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* ksaRefMT,
      KnownSizeArrayT* ksaMT,
      Ref arrayRef,
      Ref indexRef) override;
  Ref loadElementFromUSAWithUpgrade(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* usaRefMT,
      UnknownSizeArrayT* usaMT,
      Ref arrayRef,
      Ref indexRef,
      Reference* targetType) override;
  Ref loadElementFromUSAWithoutUpgrade(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* usaRefMT,
      UnknownSizeArrayT* usaMT,
      Ref arrayRef,
      Ref indexRef) override;


  Ref storeElementInUSA(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* usaRefMT,
      UnknownSizeArrayT* usaMT,
      Ref arrayRef,
      Ref indexRef,
      Ref elementRef) override;

  // TODO Make these private once refactor is done
  WeakFatPtrLE makeWeakFatPtr(Reference* referenceM_, LLVMValueRef ptrLE) override {
    return weakFatPtrMaker.make(referenceM_, ptrLE);
  }
  InterfaceFatPtrLE makeInterfaceFatPtr(Reference* referenceM_, LLVMValueRef ptrLE) override {
    return interfaceFatPtrMaker.make(referenceM_, ptrLE);
  }
  ControlBlockPtrLE makeControlBlockPtr(Referend* referendM_, LLVMValueRef ptrLE) override {
    return controlBlockPtrMaker.make(referendM_, ptrLE);
  }
  WrapperPtrLE makeWrapperPtr(Reference* referenceM_, LLVMValueRef ptrLE) override {
    return wrapperPtrMaker.make(referenceM_, ptrLE);
  }
  // TODO get rid of these once refactor is done
  ControlBlock* getControlBlock(Referend* referend) override {
    return referendStructs.getControlBlock(referend);
  }
  IReferendStructsSource* getReferendStructsSource() override {
    return &referendStructs;
  }
  IWeakRefStructsSource* getWeakRefStructsSource() override {
    return &weakRefStructs;
  }
  LLVMTypeRef getStringInnerStruct() override {
    return defaultImmutables.getStringInnerStructL();
  }
  LLVMTypeRef getStringWrapperStruct() override {
    return defaultImmutables.getStringWrapperStructL();
  }
  LLVMTypeRef getWeakRefHeaderStruct() override {
    return mutWeakableStructs.weakRefHeaderStructL;
  }
  LLVMTypeRef getWeakVoidRefStruct() override {
    return mutWeakableStructs.weakVoidRefStructL;
  }
  void fillControlBlock(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Referend* referendM,
      Mutability mutability,
      ControlBlockPtrLE controlBlockPtrLE,
      const std::string& typeName) override;

private:
  LLVMTypeRef translateInterfaceMethodToFunctionType(
      InterfaceMethod* method);

  void naiveRcFree(
      FunctionState* functionState,
      BlockState* blockState,
      LLVMBuilderRef thenBuilder,
      Reference* sourceMT,
      Ref sourceRef);


protected:
  GlobalState* globalState;

  ReferendStructs immStructs;
  ReferendStructs mutNonWeakableStructs;
  WeakableReferendStructs mutWeakableStructs;

  DefaultImmutables defaultImmutables;
  FatWeaks fatWeaks;
  WrcWeaks wrcWeaks;
  LgtWeaks lgtWeaks;
  HybridGenerationalMemory hgmWeaks;

  ReferendStructsRouter referendStructs;
  WeakRefStructsRouter weakRefStructs;

  // TODO see if we can just use referendStructs/weakRefStructs instead of having these?
  WeakFatPtrLEMaker weakFatPtrMaker;
  InterfaceFatPtrLEMaker interfaceFatPtrMaker;
  ControlBlockPtrLEMaker controlBlockPtrMaker;
  WrapperPtrLEMaker wrapperPtrMaker;
};

#endif
