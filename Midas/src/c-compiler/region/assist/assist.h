#ifndef REGION_ASSIST_ASSIST_H_
#define REGION_ASSIST_ASSIST_H_

#include <llvm-c/Core.h>
#include <function/expressions/shared/afl.h>
#include "globalstate.h"
#include "function/function.h"
#include "../iregion.h"

class Assist : public IRegion {
public:
  ~Assist() override = default;

  LLVMValueRef allocate(
      AreaAndFileAndLine from,
      GlobalState* globalState,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* desiredReference,
      const std::vector<LLVMValueRef>& membersLE) override;

  LLVMValueRef alias(
      AreaAndFileAndLine from,
      GlobalState* globalState,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceRef,
      Reference* targetRef,
      LLVMValueRef expr) override;

  void dealias(
      AreaAndFileAndLine from,
      GlobalState* globalState,
      FunctionState* functionState,
      BlockState* blockState,
      LLVMBuilderRef builder,
      Reference* sourceRef,
      LLVMValueRef expr) override;

  LLVMValueRef loadMember(
      AreaAndFileAndLine from,
      GlobalState* globalState,
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
      GlobalState* globalState,
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
      GlobalState* globalState,
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
      GlobalState* globalState,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      LLVMValueRef sourceRefLE,
      StructReferend* sourceStructReferendM,
      Reference* sourceStructTypeM,
      InterfaceReferend* targetInterfaceReferendM,
      Reference* targetInterfaceTypeM) override;

  LLVMValueRef upcast(
      GlobalState* globalState,
      FunctionState* functionState,
      LLVMBuilderRef builder,

      Reference* sourceStructTypeM,
      StructReferend* sourceStructReferendM,
      LLVMValueRef sourceStructLE,

      Reference* targetInterfaceTypeM,
      InterfaceReferend* targetInterfaceReferendM) override;


  void translateKnownSizeArray(
      GlobalState* globalState,
      KnownSizeArrayT* knownSizeArrayMT) override;

  void declareKnownSizeArray(
      GlobalState* globalState,
      KnownSizeArrayT* knownSizeArrayMT) override;

  void declareUnknownSizeArray(
      GlobalState* globalState,
      UnknownSizeArrayT* unknownSizeArrayMT) override;

  void translateUnknownSizeArray(
      GlobalState* globalState,
      UnknownSizeArrayT* unknownSizeArrayMT) override;


  LLVMValueRef lockWeak(
      GlobalState* globalState,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      bool thenResultIsNever,
      bool elseResultIsNever,
      Reference* resultOptTypeM,
//      LLVMTypeRef resultOptTypeL,
      Reference* constraintRefM,
      Reference* sourceWeakRefMT,
      LLVMValueRef sourceWeakRefLE,
      std::function<LLVMValueRef(LLVMBuilderRef, LLVMValueRef)> buildThen,
      std::function<LLVMValueRef(LLVMBuilderRef)> buildElse) override;

  // Returns a LLVMValueRef for a ref to the string object.
  // The caller should then use getStringBytesPtr to then fill the string's contents.
  LLVMValueRef constructString(
      GlobalState* globalState,
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
      GlobalState* globalState,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* referenceM,
      KnownSizeArrayT* referendM,
      const std::vector<LLVMValueRef>& membersLE) override;

  // Returns a LLVMValueRef for a ref to the string object.
  // The caller should then use getStringBytesPtr to then fill the string's contents.
  LLVMValueRef constructUnknownSizeArray(
      GlobalState* globalState,
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
      GlobalState* globalState,
      FunctionState* functionState,
      BlockState* blockState,
      LLVMBuilderRef builder,
      Reference* arrayType,
      LLVMValueRef arrayWrapperLE) override;

  LLVMTypeRef getKnownSizeArrayRefType(
      GlobalState* globalState,
      Reference* referenceM,
      KnownSizeArrayT* knownSizeArrayMT) override;

  LLVMTypeRef getUnknownSizeArrayRefType(
      GlobalState* globalState,
      Reference* referenceM,
      UnknownSizeArrayT* unknownSizeArrayMT) override;

  void checkValidReference(
      AreaAndFileAndLine checkerAFL,
      GlobalState* globalState,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refM,
      LLVMValueRef refLE) override;

  LLVMValueRef loadElement(
      GlobalState* globalState,
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
      GlobalState* globalState,
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



  LLVMTypeRef translateType(GlobalState* globalState, Reference* referenceM) override;


  void declareEdge(
      GlobalState* globalState,
      Edge* edge) override;

  void translateEdge(
      GlobalState* globalState,
      Edge* edge) override;

  LLVMTypeRef getStructRefType(
      GlobalState* globalState,
      Reference* refM,
      StructReferend* structReferendM) override;

  void translateStruct(
      GlobalState* globalState,
      StructDefinition* structM) override;

  void declareStruct(
      GlobalState* globalState,
      StructDefinition* structM) override;

  void translateInterface(
      GlobalState* globalState,
      InterfaceDefinition* interfaceM) override;


  void declareInterface(
      GlobalState* globalState,
      InterfaceDefinition* interfaceM) override;

  LLVMTypeRef getStringRefType() const override;

private:
  LLVMTypeRef translateInterfaceMethodToFunctionType(
      GlobalState* globalState,
      InterfaceMethod* method);
};

#endif
