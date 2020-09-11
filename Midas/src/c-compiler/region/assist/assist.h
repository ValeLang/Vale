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

class Assist : public Mega {
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
      std::function<Ref(LLVMBuilderRef, Ref)> buildThen,
      std::function<Ref(LLVMBuilderRef)> buildElse) override;

  LLVMTypeRef translateType(Reference* referenceM) override;

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

private:
  LLVMTypeRef translateInterfaceMethodToFunctionType(
      InterfaceMethod* method);
};

#endif