#ifndef REGION_ASSIST_ASSIST_H_
#define REGION_ASSIST_ASSIST_H_

#include <llvm-c/Core.h>
#include <function/expressions/shared/afl.h>
#include <region/mega/mega.h>
#include <region/common/fatweaks/fatweaks.h>
#include <region/common/primitives.h>
#include <region/common/defaultimmutables//defaultimmutables.h>
#include <region/common/wrcweaks/wrcweaks.h>
#include <region/common/defaultlayout/structsrouter.h>
#include "globalstate.h"
#include "function/function.h"
#include "../iregion.h"

class Assist : public IRegion {
public:
  Assist(GlobalState* globalState);
  ~Assist() override = default;


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
      bool weakRefKnownLive,
      std::function<Ref(LLVMBuilderRef, Ref)> buildThen,
      std::function<Ref(LLVMBuilderRef)> buildElse) override;

  LLVMTypeRef translateType(Reference* referenceM) override;

  LLVMValueRef getCensusObjectId(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refM,
      Ref refLE) override;

  Ref upcastWeak(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      WeakFatPtrLE sourceRefLE,
      StructReferend* sourceStructReferendM,
      Reference* sourceStructTypeM,
      InterfaceReferend* targetInterfaceReferendM,
      Reference* targetInterfaceTypeM) override;

  void declareKnownSizeArray(
      KnownSizeArrayT* knownSizeArrayMT) override;

  void declareUnknownSizeArray(
      UnknownSizeArrayT* unknownSizeArrayMT) override;

  void translateUnknownSizeArray(
      UnknownSizeArrayT* unknownSizeArrayMT) override;

  void translateKnownSizeArray(
      KnownSizeArrayT* knownSizeArrayMT) override;

  void declareStruct(
      StructDefinition* structM) override;

  void translateStruct(
      StructDefinition* structM) override;

  void declareEdge(
      Edge* edge) override;

  void translateEdge(
      Edge* edge) override;

  void declareInterface(
      InterfaceDefinition* interfaceM) override;

  void translateInterface(
      InterfaceDefinition* interfaceM) override;

  Ref weakAlias(
      FunctionState* functionState, LLVMBuilderRef builder, Reference* sourceRefMT, Reference* targetRefMT, Ref sourceRef) override;

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

  Ref loadMember(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* structRefMT,
      Ref structRef,
      bool structKnownLive,
      int memberIndex,
      Reference* expectedMemberType,
      Reference* targetMemberType,
      const std::string& memberName) override;

  void storeMember(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* structRefMT,
      Ref structRef,
      bool structKnownLive,
      int memberIndex,
      const std::string& memberName,
      LLVMValueRef newValueLE) override;

  std::tuple<LLVMValueRef, LLVMValueRef> explodeInterfaceRef(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* virtualParamMT,
      Ref virtualArgRef) override;


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
      Ref weakRef,
      bool knownLive) override;

  LLVMValueRef getStringBytesPtr(FunctionState* functionState, LLVMBuilderRef builder, Ref ref) override;

  Ref allocate(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* desiredReference,
      const std::vector<Ref>& membersLE) override;

  Ref upcast(
      FunctionState* functionState,
      LLVMBuilderRef builder,

      Reference* sourceStructMT,
      StructReferend* sourceStructReferendM,
      Ref sourceRefLE,

      Reference* targetInterfaceTypeM,
      InterfaceReferend* targetInterfaceReferendM) override;

  WrapperPtrLE lockWeakRef(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refM,
      Ref weakRefLE,
      bool weakRefKnownLive) override;

  // Returns a LLVMValueRef for a ref to the string object.
  // The caller should then use getStringBytesPtr to then fill the string's contents.
  Ref constructKnownSizeArray(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* referenceM,
      KnownSizeArrayT* referendM,
      const std::vector<Ref>& membersLE) override;

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
      Ref arrayRef,
      bool arrayKnownLive) override;

  LLVMValueRef checkValidReference(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refM,
      Ref refLE) override;


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

  void checkInlineStructType(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refMT,
      Ref refLE) override;

  Ref loadElementFromKSAWithUpgrade(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* ksaRefMT,
      KnownSizeArrayT* ksaMT,
      Ref arrayRef,
      bool arrayKnownLive,
      Ref indexRef,
      Reference* targetType) override;
  Ref loadElementFromKSAWithoutUpgrade(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* ksaRefMT,
      KnownSizeArrayT* ksaMT,
      Ref arrayRef,
      bool arrayKnownLive,
      Ref indexRef) override;
  Ref loadElementFromUSAWithUpgrade(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* usaRefMT,
      UnknownSizeArrayT* usaMT,
      Ref arrayRef,
      bool arrayKnownLive,
      Ref indexRef,
      Reference* targetType) override;
  Ref loadElementFromUSAWithoutUpgrade(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* usaRefMT,
      UnknownSizeArrayT* usaMT,
      Ref arrayRef,
      bool arrayKnownLive,
      Ref indexRef) override;


  Ref storeElementInUSA(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* usaRefMT,
      UnknownSizeArrayT* usaMT,
      Ref arrayRef,
      bool arrayKnownLive,
      Ref indexRef,
      Ref elementRef) override;


  void deallocate(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refMT,
      Ref refLE) override;


  Ref constructUnknownSizeArrayCountedStruct(
      FunctionState* functionState,
      BlockState* blockState,
      LLVMBuilderRef builder,
      Reference* usaMT,
      UnknownSizeArrayT* unknownSizeArrayT,
      Reference* generatorType,
      Prototype* generatorMethod,
      Ref generatorRef,
      LLVMTypeRef usaElementLT,
      Ref sizeRef,
      const std::string& typeName) override;


  WrapperPtrLE mallocStr(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      LLVMValueRef lengthLE) override;

