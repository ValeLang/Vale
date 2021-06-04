#include <iostream>
#include <region/common/common.h>
#include "utils/branch.h"
#include "function/expressions/shared/elements.h"
#include "region/common/controlblock.h"
#include "function/expressions/shared/members.h"
#include "region/common/heap.h"

#include "translatetype.h"

#include "expressions/expressions.h"
#include "expressions/shared/shared.h"
#include "expressions/shared/members.h"
#include "expression.h"

Ref translateExpressionInner(
    GlobalState* globalState,
    FunctionState* constraintRef,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Expression* expr);

std::vector<Ref> translateExpressions(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    std::vector<Expression*> exprs) {
  auto result = std::vector<Ref>{};
  result.reserve(exprs.size());
  for (auto expr : exprs) {
    result.push_back(
        translateExpression(globalState, functionState, blockState, builder, expr));
  }
  return result;
}

Ref translateExpression(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Expression* expr) {
  functionState->instructionDepthInAst++;
  auto resultLE = translateExpressionInner(globalState, functionState, blockState, builder, expr);
  functionState->instructionDepthInAst--;
  return resultLE;
}

Ref translateExpressionInner(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Expression* expr) {
  if (auto constantI64 = dynamic_cast<ConstantI64*>(expr)) {
    // See ULTMCIE for why we load and store here.
    auto resultLE = makeConstIntExpr(functionState, builder, LLVMInt64TypeInContext(globalState->context), constantI64->value);
    return wrap(globalState->getRegion(globalState->metalCache->intRef), globalState->metalCache->intRef, resultLE);
  } else if (auto constantFloat = dynamic_cast<ConstantF64*>(expr)) {
    // See ULTMCIE for why we load and store here.

    auto resultLE =
            makeConstExpr(
                functionState,
                builder,
                LLVMConstReal(LLVMDoubleTypeInContext(globalState->context), constantFloat->value));
    assert(LLVMTypeOf(resultLE) == LLVMDoubleTypeInContext(globalState->context));
    return wrap(globalState->getRegion(globalState->metalCache->floatRef), globalState->metalCache->floatRef, resultLE);
  } else if (auto constantBool = dynamic_cast<ConstantBool*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    // See ULTMCIE for why this is an add.
    auto resultLE = makeConstIntExpr(functionState, builder, LLVMInt1TypeInContext(globalState->context), constantBool->value);
    return wrap(globalState->getRegion(globalState->metalCache->boolRef), globalState->metalCache->boolRef, resultLE);
  } else if (auto discardM = dynamic_cast<Discard*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    return translateDiscard(globalState, functionState, blockState, builder, discardM);
  } else if (auto ret = dynamic_cast<Return*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto sourceRef = translateExpression(globalState, functionState, blockState, builder, ret->sourceExpr);
    if (ret->sourceType->kind == globalState->metalCache->never) {
      return sourceRef;
    } else {
      auto toReturnLE =
          globalState->getRegion(ret->sourceType)
              ->checkValidReference(FL(), functionState, builder, ret->sourceType, sourceRef);
      LLVMBuildRet(builder, toReturnLE);
      return wrap(globalState->getRegion(globalState->metalCache->neverRef), globalState->metalCache->neverRef, globalState->neverPtr);
    }
  } else if (auto stackify = dynamic_cast<Stackify*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto refToStore =
        translateExpression(
            globalState, functionState, blockState, builder, stackify->sourceExpr);
    globalState->getRegion(stackify->local->type)
        ->checkValidReference(FL(), functionState, builder, stackify->local->type, refToStore);
    if (stackify->local->type->kind == globalState->metalCache->innt) {
      buildFlare(FL(), globalState, functionState, builder, "Storing ", refToStore);
    }
    makeHammerLocal(
        globalState, functionState, blockState, builder, stackify->local, refToStore, stackify->knownLive);
    return makeEmptyTupleRef(globalState);
  } else if (auto localStore = dynamic_cast<LocalStore*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    // The purpose of LocalStore is to put a swap value into a local, and give
    // what was in it.
    auto localAddr = blockState->getLocalAddr(localStore->local->id);

    auto refToStore =
        translateExpression(
            globalState, functionState, blockState, builder, localStore->sourceExpr);
    if (localStore->local->type->kind == globalState->metalCache->innt) {
      buildFlare(FL(), globalState, functionState, builder, "Storing ", refToStore);
    }

    // We need to load the old ref *after* we evaluate the source expression,
    // Because of expressions like: Ship() = (mut b = (mut a = (mut b = Ship())));
    // See mutswaplocals.vale for test case.
    auto oldRef =
        globalState->getRegion(localStore->local->type)
            ->localStore(functionState, builder, localStore->local, localAddr, refToStore, localStore->knownLive);

    auto toStoreLE =
        globalState->getRegion(localStore->local->type)->checkValidReference(FL(),
            functionState, builder, localStore->local->type, refToStore);
    LLVMBuildStore(builder, toStoreLE, localAddr);
    return oldRef;
  } else if (auto weakAlias = dynamic_cast<WeakAlias*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());

    auto sourceRef =
        translateExpression(
            globalState, functionState, blockState, builder, weakAlias->sourceExpr);

    globalState->getRegion(weakAlias->sourceType)->checkValidReference(FL(), functionState, builder, weakAlias->sourceType, sourceRef);

    auto resultRef = globalState->getRegion(weakAlias->sourceType)->weakAlias(functionState, builder, weakAlias->sourceType, weakAlias->resultType, sourceRef);
    globalState->getRegion(weakAlias->resultType)->aliasWeakRef(FL(), functionState, builder, weakAlias->resultType, resultRef);
    globalState->getRegion(weakAlias->sourceType)->dealias(
        AFL("WeakAlias drop constraintref"),
        functionState, builder, weakAlias->sourceType, sourceRef);
    return resultRef;
  } else if (auto localLoad = dynamic_cast<LocalLoad*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name(), " ", localLoad->localName);

    return translateLocalLoad(globalState, functionState, blockState, builder, localLoad);
  } else if (auto unstackify = dynamic_cast<Unstackify*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    // The purpose of Unstackify is to destroy the local and give what was in
    // it, but in LLVM there's no instruction (or need) for destroying a local.
    // So, we just give what was in it. It's ironically identical to LocalLoad.
    auto localAddr = blockState->getLocalAddr(unstackify->local->id);
    blockState->markLocalUnstackified(unstackify->local->id);
    return globalState->getRegion(unstackify->local->type)->unstackify(functionState, builder, unstackify->local, localAddr);
  } else if (auto argument = dynamic_cast<Argument*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name(), " arg ", argument->argumentIndex);
    auto resultLE = LLVMGetParam(functionState->containingFuncL, argument->argumentIndex);
    auto resultRef = wrap(globalState->getRegion(argument->resultType), argument->resultType, resultLE);
    globalState->getRegion(argument->resultType)->checkValidReference(FL(), functionState, builder, argument->resultType, resultRef);
    buildFlare(FL(), globalState, functionState, builder, "/", typeid(*expr).name());
    return resultRef;
  } else if (auto constantStr = dynamic_cast<ConstantStr*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto resultLE = translateConstantStr(FL(), globalState, functionState, builder, constantStr);
    return resultLE;
  } else if (auto newStruct = dynamic_cast<NewStruct*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto memberExprs =
        translateExpressions(
            globalState, functionState, blockState, builder, newStruct->sourceExprs);
    auto resultLE =
        translateConstruct(
            AFL("NewStruct"), globalState, functionState, builder, newStruct->resultType, memberExprs);
    return resultLE;
  } else if (auto consecutor = dynamic_cast<Consecutor*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto exprs =
        translateExpressions(globalState, functionState, blockState, builder, consecutor->exprs);
    assert(!exprs.empty());
    return exprs.back();
  } else if (auto block = dynamic_cast<Block*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    return translateBlock(globalState, functionState, blockState, builder, block);
  } else if (auto iff = dynamic_cast<If*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    return translateIf(globalState, functionState, blockState, builder, iff);
  } else if (auto whiile = dynamic_cast<While*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    return translateWhile(globalState, functionState, blockState, builder, whiile);
  } else if (auto destructureM = dynamic_cast<Destroy*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    return translateDestructure(globalState, functionState, blockState, builder, destructureM);
  } else if (auto memberLoad = dynamic_cast<MemberLoad*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name(), " ", memberLoad->memberName);
    auto structRef =
        translateExpression(
            globalState, functionState, blockState, builder, memberLoad->structExpr);
    globalState->getRegion(memberLoad->structType)
        ->checkValidReference(FL(), functionState, builder, memberLoad->structType, structRef);
    auto mutability = ownershipToMutability(memberLoad->structType->ownership);
    auto memberIndex = memberLoad->memberIndex;
    auto memberName = memberLoad->memberName;
    bool structKnownLive = memberLoad->structKnownLive || globalState->opt->overrideKnownLiveTrue;
    auto resultRef =
        loadMember(
            AFL("MemberLoad"),
            globalState,
            functionState,
            builder,
            memberLoad->structType,
            structRef,
            structKnownLive,
            mutability,
            memberLoad->expectedMemberType,
            memberIndex,
            memberLoad->expectedResultType,
            memberName);
    globalState->getRegion(memberLoad->expectedResultType)
        ->checkValidReference(FL(), functionState, builder, memberLoad->expectedResultType, resultRef);
    globalState->getRegion(memberLoad->structType)->dealias(
        AFL("MemberLoad drop struct"),
        functionState, builder, memberLoad->structType, structRef);
    return resultRef;
  } else if (auto destroyStaticSizedArrayIntoFunction = dynamic_cast<DestroyStaticSizedArrayIntoFunction*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto consumerType = destroyStaticSizedArrayIntoFunction->consumerType;
    auto arrayKind = destroyStaticSizedArrayIntoFunction->arrayKind;
    auto arrayExpr = destroyStaticSizedArrayIntoFunction->arrayExpr;
    auto consumerExpr = destroyStaticSizedArrayIntoFunction->consumerExpr;
    auto consumerMethod = destroyStaticSizedArrayIntoFunction->consumerMethod;
    auto arrayType = destroyStaticSizedArrayIntoFunction->arrayType;
    auto elementType = destroyStaticSizedArrayIntoFunction->elementType;
    int arraySize = destroyStaticSizedArrayIntoFunction->arraySize;
    bool arrayKnownLive = true;

    auto sizeRef =
        wrap(
            globalState->getRegion(globalState->metalCache->intRef),
            globalState->metalCache->intRef,
            LLVMConstInt(LLVMInt64TypeInContext(globalState->context), arraySize, false));

    auto arrayRef = translateExpression(globalState, functionState, blockState, builder, arrayExpr);

    auto consumerRef = translateExpression(globalState, functionState, blockState, builder, consumerExpr);
    globalState->getRegion(consumerType)
        ->checkValidReference(FL(), functionState, builder, consumerType, consumerRef);

    intRangeLoop(
        globalState, functionState, builder, sizeRef,
        [globalState, functionState, elementType, consumerType, consumerMethod, arrayType, arrayKind, consumerRef, arrayRef, arrayKnownLive](
            Ref indexRef, LLVMBuilderRef bodyBuilder) {
          globalState->getRegion(consumerType)->alias(
              AFL("DestroySSAIntoF consume iteration"),
              functionState, bodyBuilder, consumerType, consumerRef);

          auto elementLoadResult =
              globalState->getRegion(arrayType)->loadElementFromSSA(
                  functionState, bodyBuilder, arrayType, arrayKind, arrayRef, arrayKnownLive, indexRef);
          auto elementRef = elementLoadResult.move();

          globalState->getRegion(elementType)
              ->checkValidReference(
                  FL(), functionState, bodyBuilder, elementType, elementRef);
          std::vector<Ref> argExprRefs = { consumerRef, elementRef };

          auto consumerInterfaceMT = dynamic_cast<InterfaceKind*>(consumerType->kind);
          assert(consumerInterfaceMT);
          int indexInEdge = globalState->getInterfaceMethodIndex(consumerInterfaceMT, consumerMethod);
          auto methodFunctionPtrLE =
              globalState->getRegion(consumerType)
                  ->getInterfaceMethodFunctionPtr(functionState, bodyBuilder, consumerType, consumerRef, indexInEdge);
          buildInterfaceCall(
              globalState, functionState, bodyBuilder, consumerMethod, methodFunctionPtrLE, argExprRefs, 0);
        });

    if (arrayType->ownership == Ownership::OWN) {
      globalState->getRegion(arrayType)
          ->discardOwningRef(FL(), functionState, blockState, builder, arrayType, arrayRef);
    } else if (arrayType->ownership == Ownership::SHARE) {
      // We dont decrement anything here, we're only here because we already hit zero.

      globalState->getRegion(arrayType)
          ->deallocate(
              AFL("DestroySSAIntoF"), functionState, builder, arrayType, arrayRef);
    } else {
      assert(false);
    }


    globalState->getRegion(consumerType)
        ->dealias(
            AFL("DestroySSAIntoF"), functionState, builder, consumerType, consumerRef);

    return makeEmptyTupleRef(globalState);
  } else if (auto destroyRuntimeSizedArrayIntoFunction = dynamic_cast<DestroyRuntimeSizedArray*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto consumerType = destroyRuntimeSizedArrayIntoFunction->consumerType;
    auto arrayKind = destroyRuntimeSizedArrayIntoFunction->arrayKind;
    auto arrayExpr = destroyRuntimeSizedArrayIntoFunction->arrayExpr;
    auto consumerExpr = destroyRuntimeSizedArrayIntoFunction->consumerExpr;
    auto consumerMethod = destroyRuntimeSizedArrayIntoFunction->consumerMethod;
    auto arrayType = destroyRuntimeSizedArrayIntoFunction->arrayType;
    bool arrayKnownLive = true;

    auto arrayRef = translateExpression(globalState, functionState, blockState, builder, arrayExpr);
    globalState->getRegion(arrayType)
        ->checkValidReference(FL(), functionState, builder, arrayType, arrayRef);
    auto arrayLenRef =
        globalState->getRegion(arrayType)
            ->getRuntimeSizedArrayLength(
                functionState, builder, arrayType, arrayRef, arrayKnownLive);
    auto arrayLenLE =
        globalState->getRegion(globalState->metalCache->intRef)
            ->checkValidReference(FL(),
                functionState, builder, globalState->metalCache->intRef, arrayLenRef);

    auto consumerRef = translateExpression(globalState, functionState, blockState, builder, consumerExpr);
    globalState->getRegion(consumerType)
        ->checkValidReference(FL(), functionState, builder, consumerType, consumerRef);

    intRangeLoopReverse(
        globalState, functionState, builder, arrayLenRef,
        [globalState, functionState, consumerType, consumerMethod, arrayKind, arrayType, arrayRef, arrayKnownLive, consumerRef](Ref indexRef, LLVMBuilderRef bodyBuilder) {
          globalState->getRegion(consumerType)
              ->alias(
                  AFL("DestroyRSAIntoF consume iteration"),
                  functionState, bodyBuilder, consumerType, consumerRef);

          auto elementRef =
              globalState->getRegion(arrayType)
                  ->deinitializeElementFromRSA(
                      functionState, bodyBuilder, arrayType, arrayKind, arrayRef, arrayKnownLive, indexRef);
          std::vector<Ref> argExprRefs = { consumerRef, elementRef };

          auto consumerInterfaceMT = dynamic_cast<InterfaceKind*>(consumerType->kind);
          assert(consumerInterfaceMT);
          int indexInEdge = globalState->getInterfaceMethodIndex(consumerInterfaceMT, consumerMethod);
          auto methodFunctionPtrLE =
              globalState->getRegion(consumerType)
                  ->getInterfaceMethodFunctionPtr(functionState, bodyBuilder, consumerType, consumerRef, indexInEdge);
          buildInterfaceCall(globalState, functionState, bodyBuilder, consumerMethod, methodFunctionPtrLE, argExprRefs, 0);
        });

    if (arrayType->ownership == Ownership::OWN) {
      globalState->getRegion(arrayType)
          ->discardOwningRef(FL(), functionState, blockState, builder, arrayType, arrayRef);
    } else if (arrayType->ownership == Ownership::SHARE) {
      // We dont decrement anything here, we're only here because we already hit zero.

      // Free it!
      globalState->getRegion(arrayType)
          ->deallocate(
              AFL("DestroyRSAIntoF"), functionState, builder, arrayType, arrayRef);
    } else {
      assert(false);
    }

    globalState->getRegion(consumerType)
        ->dealias(
            AFL("DestroyRSAIntoF"), functionState, builder, consumerType, consumerRef);

    return makeEmptyTupleRef(globalState);
  } else if (auto staticSizedArrayLoad = dynamic_cast<StaticSizedArrayLoad*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto arrayType = staticSizedArrayLoad->arrayType;
    auto arrayExpr = staticSizedArrayLoad->arrayExpr;
    auto indexExpr = staticSizedArrayLoad->indexExpr;
    auto arrayKind = staticSizedArrayLoad->arrayKind;
    auto elementType = staticSizedArrayLoad->arrayElementType;
    auto targetOwnership = staticSizedArrayLoad->targetOwnership;
    auto targetLocation = targetOwnership == Ownership::SHARE ? elementType->location : Location::YONDER;
    auto resultType =
        globalState->metalCache->getReference(
            targetOwnership, targetLocation, elementType->kind);
    bool arrayKnownLive = staticSizedArrayLoad->arrayKnownLive || globalState->opt->overrideKnownLiveTrue;
    int arraySize = staticSizedArrayLoad->arraySize;

    auto arrayRef = translateExpression(globalState, functionState, blockState, builder, arrayExpr);



    globalState->getRegion(arrayType)
        ->checkValidReference(FL(), functionState, builder, arrayType, arrayRef);
    auto sizeLE =
        wrap(
            globalState->getRegion(globalState->metalCache->intRef),
            globalState->metalCache->intRef,
            constI64LE(globalState, arraySize));
    auto indexLE = translateExpression(globalState, functionState, blockState, builder, indexExpr);
    auto mutability = ownershipToMutability(arrayType->ownership);
    globalState->getRegion(arrayType)
        ->dealias(AFL("SSALoad"), functionState, builder, arrayType, arrayRef);

    auto loadResult =
        globalState->getRegion(arrayType)
            ->loadElementFromSSA(
                functionState, builder, arrayType, arrayKind, arrayRef, arrayKnownLive, indexLE);
    auto resultRef =
        globalState->getRegion(staticSizedArrayLoad->resultType)
            ->upgradeLoadResultToRefWithTargetOwnership(
                functionState, builder, elementType, staticSizedArrayLoad->resultType, loadResult);
    globalState->getRegion(elementType)->checkValidReference(FL(), functionState, builder, staticSizedArrayLoad->resultType, resultRef);
    globalState->getRegion(elementType)
        ->alias(FL(), functionState, builder, staticSizedArrayLoad->resultType, resultRef);
    globalState->getRegion(elementType)
        ->checkValidReference(FL(), functionState, builder, staticSizedArrayLoad->resultType, resultRef);
    return resultRef;
  } else if (auto runtimeSizedArrayLoad = dynamic_cast<RuntimeSizedArrayLoad*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto arrayType = runtimeSizedArrayLoad->arrayType;
    auto arrayExpr = runtimeSizedArrayLoad->arrayExpr;
    auto indexExpr = runtimeSizedArrayLoad->indexExpr;
    auto arrayKind = runtimeSizedArrayLoad->arrayKind;
    auto elementType = runtimeSizedArrayLoad->arrayElementType;
    auto targetOwnership = runtimeSizedArrayLoad->targetOwnership;
    auto targetLocation = targetOwnership == Ownership::SHARE ? elementType->location : Location::YONDER;
    auto resultType = globalState->metalCache->getReference(targetOwnership, targetLocation, elementType->kind);
    bool arrayKnownLive = runtimeSizedArrayLoad->arrayKnownLive || globalState->opt->overrideKnownLiveTrue;

    auto arrayRef = translateExpression(globalState, functionState, blockState, builder, arrayExpr);

    globalState->getRegion(arrayType)
        ->checkValidReference(FL(), functionState, builder, arrayType, arrayRef);

//    auto sizeLE = getRuntimeSizedArrayLength(globalState, functionState, builder, arrayType, arrayRef);
    auto indexLE = translateExpression(globalState, functionState, blockState, builder, indexExpr);
    auto mutability = ownershipToMutability(arrayType->ownership);

    auto loadResult =
        globalState->getRegion(arrayType)->loadElementFromRSA(
            functionState, builder, arrayType, arrayKind, arrayRef, arrayKnownLive, indexLE);
    auto resultRef =
        globalState->getRegion(elementType)
            ->upgradeLoadResultToRefWithTargetOwnership(
                functionState, builder, elementType, resultType, loadResult);

    globalState->getRegion(resultType)
        ->alias(FL(), functionState, builder, resultType, resultRef);

    globalState->getRegion(resultType)
        ->checkValidReference(FL(), functionState, builder, resultType, resultRef);

    globalState->getRegion(arrayType)
        ->dealias(AFL("RSALoad"), functionState, builder, arrayType, arrayRef);

    return resultRef;
  } else if (auto runtimeSizedArrayStore = dynamic_cast<RuntimeSizedArrayStore*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto arrayType = runtimeSizedArrayStore->arrayType;
    auto arrayExpr = runtimeSizedArrayStore->arrayExpr;
    auto indexExpr = runtimeSizedArrayStore->indexExpr;
    auto arrayKind = runtimeSizedArrayStore->arrayKind;
    bool arrayKnownLive = runtimeSizedArrayStore->arrayKnownLive || globalState->opt->overrideKnownLiveTrue;

    auto elementType = globalState->program->getRuntimeSizedArray(arrayKind)->rawArray->elementType;

    auto arrayRefLE = translateExpression(globalState, functionState, blockState, builder, arrayExpr);
    globalState->getRegion(arrayType)
        ->checkValidReference(FL(), functionState, builder, arrayType, arrayRefLE);


    auto sizeRef =
        globalState->getRegion(arrayType)
            ->getRuntimeSizedArrayLength(functionState, builder, arrayType, arrayRefLE, arrayKnownLive);
    globalState->getRegion(globalState->metalCache->intRef)
        ->checkValidReference(FL(), functionState, builder, globalState->metalCache->intRef, sizeRef);


    auto indexRef =
        translateExpression(globalState, functionState, blockState, builder, indexExpr);
    auto mutability = ownershipToMutability(arrayType->ownership);



    // The purpose of RuntimeSizedArrayStore is to put a swap value into a spot, and give
    // what was in it.



    auto valueToStoreLE =
        translateExpression(
            globalState, functionState, blockState, builder, runtimeSizedArrayStore->sourceExpr);

    globalState->getRegion(elementType)
        ->checkValidReference(FL(), functionState, builder, elementType, valueToStoreLE);

    auto loadResult =
        globalState->getRegion(arrayType)->
            loadElementFromRSA(
                functionState, builder, arrayType, arrayKind, arrayRefLE, arrayKnownLive, indexRef);
    auto oldValueLE = loadResult.move();
    globalState->getRegion(elementType)
        ->checkValidReference(FL(), functionState, builder, elementType, oldValueLE);
    // We dont acquireReference here because we aren't aliasing the reference, we're moving it out.

    globalState->getRegion(arrayType)
        ->storeElementInRSA(
            functionState, builder,
            arrayType, arrayKind, arrayRefLE, arrayKnownLive, indexRef, valueToStoreLE);

    globalState->getRegion(arrayType)
        ->dealias(AFL("RSAStore"), functionState, builder, arrayType, arrayRefLE);

    return oldValueLE;
  } else if (auto arrayLength = dynamic_cast<ArrayLength*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto arrayType = arrayLength->sourceType;
    auto arrayExpr = arrayLength->sourceExpr;
    bool arrayKnownLive = arrayLength->sourceKnownLive || globalState->opt->overrideKnownLiveTrue;
//    auto indexExpr = arrayLength->indexExpr;

    auto arrayRefLE = translateExpression(globalState, functionState, blockState, builder, arrayExpr);
    globalState->getRegion(arrayType)
        ->checkValidReference(FL(), functionState, builder, arrayType, arrayRefLE);

    auto sizeLE =
        globalState->getRegion(arrayType)
            ->getRuntimeSizedArrayLength(
                functionState, builder, arrayType, arrayRefLE, arrayKnownLive);
    globalState->getRegion(arrayType)
        ->dealias(AFL("RSALen"), functionState, builder, arrayType, arrayRefLE);

    return sizeLE;
  } else if (auto narrowPermission = dynamic_cast<NarrowPermission*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto sourceExpr = narrowPermission->sourceExpr;
    return translateExpression(globalState, functionState, blockState, builder, sourceExpr);
  } else if (auto newArrayFromValues = dynamic_cast<NewArrayFromValues*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    return translateNewArrayFromValues(globalState, functionState, blockState, builder, newArrayFromValues);
  } else if (auto constructRuntimeSizedArray = dynamic_cast<ConstructRuntimeSizedArray*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    return translateConstructRuntimeSizedArray(globalState, functionState, blockState, builder, constructRuntimeSizedArray);
  } else if (auto staticArrayFromCallable = dynamic_cast<StaticArrayFromCallable*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    return translateStaticArrayFromCallable(globalState, functionState, blockState, builder, staticArrayFromCallable);
  } else if (auto call = dynamic_cast<Call*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name(), " ", call->function->name->name);
    auto resultLE = translateCall(globalState, functionState, blockState, builder, call);
    return resultLE;
  } else if (auto externCall = dynamic_cast<ExternCall*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto resultLE = translateExternCall(globalState, functionState, blockState, builder, externCall);
    return resultLE;
  } else if (auto interfaceCall = dynamic_cast<InterfaceCall*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name(), " ", interfaceCall->functionType->name->name);
    auto resultLE = translateInterfaceCall(globalState, functionState, blockState, builder, interfaceCall);
    if (interfaceCall->functionType->returnType->kind != globalState->metalCache->never) {
      buildFlare(FL(), globalState, functionState, builder, "/", typeid(*expr).name());
    }
    return resultLE;
  } else if (auto memberStore = dynamic_cast<MemberStore*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto structKind =
        dynamic_cast<StructKind*>(memberStore->structType->kind);
    auto structDefM = globalState->program->getStruct(structKind);
    auto memberIndex = memberStore->memberIndex;
    auto memberName = memberStore->memberName;
    auto structType = memberStore->structType;
    auto memberType = structDefM->members[memberIndex]->type;
    bool structKnownLive = memberStore->structKnownLive || globalState->opt->overrideKnownLiveTrue;

    auto sourceExpr =
        translateExpression(
            globalState, functionState, blockState, builder, memberStore->sourceExpr);
    globalState->getRegion(memberType)
        ->checkValidReference(FL(), functionState, builder, memberType, sourceExpr);

    auto structExpr =
        translateExpression(
            globalState, functionState, blockState, builder, memberStore->structExpr);
    globalState->getRegion(memberStore->structType)
        ->checkValidReference(FL(), functionState, builder, memberStore->structType, structExpr);

    auto oldMemberLE =
        swapMember(
            globalState, functionState, builder, structDefM, structType, structExpr, structKnownLive, memberIndex, memberName, sourceExpr);
    globalState->getRegion(memberType)
        ->checkValidReference(FL(), functionState, builder, memberType, oldMemberLE);
    globalState->getRegion(structType)
        ->dealias(
            AFL("MemberStore discard struct"),
            functionState, builder, structType, structExpr);
    return oldMemberLE;
  } else if (auto structToInterfaceUpcast = dynamic_cast<StructToInterfaceUpcast*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto sourceLE =
        translateExpression(
            globalState, functionState, blockState, builder, structToInterfaceUpcast->sourceExpr);
    globalState->getRegion(structToInterfaceUpcast->sourceStructType)
        ->checkValidReference(
            FL(), functionState, builder, structToInterfaceUpcast->sourceStructType, sourceLE);

    // If it was inline before, upgrade it to a yonder struct.
    // This however also means that small imm virtual params must be pointers,
    // and value-ify themselves immediately inside their bodies.
    // If the receiver expects a yonder, then they'll assume its on the heap.
    // But if receiver expects an inl, its in a register.
    // But we can only interfacecall with a yonder.
    // So we need a thunk to receive that yonder, copy it, fire it into the
    // real function.
    // fuck... thunks. didnt want to do that.

    // alternative:
    // what if we made it so someone receiving an override of an imm inl interface
    // just takes in that much memory? it really just means a bit of wasted stack
    // space, but it means we wouldnt need any thunking.
    // It also means we wouldnt need any heap allocating.
    // So, the override function will receive the entire interface, and just
    // assume that the right thing is in there.
    // Any callers will also have to wrap in an interface. but theyre copying
    // anyway so should be fine.

    // alternative:
    // only inline primitives. Which cant have interfaces anyway.
    // maybe the best solution for now?

    // maybe function params that are inl can take a pointer, and they can
    // just copy it immediately?

    return globalState->getRegion(structToInterfaceUpcast->sourceStructType)
        ->upcast(
            functionState,
            builder,
            structToInterfaceUpcast->sourceStructType,
            structToInterfaceUpcast->sourceStructKind,
            sourceLE,
            structToInterfaceUpcast->targetInterfaceType,
            structToInterfaceUpcast->targetInterfaceKind);
  } else if (auto lockWeak = dynamic_cast<LockWeak*>(expr)) {
    bool sourceKnownLive = lockWeak->sourceKnownLive || globalState->opt->overrideKnownLiveTrue;

    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());

    auto sourceType = lockWeak->sourceType;
    auto sourceLE =
        translateExpression(
            globalState, functionState, blockState, builder, lockWeak->sourceExpr);
    globalState->getRegion(sourceType)
        ->checkValidReference(FL(), functionState, builder, sourceType, sourceLE);

    auto sourceTypeAsConstraintRefM =
        globalState->metalCache->getReference(
            Ownership::BORROW,
            sourceType->location,
            sourceType->kind);

    auto resultOptTypeLE =
        globalState->getRegion(lockWeak->resultOptType)
            ->translateType(lockWeak->resultOptType);

    auto resultOptLE =
        globalState->getRegion(sourceType)->lockWeak(
            functionState, builder,
            false, false, lockWeak->resultOptType,
            sourceTypeAsConstraintRefM,
            sourceType,
            sourceLE,
            sourceKnownLive,
            [globalState, functionState, lockWeak, sourceLE](LLVMBuilderRef thenBuilder, Ref constraintRef) -> Ref {
              globalState->getRegion(lockWeak->someConstructor->params[0])
                  ->checkValidReference(
                      FL(), functionState, thenBuilder,
                      lockWeak->someConstructor->params[0],
                      constraintRef);
              globalState->getRegion(lockWeak->someConstructor->params[0])
                  ->alias(
                      FL(), functionState, thenBuilder,
                      lockWeak->someConstructor->params[0],
                      constraintRef);
              // If we get here, object is alive, return a Some.
              auto someRef = buildCall(globalState, functionState, thenBuilder, lockWeak->someConstructor, {constraintRef});
              globalState->getRegion(lockWeak->someType)
                  ->checkValidReference(
                      FL(), functionState, thenBuilder, lockWeak->someType, someRef);
              return globalState->getRegion(lockWeak->someType)
                  ->upcast(
                      functionState,
                      thenBuilder,
                      lockWeak->someType,
                      lockWeak->someKind,
                      someRef,
                      lockWeak->resultOptType,
                      lockWeak->resultOptKind);
            },
            [globalState, functionState, lockWeak](LLVMBuilderRef elseBuilder) {
              auto noneConstructor = lockWeak->noneConstructor;
              // If we get here, object is dead, return a None.
              auto noneRef = buildCall(globalState, functionState, elseBuilder, noneConstructor, {});
              globalState->getRegion(lockWeak->noneType)
                  ->checkValidReference(
                      FL(), functionState, elseBuilder, lockWeak->noneType, noneRef);
              return globalState->getRegion(lockWeak->noneType)
                  ->upcast(
                      functionState,
                      elseBuilder,
                      lockWeak->noneType,
                      lockWeak->noneKind,
                      noneRef,
                      lockWeak->resultOptType,
                      lockWeak->resultOptKind);
            });

    globalState->getRegion(sourceType)->dealias(
        AFL("LockWeak drop weak ref"),
        functionState, builder, sourceType, sourceLE);

    return resultOptLE;
  } else if (auto asSubtype = dynamic_cast<AsSubtype*>(expr)) {
    bool sourceKnownLive = asSubtype->sourceKnownLive || globalState->opt->overrideKnownLiveTrue;

    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());

    auto sourceType = asSubtype->sourceType;
    auto sourceLE =
        translateExpression(
            globalState, functionState, blockState, builder, asSubtype->sourceExpr);
    globalState->getRegion(sourceType)
        ->checkValidReference(FL(), functionState, builder, sourceType, sourceLE);

    auto sourceTypeAsConstraintRefM =
        globalState->metalCache->getReference(
            Ownership::BORROW,
            sourceType->location,
            sourceType->kind);

    auto resultResultTypeLE =
        globalState->getRegion(asSubtype->resultResultType)
            ->translateType(asSubtype->resultResultType);

    auto resultOptLE =
        globalState->getRegion(sourceType)->asSubtype(
            functionState, builder,
            false, false, asSubtype->resultResultType,
            sourceTypeAsConstraintRefM,
            sourceType,
            sourceLE,
            sourceKnownLive,
            asSubtype->targetKind,
            [globalState, functionState, asSubtype, sourceType, sourceLE](LLVMBuilderRef thenBuilder, Ref refAsSubtype) -> Ref {
              globalState->getRegion(asSubtype->okConstructor->params[0])
                  ->checkValidReference(
                      FL(), functionState, thenBuilder,
                      asSubtype->okConstructor->params[0],
                      refAsSubtype);

              // If we get here, object is of the desired targetType, return a Ok containing it.
              auto okRef = buildCall(globalState, functionState, thenBuilder, asSubtype->okConstructor, {refAsSubtype});
              globalState->getRegion(asSubtype->okType)
                  ->checkValidReference(
                      FL(), functionState, thenBuilder, asSubtype->okType, okRef);
              return globalState->getRegion(asSubtype->okType)
                  ->upcast(
                      functionState,
                      thenBuilder,
                      asSubtype->okType,
                      asSubtype->okKind,
                      okRef,
                      asSubtype->resultResultType,
                      asSubtype->resultResultKind);
            },
            [globalState, functionState, asSubtype, sourceLE](LLVMBuilderRef thenBuilder) -> Ref {
              // If we get here, object is not of the desired targetType, return a Err containing the original ref.
              auto errRef = buildCall(globalState, functionState, thenBuilder, asSubtype->errConstructor, {sourceLE});
              globalState->getRegion(asSubtype->errType)
                  ->checkValidReference(
                      FL(), functionState, thenBuilder, asSubtype->errType, errRef);
              return globalState->getRegion(asSubtype->errType)
                  ->upcast(
                      functionState,
                      thenBuilder,
                      asSubtype->errType,
                      asSubtype->errKind,
                      errRef,
                      asSubtype->resultResultType,
                      asSubtype->resultResultKind);
            });

    return resultOptLE;
  } else {
    std::string name = typeid(*expr).name();
    std::cout << name << std::endl;
    assert(false);
  }
  assert(false);
}
