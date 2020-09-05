#ifndef REGION_IREGION_H_
#define REGION_IREGION_H_

#include <llvm-c/Core.h>
#include <function/expressions/shared/afl.h>

class FunctionState;
class BlockState;
class GlobalState;

class IRegion {
public:
  virtual ~IRegion() = default;

  virtual LLVMValueRef allocate(
      AreaAndFileAndLine from,
      GlobalState* globalState,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* desiredReference,
      const std::vector<LLVMValueRef>& membersLE) = 0;

  virtual LLVMValueRef alias(
      AreaAndFileAndLine from,
      GlobalState* globalState,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceRef,
      Reference* targetRef,
      LLVMValueRef expr) = 0;

  virtual void dealias(
      AreaAndFileAndLine from,
      GlobalState* globalState,
      FunctionState* functionState,
      BlockState* blockState,
      LLVMBuilderRef builder,
      Reference* sourceRef,
      LLVMValueRef expr) = 0;

  virtual LLVMValueRef loadMember(
      AreaAndFileAndLine from,
      GlobalState* globalState,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* structRefM,
      LLVMValueRef structExpr,
      Mutability mutability,
      Reference* memberType,
      int memberIndex,
      const std::string& memberName) = 0;

  virtual LLVMValueRef storeMember(
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
      LLVMValueRef sourceLE) = 0;

  virtual std::vector<LLVMValueRef> destructure(
      GlobalState* globalState,
      FunctionState* functionState,
      BlockState* blockState,
      LLVMBuilderRef builder,
      Reference* structType,
      LLVMValueRef structLE) = 0;

  // Suitable for passing in to an interface method
  virtual LLVMValueRef getConcreteRefFromInterfaceRef(
      LLVMBuilderRef builder,
      LLVMValueRef refLE) = 0;

  virtual LLVMValueRef upcastWeak(
      GlobalState* globalState,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      LLVMValueRef sourceRefLE,
      StructReferend* sourceStructReferendM,
      Reference* sourceStructTypeM,
      InterfaceReferend* targetInterfaceReferendM,
      Reference* targetInterfaceTypeM) = 0;

  virtual LLVMValueRef upcast(
      GlobalState* globalState,
      FunctionState* functionState,
      LLVMBuilderRef builder,

      Reference* sourceStructTypeM,
      StructReferend* sourceStructReferendM,
      LLVMValueRef sourceStructLE,

      Reference* targetInterfaceTypeM,
      InterfaceReferend* targetInterfaceReferendM) = 0;

  virtual LLVMValueRef lockWeak(
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
      std::function<LLVMValueRef(LLVMBuilderRef)> buildElse) = 0;

  // Returns a LLVMValueRef for a ref to the string object.
  // The caller should then use getStringBytesPtr to then fill the string's contents.
  virtual LLVMValueRef constructString(
      GlobalState* globalState,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      LLVMValueRef lengthLE) = 0;

  // Returns a LLVMValueRef for a pointer to the strings contents bytes
  virtual LLVMValueRef getStringBytesPtr(
      LLVMBuilderRef builder,
      LLVMValueRef stringRefLE) = 0;

  virtual LLVMValueRef getStringLength(
      LLVMBuilderRef builder,
      LLVMValueRef stringRefLE) = 0;

  // Returns a LLVMValueRef for a ref to the string object.
  // The caller should then use getStringBytesPtr to then fill the string's contents.
  virtual LLVMValueRef constructKnownSizeArray(
      GlobalState* globalState,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* referenceM,
      KnownSizeArrayT* referendM,
      const std::vector<LLVMValueRef>& membersLE) = 0;

  // Returns a LLVMValueRef for a ref to the string object.
  // The caller should then use getStringBytesPtr to then fill the string's contents.
  virtual LLVMValueRef constructUnknownSizeArray(
      GlobalState* globalState,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* usaMT,
      LLVMValueRef sizeLE,
      const std::string& typeName) = 0;

  // should expose a dereference thing instead
//  virtual LLVMValueRef getKnownSizeArrayElementsPtr(
//      LLVMBuilderRef builder,
//      LLVMValueRef knownSizeArrayWrapperPtrLE) = 0;
//  virtual LLVMValueRef getUnknownSizeArrayElementsPtr(
//      LLVMBuilderRef builder,
//      LLVMValueRef unknownSizeArrayWrapperPtrLE) = 0;
//  virtual LLVMValueRef getUnknownSizeArrayLength(
//      LLVMBuilderRef builder,
//      LLVMValueRef unknownSizeArrayWrapperPtrLE) = 0;

  virtual void destroyArray(
      GlobalState* globalState,
      FunctionState* functionState,
      BlockState* blockState,
      LLVMBuilderRef builder,
      Reference* arrayType,
      LLVMValueRef arrayWrapperLE) = 0;

  virtual LLVMTypeRef getKnownSizeArrayRefType(
      GlobalState* globalState,
      Reference* referenceM,
      KnownSizeArrayT* knownSizeArrayMT) = 0;

  virtual LLVMTypeRef getUnknownSizeArrayRefType(
      GlobalState* globalState,
      Reference* referenceM,
      UnknownSizeArrayT* unknownSizeArrayMT) = 0;

  virtual void checkValidReference(
      AreaAndFileAndLine checkerAFL,
      GlobalState* globalState,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refM,
      LLVMValueRef refLE) = 0;

  virtual LLVMValueRef loadElement(
      GlobalState* globalState,
      FunctionState* functionState,
      BlockState* blockState,
      LLVMBuilderRef builder,
      Reference* structRefM,
      Reference* elementRefM,
      LLVMValueRef sizeIntLE,
      LLVMValueRef arrayCRefLE,
      Mutability mutability,
      LLVMValueRef indexIntLE) = 0;

  virtual LLVMValueRef storeElement(
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
      LLVMValueRef sourceLE) = 0;

  virtual LLVMTypeRef translateType(GlobalState* globalState, Reference* referenceM) = 0;

  virtual void translateKnownSizeArray(
      GlobalState* globalState,
      KnownSizeArrayT* knownSizeArrayMT) = 0;

  virtual void declareKnownSizeArray(
      GlobalState* globalState,
      KnownSizeArrayT* knownSizeArrayMT) = 0;

  virtual void declareUnknownSizeArray(
      GlobalState* globalState,
      UnknownSizeArrayT* unknownSizeArrayMT) = 0;

  virtual void translateUnknownSizeArray(
      GlobalState* globalState,
      UnknownSizeArrayT* unknownSizeArrayMT) = 0;


  virtual void declareEdge(
      GlobalState* globalState,
      Edge* edge) = 0;

  virtual void translateEdge(
      GlobalState* globalState,
      Edge* edge) = 0;

  virtual LLVMTypeRef getStructRefType(
      GlobalState* globalState,
      Reference* refM,
      StructReferend* structReferendM) = 0;

  virtual void translateStruct(
      GlobalState* globalState,
      StructDefinition* structM) = 0;

  virtual void declareStruct(
      GlobalState* globalState,
      StructDefinition* structM) = 0;

  virtual void translateInterface(
      GlobalState* globalState,
      InterfaceDefinition* interfaceM) = 0;

  virtual void declareInterface(
      GlobalState* globalState,
      InterfaceDefinition* interfaceM) = 0;

  virtual LLVMTypeRef getStringRefType() const = 0;

//  LLVMValueRef initStr, addStr, eqStr, printStr;
};

#endif