//  LLVMValueRef mallocKnownSize(
//      FunctionState* functionState,
//      LLVMBuilderRef builder,
//      Location location,
//      LLVMTypeRef referendLT) override;

//  LLVMValueRef mallocUnknownSizeArray(
//      LLVMBuilderRef builder,
//      LLVMTypeRef usaWrapperLT,
//      LLVMTypeRef usaElementLT,
//      LLVMValueRef lengthLE) override;

  // TODO Make these private once refactor is done
//  WeakFatPtrLE makeWeakFatPtr(Reference* referenceM_, LLVMValueRef ptrLE) override {
//    return mutWeakableStructs.makeWeakFatPtr(referenceM_, ptrLE);
//  }
  // TODO get rid of these once refactor is done
//  ControlBlock* getControlBlock(Referend* referend) override {
//    return referendStructs.getControlBlock(referend);
//  }
//  IReferendStructsSource* getReferendStructsSource() override {
//    return &referendStructs;
//  }
//  IWeakRefStructsSource* getWeakRefStructsSource() override {
//    return &weakRefStructs;
//  }
  LLVMValueRef getStringLen(FunctionState* functionState, LLVMBuilderRef builder, Ref ref) override {
    return referendStructs.getStringLen(functionState, builder, ref);
  }
//  LLVMTypeRef getWeakRefHeaderStruct(Referend* referend) override {
//    return mutWeakableStructs.getWeakRefHeaderStruct(referend);
//  }
//  LLVMTypeRef getWeakVoidRefStruct(Referend* referend) override {
//    return mutWeakableStructs.getWeakVoidRefStruct(referend);
//  }
  void fillControlBlock(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Referend* referendM,
      Mutability mutability,
      ControlBlockPtrLE controlBlockPtrLE,
      const std::string& typeName);

  std::string getRefNameC(
      Reference* refMT) override;
  void generateStructDefsC(
      std::unordered_map<std::string, std::string>* cByExportedName, StructDefinition* refMT) override;
  void generateInterfaceDefsC(
      std::unordered_map<std::string, std::string>* cByExportedName, InterfaceDefinition* refMT) override;


  LLVMTypeRef getExternalType(
      Reference* refMT) override;

  LLVMValueRef externalify(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refMT,
      Ref ref) override;

  Ref internalify(FunctionState *functionState, LLVMBuilderRef builder, Reference *refMT, LLVMValueRef ref) override;

private:
  LLVMTypeRef translateInterfaceMethodToFunctionType(
      InterfaceReferend* referend,
      InterfaceMethod* method);


  GlobalState* globalState;

  ReferendStructs immStructs;
  ReferendStructs mutNonWeakableStructs;
  WeakableReferendStructs mutWeakableStructs;

  DefaultImmutables defaultImmutables;

  ReferendStructsRouter referendStructs;
  WeakRefStructsRouter weakRefStructs;

  FatWeaks fatWeaks;
  WrcWeaks wrcWeaks;
};

#endif
