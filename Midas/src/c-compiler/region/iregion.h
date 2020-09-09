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

class IRegion {
public:
  virtual ~IRegion() = default;

  virtual LLVMValueRef allocate(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* desiredReference,
      const std::vector<LLVMValueRef>& membersLE) = 0;

  virtual LLVMValueRef alias(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* sourceRef,
      Reference* targetRef,
      LLVMValueRef expr) = 0;

  virtual void dealias(
      AreaAndFileAndLine from,
      FunctionState* functionState,
      BlockState* blockState,
      LLVMBuilderRef builder,
      Reference* sourceRef,
      LLVMValueRef expr) = 0;

  virtual LLVMValueRef loadMember(
      AreaAndFileAndLine from,
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
      FunctionState* functionState,
      LLVMBuilderRef builder,
      WeakFatPtrLE sourceRefLE,
      StructReferend* sourceStructReferendM,
      Reference* sourceStructTypeM,
      InterfaceReferend* targetInterfaceReferendM,
      Reference* targetInterfaceTypeM) = 0;

  virtual LLVMValueRef upcast(
      FunctionState* functionState,
      LLVMBuilderRef builder,

      Reference* sourceStructTypeM,
      StructReferend* sourceStructReferendM,
      LLVMValueRef sourceStructLE,

      Reference* targetInterfaceTypeM,
      InterfaceReferend* targetInterfaceReferendM) = 0;

  virtual Ref lockWeak(
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
      std::function<Ref(LLVMBuilderRef)> buildElse) = 0;

  // Returns a LLVMValueRef for a ref to the string object.
  // The caller should then use getStringBytesPtr to then fill the string's contents.
  virtual LLVMValueRef constructString(
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
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* referenceM,
      KnownSizeArrayT* referendM,
      const std::vector<LLVMValueRef>& membersLE) = 0;

  // Returns a LLVMValueRef for a ref to the string object.
  // The caller should then use getStringBytesPtr to then fill the string's contents.
  virtual LLVMValueRef constructUnknownSizeArray(
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
      FunctionState* functionState,
      BlockState* blockState,
      LLVMBuilderRef builder,
      Reference* arrayType,
      LLVMValueRef arrayWrapperLE) = 0;

  virtual LLVMTypeRef getKnownSizeArrayRefType(
      Reference* referenceM,
      KnownSizeArrayT* knownSizeArrayMT) = 0;

  virtual LLVMTypeRef getUnknownSizeArrayRefType(
      Reference* referenceM,
      UnknownSizeArrayT* unknownSizeArrayMT) = 0;

  virtual LLVMValueRef checkValidReference(
      AreaAndFileAndLine checkerAFL,
      FunctionState* functionState,
      LLVMBuilderRef builder,
      Reference* refM,
      Ref refLE) = 0;

  virtual LLVMValueRef loadElement(
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

  virtual LLVMTypeRef getStructRefType(
      Reference* refM,
      StructReferend* structReferendM) = 0;

  virtual void translateStruct(
      StructDefinition* structM) = 0;

  virtual void declareStruct(
      StructDefinition* structM) = 0;

  virtual void translateInterface(
      InterfaceDefinition* interfaceM) = 0;

  virtual void declareInterface(
      InterfaceDefinition* interfaceM) = 0;

  virtual LLVMTypeRef getStringRefType() const = 0;

  virtual Ref weakAlias(FunctionState* functionState, LLVMBuilderRef builder, Reference* sourceRefMT, Reference* targetRefMT, Ref sourceRef) = 0;

//  LLVMValueRef initStr, addStr, eqStr, printStr;
};

#endif
