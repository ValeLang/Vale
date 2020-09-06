#ifndef REGION_ASSIST_ASSIST_H_
#define REGION_ASSIST_ASSIST_H_

#include <llvm-c/Core.h>
#include <function/expressions/shared/afl.h>
#include <region/mega/mega.h>
#include <region/common/fatweaks/fatweaks.h>
#include <region/common/primitives.h>
#include <region/common/defaultrefcounting/defaultrefcounting.h>
#include <region/common/wrcweaks/wrcweaks.h>
#include <region/common/defaultlayout/defaultlayout.h>
#include "globalstate.h"
#include "function/function.h"
#include "../iregion.h"

class Assist : public Mega {
public:
  Assist(GlobalState* globalState);
  ~Assist() override = default;

  LLVMValueRef lockWeak(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      bool thenResultIsNever,
      bool elseResultIsNever,
      Reference* resultOptTypeM,
//      LLVMTypeRef resultOptTypeL,
      Reference* constraintRefMT,
      Reference* sourceWeakRefMT,
      LLVMValueRef sourceWeakRefLE,
      std::function<LLVMValueRef(LLVMBuilderRef, LLVMValueRef)> buildThen,
      std::function<LLVMValueRef(LLVMBuilderRef)> buildElse) override;

  LLVMTypeRef translateType(GlobalState* globalState, Reference* referenceM) override;

  LLVMValueRef upcastWeak(
      FunctionState* functionState,
      LLVMBuilderRef builder,
      LLVMValueRef sourceRefLE,
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

private:
  LLVMTypeRef translateInterfaceMethodToFunctionType(
      InterfaceMethod* method);
};

#endif
