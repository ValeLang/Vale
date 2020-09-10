#ifndef REGION_ASSIST_MEGA_H_
#define REGION_ASSIST_MEGA_H_

#include <llvm-c/Core.h>
#include <function/expressions/shared/afl.h>
#include <region/common/defaultrefcounting/defaultrefcounting.h>
#include <region/common/defaultlayout/defaultlayout.h>
#include <region/common/wrcweaks/wrcweaks.h>
#include "globalstate.h"
#include "function/function.h"
#include "../iregion.h"

class Mega : public IRegion {
public:
  Mega(GlobalState* globalState);
  ~Mega() override = default;

  LLVMValueRef allocate(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* desiredReference,
      const std::vector<LLVMValueRef>& membersLE) override;

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

  LLVMValueRef loadMember(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* structRefM,
      LLVMValueRef structExpr,
      Mutability mutability,
      Reference* memberType,
      int memberIndex,
      const std::string& memberName) override;

  LLVMValueRef storeMember(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      BlockState* blockState,
      LLVMBuilderRef builder,
      Reference* structRefM,
      LLVMValueRef structExpr,
      Mutability mutability,
      Reference* memberType,
      int memberIndex,
      const std::string& memberName,
      LLVMValueRef sourceLE) override;

  std::vector<LLVMValueRef> destructure(
      FunctionState* functionState,
      BlockState* blockState,
      LLVMBuilderRef builder,
      Reference* structType,
      LLVMValueRef structLE) override;

  // Suitable for passing in to an interface method
  LLVMValueRef getConcreteRefFromInterfaceRef(
      LLVMBuilderRef builder,
      LLVMValueRef refLE) override;


  LLVMValueRef upcastWeak(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      WeakFatPtrLE sourceRefLE,
      StructReferend* sourceStructReferendM,
      Reference* sourceStructTypeM,
      InterfaceReferend* targetInterfaceReferendM,
      Reference* targetInterfaceTypeM) override;

  LLVMValueRef upcast(
      FunctionState* functionState,
      LLVMBuilderRef builder,

      Reference* sourceStructTypeM,
      StructReferend* sourceStructReferendM,
      LLVMValueRef sourceStructLE,

      Reference* targetInterfaceTypeM,
      InterfaceReferend* targetInterfaceReferendM) override;


  void translateKnownSizeArray(
      KnownSizeArrayT* knownSizeArrayMT) override;

  void declareKnownSizeArray(
      KnownSizeArrayT* knownSizeArrayMT) override;

  void declareUnknownSizeArray(
      UnknownSizeArrayT* unknownSizeArrayMT) override;

  void translateUnknownSizeArray(
      UnknownSizeArrayT* unknownSizeArrayMT) override;


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
  LLVMValueRef constructString(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      LLVMValueRef lengthLE) override;

  // Returns a LLVMValueRef for a pointer to the strings contents bytes
  LLVMValueRef getStringBytesPtr(
      LLVMBuilderRef builder,
      LLVMValueRef stringRefLE) override;

  LLVMValueRef getStringLength(
      LLVMBuilderRef builder,
      LLVMValueRef stringRefLE) override;

  // Returns a LLVMValueRef for a ref to the string object.
  // The caller should then use getStringBytesPtr to then fill the string's contents.
  LLVMValueRef constructKnownSizeArray(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* referenceM,
      KnownSizeArrayT* referendM,
      const std::vector<LLVMValueRef>& membersLE) override;

  // Returns a LLVMValueRef for a ref to the string object.
  // The caller should then use getStringBytesPtr to then fill the string's contents.
  LLVMValueRef constructUnknownSizeArray(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* usaMT,
      LLVMValueRef sizeLE,
      const std::string& typeName) override;

  // should expose a dereference thing instead
//  LLVMValueRef getKnownSizeArrayElementsPtr(
//      LLVMBuilderRef builder,
//      LLVMValueRef knownSizeArrayWrapperPtrLE) override;
//  LLVMValueRef getUnknownSizeArrayElementsPtr(
//      LLVMBuilderRef builder,
//      LLVMValueRef unknownSizeArrayWrapperPtrLE) override;
//  LLVMValueRef getUnknownSizeArrayLength(
//      LLVMBuilderRef builder,
//      LLVMValueRef unknownSizeArrayWrapperPtrLE) override;

  void destroyArray(
      FunctionState* functionState,
      BlockState* blockState,
      LLVMBuilderRef builder,
      Reference* arrayType,
      LLVMValueRef arrayWrapperLE) override;

  LLVMTypeRef getKnownSizeArrayRefType(
      Reference* referenceM,
      KnownSizeArrayT* knownSizeArrayMT) override;

  LLVMTypeRef getUnknownSizeArrayRefType(
      Reference* referenceM,
      UnknownSizeArrayT* unknownSizeArrayMT) override;

  LLVMValueRef checkValidReference(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refM,
      Ref refLE) override;

  LLVMValueRef loadElement(
      FunctionState* functionState,
      BlockState* blockState,
      LLVMBuilderRef builder,
      Reference* structRefM,
      Reference* elementRefM,
      LLVMValueRef sizeIntLE,
      LLVMValueRef arrayCRefLE,
      Mutability mutability,
      LLVMValueRef indexIntLE) override;

  LLVMValueRef storeElement(
      FunctionState* functionState,
      BlockState* blockState,
      LLVMBuilderRef builder,
      Reference* arrayRefM,
      Reference* elementRefM,
      LLVMValueRef sizeIntLE,
      LLVMValueRef arrayCRefLE,
      Mutability mutability,
      LLVMValueRef indexIntLE,
      LLVMValueRef sourceLE) override;



  LLVMTypeRef translateType(Reference* referenceM) override;


  void declareEdge(
      Edge* edge) override;

  void translateEdge(
      Edge* edge) override;

  LLVMTypeRef getStructRefType(
      Reference* refM,
      StructReferend* structReferendM) override;

  void translateStruct(
      StructDefinition* structM) override;

  void declareStruct(
      StructDefinition* structM) override;

  void translateInterface(
      InterfaceDefinition* interfaceM) override;


  void declareInterface(
      InterfaceDefinition* interfaceM) override;

  LLVMTypeRef getStringRefType() const override;

  Ref weakAlias(FunctionState* functionState, LLVMBuilderRef builder, Reference* sourceRefMT, Reference* targetRefMT, Ref sourceRef) override;

// Transmutes a weak ref of one ownership (such as borrow) to another ownership (such as weak).
  Ref transmuteWeakRef(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceWeakRefMT,
      Reference* targetWeakRefMT,
      Ref sourceWeakRef);

private:
  LLVMTypeRef translateInterfaceMethodToFunctionType(
      InterfaceMethod* method);

protected:
  GlobalState* globalState;
  DefaultRefCounting defaultRefCounting;
  FatWeaks fatWeaks;
  WrcWeaks wrcWeaks;
  DefaultLayoutter defaultLayout;
};

#endif
