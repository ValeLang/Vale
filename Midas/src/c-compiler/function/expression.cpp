#include <iostream>
#include <region/common/common.h>
#include "utils/branch.h"
#include "function/expressions/shared/elements.h"
#include "region/common/controlblock.h"
#include "function/expressions/shared/members.h"
#include "region/common/heap.h"
#include "function/expressions/shared/weaks.h"

#include "translatetype.h"

#include "expressions/expressions.h"
#include "expressions/shared/shared.h"
#include "expressions/shared/members.h"
#include "expression.h"

Ref loadElementtttFromUSAWithoutUpgrade(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* usaRefMT,
    UnknownSizeArrayT* usaMT,
    Ref arrayRef,
    Ref indexRef) {
  LLVMValueRef arrayElementsPtrLE = nullptr;
  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      arrayElementsPtrLE =
          getUnknownSizeArrayContentsPtr(builder,
              functionState->defaultRegion->makeWrapperPtr(
                  usaRefMT,
                  checkValidReference(FL(), globalState, functionState, builder, usaRefMT, arrayRef)));
      break;
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      switch (usaRefMT->ownership) {
        case Ownership::SHARE:
        case Ownership::OWN:
          arrayElementsPtrLE = getUnknownSizeArrayContentsPtr(builder,
              functionState->defaultRegion->makeWrapperPtr(
                  usaRefMT,
                  checkValidReference(FL(), globalState, functionState, builder, usaRefMT, arrayRef)));
          break;
        case Ownership::BORROW:
          arrayElementsPtrLE = getUnknownSizeArrayContentsPtr(builder, lockWeakRef(FL(), globalState, functionState, builder, usaRefMT, arrayRef));
          break;
        case Ownership::WEAK:
          assert(false); // VIR never loads from a weak ref
        default:
          assert(false);
      }
      break;
    }
    default:
      assert(false);
  }

  auto sizeRef = getUnknownSizeArrayLength(globalState, functionState, builder, usaRefMT, arrayRef);

  return loadElementWithoutUpgrade(
      globalState, functionState, builder, usaRefMT,
      usaMT->rawArray->elementType,
      sizeRef, arrayElementsPtrLE, usaMT->rawArray->mutability, indexRef);
}

Ref loadElementtttFromKSAWithoutUpgrade(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ksaRefMT,
    KnownSizeArrayT* ksaMT,
    Ref arrayRef,
    Ref indexRef) {
  LLVMValueRef arrayElementsPtrLE = nullptr;
  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
    case RegionOverride::FAST: {
      arrayElementsPtrLE =
          getKnownSizeArrayContentsPtr(builder,
          functionState->defaultRegion->makeWrapperPtr(
          ksaRefMT,
          checkValidReference(FL(), globalState, functionState, builder, ksaRefMT, arrayRef)));
      break;
    }
    case RegionOverride::RESILIENT_V0:
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      switch (ksaRefMT->ownership) {
        case Ownership::SHARE:
        case Ownership::OWN:
          arrayElementsPtrLE = getKnownSizeArrayContentsPtr(builder,
              functionState->defaultRegion->makeWrapperPtr(
              ksaRefMT,
              checkValidReference(FL(), globalState, functionState, builder, ksaRefMT, arrayRef)));
          break;
        case Ownership::BORROW:
          arrayElementsPtrLE = getKnownSizeArrayContentsPtr(builder, lockWeakRef(FL(), globalState, functionState, builder, ksaRefMT, arrayRef));
          break;
        case Ownership::WEAK:
          assert(false); // VIR never loads from a weak ref
        default:
          assert(false);
      }
      break;
    }
    default:
      assert(false);
  }

  auto sizeRef =
      wrap(
          functionState->defaultRegion,
          globalState->metalCache.intRef,
          LLVMConstInt(LLVMInt64Type(), ksaMT->size, false));

  return loadElementWithoutUpgrade(
          globalState, functionState, builder, ksaRefMT,
          ksaMT->rawArray->elementType,
      sizeRef, arrayElementsPtrLE, ksaMT->rawArray->mutability, indexRef);
}

Ref loadElementtttFromKSAWithUpgrade(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* ksaRefMT,
    KnownSizeArrayT* ksaMT,
    Ref arrayRef,
    Ref indexRef,
    Reference* targetType) {
  Ref memberRef = loadElementtttFromKSAWithoutUpgrade(globalState, functionState, builder, ksaRefMT,
      ksaMT, arrayRef, indexRef);
  return upgradeLoadResultToRefWithTargetOwnership(
      globalState, functionState, builder, ksaMT->rawArray->elementType, targetType, memberRef);
}

Ref loadElementtttFromUSAWithUpgrade(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* usaRefMT,
    UnknownSizeArrayT* usaMT,
    Ref arrayRef,
    Ref indexRef,
    Reference* targetType) {
  Ref memberRef = loadElementtttFromUSAWithoutUpgrade(globalState, functionState, builder, usaRefMT,
      usaMT, arrayRef, indexRef);
  return upgradeLoadResultToRefWithTargetOwnership(
      globalState, functionState, builder, usaMT->rawArray->elementType, targetType, memberRef);
}

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
    auto resultLE = makeConstIntExpr(functionState, builder, LLVMInt64Type(), constantI64->value);
    return wrap(functionState->defaultRegion, globalState->metalCache.intRef, resultLE);
  } else if (auto constantBool = dynamic_cast<ConstantBool*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    // See ULTMCIE for why this is an add.
    auto resultLE = makeConstIntExpr(functionState, builder, LLVMInt1Type(), constantBool->value);
    return wrap(functionState->defaultRegion, globalState->metalCache.boolRef, resultLE);
  } else if (auto discardM = dynamic_cast<Discard*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    return translateDiscard(globalState, functionState, blockState, builder, discardM);
  } else if (auto ret = dynamic_cast<Return*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto sourceRef = translateExpression(globalState, functionState, blockState, builder, ret->sourceExpr);
    if (ret->sourceType->referend == globalState->metalCache.never) {
      return sourceRef;
    } else {
      auto toReturnLE = checkValidReference(FL(), globalState, functionState, builder, ret->sourceType, sourceRef);
      LLVMBuildRet(builder, toReturnLE);
      return wrap(functionState->defaultRegion, globalState->metalCache.neverRef, globalState->neverPtr);
    }
  } else if (auto stackify = dynamic_cast<Stackify*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto refToStore =
        translateExpression(
            globalState, functionState, blockState, builder, stackify->sourceExpr);
    checkValidReference(FL(), globalState, functionState, builder, stackify->local->type, refToStore);
    if (stackify->local->type->referend == globalState->metalCache.innt) {
      buildFlare(FL(), globalState, functionState, builder, "Storing ", refToStore);
    }
    makeHammerLocal(
        globalState, functionState, blockState, builder, stackify->local, refToStore);
    return makeEmptyTupleRef(globalState, functionState, builder);
  } else if (auto localStore = dynamic_cast<LocalStore*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    // The purpose of LocalStore is to put a swap value into a local, and give
    // what was in it.
    auto localAddr = blockState->getLocalAddr(localStore->local->id);

    auto oldRefLE =
        wrap(
            functionState->defaultRegion,
            localStore->local->type,
            LLVMBuildLoad(builder, localAddr, localStore->localName.c_str()));
    checkValidReference(
        FL(), globalState, functionState, builder, localStore->local->type, oldRefLE);
    auto refToStore =
        translateExpression(
            globalState, functionState, blockState, builder, localStore->sourceExpr);
    if (localStore->local->type->referend == globalState->metalCache.innt) {
      buildFlare(FL(), globalState, functionState, builder, "Storing ", refToStore);
    }
    auto toStoreLE =
        checkValidReference(
            FL(), globalState, functionState, builder, localStore->local->type, refToStore);
    LLVMBuildStore(builder, toStoreLE, localAddr);
    return oldRefLE;
  } else if (auto weakAlias = dynamic_cast<WeakAlias*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());

    auto sourceRef =
        translateExpression(
            globalState, functionState, blockState, builder, weakAlias->sourceExpr);

    checkValidReference(FL(), globalState, functionState, builder, weakAlias->sourceType, sourceRef);

    auto resultRef = functionState->defaultRegion->weakAlias(functionState, builder, weakAlias->sourceType, weakAlias->resultType, sourceRef);
    aliasWeakRef(FL(), globalState, functionState, builder, weakAlias->resultType, resultRef);
    functionState->defaultRegion->dealias(
        AFL("WeakAlias drop constraintref"),
        functionState, blockState, builder, weakAlias->sourceType, sourceRef);
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
    auto resultLE = wrap(functionState->defaultRegion, unstackify->local->type, LLVMBuildLoad(builder, localAddr, ""));
    checkValidReference(FL(), globalState, functionState, builder, unstackify->local->type, resultLE);
    return resultLE;
  } else if (auto argument = dynamic_cast<Argument*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name(), " arg ", argument->argumentIndex);
    auto resultLE = LLVMGetParam(functionState->containingFuncL, argument->argumentIndex);
    auto resultRef = wrap(functionState->defaultRegion, argument->resultType, resultLE);
    checkValidReference(FL(), globalState, functionState, builder, argument->resultType, resultRef);
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
    checkValidReference(FL(), globalState, functionState, builder, newStruct->resultType, resultLE);
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
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto structRef =
        translateExpression(
            globalState, functionState, blockState, builder, memberLoad->structExpr);
    checkValidReference(FL(), globalState, functionState, builder, memberLoad->structType, structRef);
    auto mutability = ownershipToMutability(memberLoad->structType->ownership);
    auto memberIndex = memberLoad->memberIndex;
    auto memberName = memberLoad->memberName;
    auto resultRef =
        loadMember(
            AFL("MemberLoad"),
            globalState,
            functionState,
            builder,
            memberLoad->structType,
            structRef,
            mutability,
            memberLoad->expectedMemberType,
            memberIndex,
            memberLoad->expectedResultType,
            memberName);
    checkValidReference(FL(), globalState, functionState, builder, memberLoad->expectedResultType, resultRef);
    if (memberLoad->expectedResultType->referend == globalState->metalCache.innt) {
      buildFlare(FL(), globalState, functionState, builder, "MemberLoad loaded ", memberName, ": ", resultRef);
    }
    functionState->defaultRegion->dealias(
        AFL("MemberLoad drop struct"),
        functionState, blockState, builder, memberLoad->structType, structRef);
    return resultRef;
  } else if (auto destroyKnownSizeArrayIntoFunction = dynamic_cast<DestroyKnownSizeArrayIntoFunction*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto consumerType = destroyKnownSizeArrayIntoFunction->consumerType;
    auto arrayReferend = destroyKnownSizeArrayIntoFunction->arrayReferend;
    auto arrayExpr = destroyKnownSizeArrayIntoFunction->arrayExpr;
    auto consumerExpr = destroyKnownSizeArrayIntoFunction->consumerExpr;
    auto consumerMethod = destroyKnownSizeArrayIntoFunction->consumerMethod;
    auto arrayType = destroyKnownSizeArrayIntoFunction->arrayType;

    auto sizeRef =
        wrap(
            functionState->defaultRegion,
            globalState->metalCache.intRef,
            LLVMConstInt(LLVMInt64Type(), arrayReferend->size, false));

    auto arrayRef = translateExpression(globalState, functionState, blockState, builder, arrayExpr);

    auto consumerRef = translateExpression(globalState, functionState, blockState, builder, consumerExpr);
    checkValidReference(FL(), globalState, functionState, builder, consumerType, consumerRef);

    foreachArrayElement(
        globalState, functionState, builder, sizeRef,
        [globalState, functionState, blockState, consumerType, consumerMethod, arrayType, arrayReferend, consumerRef, arrayRef](
            Ref indexRef, LLVMBuilderRef bodyBuilder) {
          functionState->defaultRegion->alias(
              AFL("DestroyKSAIntoF consume iteration"),
              functionState, bodyBuilder, consumerType, consumerRef);

          auto elementRef = loadElementtttFromKSAWithoutUpgrade(globalState, functionState,
              bodyBuilder, arrayType, arrayReferend, arrayRef, indexRef);
          checkValidReference(FL(), globalState, functionState, bodyBuilder, arrayReferend->rawArray->elementType, elementRef);
          std::vector<Ref> argExprRefs = { consumerRef, elementRef };
          buildInterfaceCall(globalState, functionState, bodyBuilder, consumerMethod, argExprRefs, 0, 0);
        });

    if (arrayType->ownership == Ownership::OWN) {
      functionState->defaultRegion->discardOwningRef(FL(), functionState, blockState, builder, arrayType, arrayRef);
    } else if (arrayType->ownership == Ownership::SHARE) {
      // We dont decrement anything here, we're only here because we already hit zero.

      Weakability effectiveWeakability = Weakability::WEAKABLE;
      switch (globalState->opt->regionOverride) {
        case RegionOverride::ASSIST:
        case RegionOverride::NAIVE_RC:
        case RegionOverride::FAST:
          effectiveWeakability = Weakability::NON_WEAKABLE;
          break;
        case RegionOverride::RESILIENT_V0:
        case RegionOverride::RESILIENT_V1:
        case RegionOverride::RESILIENT_V2:
          if (arrayType->ownership == Ownership::SHARE) {
            effectiveWeakability = Weakability::NON_WEAKABLE;
          } else {
            effectiveWeakability = Weakability::WEAKABLE;
          }
          break;
        default:
          assert(false);
      }

      auto arrayWrapperLE =
          functionState->defaultRegion->makeWrapperPtr(
              arrayType,
              checkValidReference(
                  FL(), globalState, functionState, builder, arrayType, arrayRef));
      auto controlBlockPtrLE = getConcreteControlBlockPtr(globalState, builder, arrayWrapperLE);
      deallocate(
          AFL("DestroyKSAIntoF"), globalState, functionState, builder,
          controlBlockPtrLE, arrayType);
    } else {
      assert(false);
    }


    functionState->defaultRegion->dealias(
        AFL("DestroyKSAIntoF"), functionState, blockState, builder, consumerType, consumerRef);

    return makeEmptyTupleRef(globalState, functionState, builder);
  } else if (auto destroyUnknownSizeArrayIntoFunction = dynamic_cast<DestroyUnknownSizeArray*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto consumerType = destroyUnknownSizeArrayIntoFunction->consumerType;
    auto arrayReferend = destroyUnknownSizeArrayIntoFunction->arrayReferend;
    auto arrayExpr = destroyUnknownSizeArrayIntoFunction->arrayExpr;
    auto consumerExpr = destroyUnknownSizeArrayIntoFunction->consumerExpr;
    auto consumerMethod = destroyUnknownSizeArrayIntoFunction->consumerMethod;
    auto arrayType = destroyUnknownSizeArrayIntoFunction->arrayType;

    auto arrayRef = translateExpression(globalState, functionState, blockState, builder, arrayExpr);
    checkValidReference(FL(), globalState, functionState, builder, arrayType, arrayRef);
    auto arrayLenRef = getUnknownSizeArrayLength(globalState, functionState, builder, arrayType, arrayRef);
    auto arrayLenLE =
        checkValidReference(
            FL(), globalState, functionState, builder, globalState->metalCache.intRef, arrayLenRef);

    auto consumerRef = translateExpression(globalState, functionState, blockState, builder, consumerExpr);
    checkValidReference(FL(), globalState, functionState, builder, consumerType, consumerRef);

    foreachArrayElement(
        globalState, functionState, builder, arrayLenRef,
        [globalState, functionState, blockState, consumerType, consumerMethod, arrayReferend, arrayType, arrayRef, consumerRef](Ref indexRef, LLVMBuilderRef bodyBuilder) {
          functionState->defaultRegion->alias(
              AFL("DestroyUSAIntoF consume iteration"),
              functionState, bodyBuilder, consumerType, consumerRef);

          auto elementRef = loadElementtttFromUSAWithoutUpgrade(globalState, functionState, bodyBuilder, arrayType, arrayReferend, arrayRef, indexRef);
          std::vector<Ref> argExprRefs = { consumerRef, elementRef };
          buildInterfaceCall(globalState, functionState, bodyBuilder, consumerMethod, argExprRefs, 0, 0);
        });

    if (arrayType->ownership == Ownership::OWN) {
      functionState->defaultRegion->discardOwningRef(FL(), functionState, blockState, builder, arrayType, arrayRef);
    } else if (arrayType->ownership == Ownership::SHARE) {
      // We dont decrement anything here, we're only here because we already hit zero.

      Weakability effectiveWeakability = Weakability::WEAKABLE;
      switch (globalState->opt->regionOverride) {
        case RegionOverride::ASSIST:
        case RegionOverride::NAIVE_RC:
        case RegionOverride::FAST:
          effectiveWeakability = Weakability::NON_WEAKABLE;
          break;
        case RegionOverride::RESILIENT_V0:
        case RegionOverride::RESILIENT_V1:
        case RegionOverride::RESILIENT_V2:
          if (arrayType->ownership == Ownership::SHARE) {
            effectiveWeakability = Weakability::NON_WEAKABLE;
          } else {
            effectiveWeakability = Weakability::WEAKABLE;
          }
          break;
        default:
          assert(false);
      }

      auto arrayWrapperLE =
          functionState->defaultRegion->makeWrapperPtr(
              arrayType,
              checkValidReference(FL(), globalState, functionState, builder, arrayType, arrayRef));
      auto controlBlockPtrLE = getConcreteControlBlockPtr(globalState, builder, arrayWrapperLE);
      // Free it!
      deallocate(AFL("DestroyUSAIntoF"), globalState, functionState, builder,
          controlBlockPtrLE, arrayType);
    } else {
      assert(false);
    }

    functionState->defaultRegion->dealias(
        AFL("DestroyUSAIntoF"), functionState, blockState, builder, consumerType, consumerRef);

    return makeEmptyTupleRef(globalState, functionState, builder);
  } else if (auto knownSizeArrayLoad = dynamic_cast<KnownSizeArrayLoad*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto arrayType = knownSizeArrayLoad->arrayType;
    auto arrayExpr = knownSizeArrayLoad->arrayExpr;
    auto indexExpr = knownSizeArrayLoad->indexExpr;
    auto arrayReferend = knownSizeArrayLoad->arrayReferend;
    auto elementType = arrayReferend->rawArray->elementType;
    auto targetOwnership = knownSizeArrayLoad->targetOwnership;
    auto targetLocation = targetOwnership == Ownership::SHARE ? elementType->location : Location::YONDER;
    auto resultType = globalState->metalCache.getReference(targetOwnership, targetLocation, elementType->referend);

    auto arrayRef = translateExpression(globalState, functionState, blockState, builder, arrayExpr);



    checkValidReference(FL(), globalState, functionState, builder, arrayType, arrayRef);
    auto sizeLE =
        wrap(
            functionState->defaultRegion,
            globalState->metalCache.intRef,
            constI64LE(dynamic_cast<KnownSizeArrayT*>(knownSizeArrayLoad->arrayType->referend)->size));
    auto indexLE = translateExpression(globalState, functionState, blockState, builder, indexExpr);
    auto mutability = ownershipToMutability(arrayType->ownership);
    functionState->defaultRegion->dealias(AFL("KSALoad"), functionState, blockState, builder, arrayType, arrayRef);

    auto resultLE = loadElementtttFromKSAWithUpgrade(globalState, functionState, builder, arrayType, arrayReferend, arrayRef, indexLE, knownSizeArrayLoad->resultType);
    functionState->defaultRegion->alias(FL(), functionState, builder, arrayReferend->rawArray->elementType, resultLE);
    checkValidReference(FL(), globalState, functionState, builder, arrayReferend->rawArray->elementType, resultLE);
    return resultLE;
  } else if (auto unknownSizeArrayLoad = dynamic_cast<UnknownSizeArrayLoad*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto arrayType = unknownSizeArrayLoad->arrayType;
    auto arrayExpr = unknownSizeArrayLoad->arrayExpr;
    auto indexExpr = unknownSizeArrayLoad->indexExpr;
    auto arrayReferend = unknownSizeArrayLoad->arrayReferend;
    auto elementType = arrayReferend->rawArray->elementType;
    auto targetOwnership = unknownSizeArrayLoad->targetOwnership;
    auto targetLocation = targetOwnership == Ownership::SHARE ? elementType->location : Location::YONDER;
    auto resultType = globalState->metalCache.getReference(targetOwnership, targetLocation, elementType->referend);

    auto arrayRef = translateExpression(globalState, functionState, blockState, builder, arrayExpr);

    checkValidReference(FL(), globalState, functionState, builder, arrayType, arrayRef);

    auto sizeLE = getUnknownSizeArrayLength(globalState, functionState, builder, arrayType, arrayRef);
    auto indexLE = translateExpression(globalState, functionState, blockState, builder, indexExpr);
    auto mutability = ownershipToMutability(arrayType->ownership);

    auto resultLE = loadElementtttFromUSAWithUpgrade(globalState, functionState, builder, arrayType, arrayReferend, arrayRef, indexLE, resultType);

    functionState->defaultRegion->alias(FL(), functionState, builder, resultType, resultLE);

    checkValidReference(FL(), globalState, functionState, builder, resultType, resultLE);

    functionState->defaultRegion->dealias(AFL("USALoad"), functionState, blockState, builder, arrayType, arrayRef);

    return resultLE;
  } else if (auto unknownSizeArrayStore = dynamic_cast<UnknownSizeArrayStore*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto arrayType = unknownSizeArrayStore->arrayType;
    auto arrayExpr = unknownSizeArrayStore->arrayExpr;
    auto indexExpr = unknownSizeArrayStore->indexExpr;
    auto arrayReferend = unknownSizeArrayStore->arrayReferend;
    auto elementType = arrayReferend->rawArray->elementType;

    auto arrayRefLE = translateExpression(globalState, functionState, blockState, builder, arrayExpr);
    checkValidReference(FL(), globalState, functionState, builder, arrayType, arrayRefLE);


    auto sizeRef = getUnknownSizeArrayLength(globalState, functionState, builder, arrayType, arrayRefLE);
    checkValidReference(FL(), globalState, functionState, builder, globalState->metalCache.intRef, sizeRef);


    auto indexRef =
        translateExpression(globalState, functionState, blockState, builder, indexExpr);
    auto mutability = ownershipToMutability(arrayType->ownership);



    // The purpose of UnknownSizeArrayStore is to put a swap value into a spot, and give
    // what was in it.
    auto arrayWrapperPtrLE = getUnknownSizeArrayWrapperPtr(globalState, functionState, builder, arrayType, arrayRefLE);
    LLVMValueRef arrayPtrLE = getUnknownSizeArrayContentsPtr(builder, arrayWrapperPtrLE);


    auto oldValueLE =
        loadElementtttFromUSAWithoutUpgrade(
            globalState, functionState, builder, arrayType, arrayReferend,
            arrayRefLE, indexRef);
    checkValidReference(FL(), globalState, functionState, builder, arrayReferend->rawArray->elementType, oldValueLE);
    // We dont acquireReference here because we aren't aliasing the reference, we're moving it out.


    auto valueToStoreLE =
        translateExpression(
            globalState, functionState, blockState, builder, unknownSizeArrayStore->sourceExpr);

    checkValidReference(FL(), globalState, functionState, builder, arrayReferend->rawArray->elementType, valueToStoreLE);


    storeElement(
        globalState, functionState, blockState, builder,
        arrayType, arrayReferend->rawArray->elementType, sizeRef, arrayPtrLE, mutability, indexRef, valueToStoreLE);

    functionState->defaultRegion->dealias(AFL("USAStore"), functionState, blockState, builder, arrayType, arrayRefLE);


    return oldValueLE;
  } else if (auto arrayLength = dynamic_cast<ArrayLength*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto arrayType = arrayLength->sourceType;
    auto arrayExpr = arrayLength->sourceExpr;
//    auto indexExpr = arrayLength->indexExpr;

    auto arrayRefLE = translateExpression(globalState, functionState, blockState, builder, arrayExpr);
    checkValidReference(FL(), globalState, functionState, builder, arrayType, arrayRefLE);

    auto sizeLE = getUnknownSizeArrayLength(globalState, functionState, builder, arrayType, arrayRefLE);
    functionState->defaultRegion->dealias(AFL("USALen"), functionState, blockState, builder, arrayType, arrayRefLE);

    return sizeLE;
  } else if (auto newArrayFromValues = dynamic_cast<NewArrayFromValues*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    return translateNewArrayFromValues(globalState, functionState, blockState, builder, newArrayFromValues);
  } else if (auto constructUnknownSizeArray = dynamic_cast<ConstructUnknownSizeArray*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    return translateConstructUnknownSizeArray(globalState, functionState, blockState, builder, constructUnknownSizeArray);
  } else if (auto call = dynamic_cast<Call*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto resultLE = translateCall(globalState, functionState, blockState, builder, call);
    return resultLE;
  } else if (auto externCall = dynamic_cast<ExternCall*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto resultLE = translateExternCall(globalState, functionState, blockState, builder, externCall);
    return resultLE;
  } else if (auto interfaceCall = dynamic_cast<InterfaceCall*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name(), " ", interfaceCall->functionType->name->name);
    auto resultLE = translateInterfaceCall(globalState, functionState, blockState, builder, interfaceCall);
    if (interfaceCall->functionType->returnType->referend != globalState->metalCache.never) {
      buildFlare(FL(), globalState, functionState, builder, "/", typeid(*expr).name());
    }
    return resultLE;
  } else if (auto memberStore = dynamic_cast<MemberStore*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto structReferend =
        dynamic_cast<StructReferend*>(memberStore->structType->referend);
    auto structDefM = globalState->program->getStruct(structReferend->fullName);
    auto memberIndex = memberStore->memberIndex;
    auto memberName = memberStore->memberName;
    auto structType = memberStore->structType;
    auto memberType = structDefM->members[memberIndex]->type;

    auto sourceExpr =
        translateExpression(
            globalState, functionState, blockState, builder, memberStore->sourceExpr);
    checkValidReference(FL(), globalState, functionState, builder, memberType, sourceExpr);

    auto structExpr =
        translateExpression(
            globalState, functionState, blockState, builder, memberStore->structExpr);
    checkValidReference(FL(), globalState, functionState, builder, memberStore->structType, structExpr);

    auto oldMemberLE =
        swapMember(
            globalState, functionState, builder, structDefM, structType, structExpr, memberIndex, memberName, sourceExpr);
    checkValidReference(FL(), globalState, functionState, builder, memberType, oldMemberLE);
    functionState->defaultRegion->dealias(
        AFL("MemberStore discard struct"), functionState, blockState, builder,
        structType, structExpr);
    return oldMemberLE;
  } else if (auto structToInterfaceUpcast = dynamic_cast<StructToInterfaceUpcast*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto sourceLE =
        translateExpression(
            globalState, functionState, blockState, builder, structToInterfaceUpcast->sourceExpr);
    checkValidReference(
        FL(), globalState, functionState, builder,
        structToInterfaceUpcast->sourceStructType,
        sourceLE);

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

    return upcast(
        globalState,
        functionState,
        builder,
        structToInterfaceUpcast->sourceStructType,
        structToInterfaceUpcast->sourceStructReferend,
        sourceLE,
        structToInterfaceUpcast->targetInterfaceType,
        structToInterfaceUpcast->targetInterfaceReferend);
  } else if (auto lockWeak = dynamic_cast<LockWeak*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());

    auto sourceType = lockWeak->sourceType;
    auto sourceLE =
        translateExpression(
            globalState, functionState, blockState, builder, lockWeak->sourceExpr);
    checkValidReference(FL(), globalState, functionState, builder, sourceType, sourceLE);

    auto sourceTypeAsConstraintRefM =
        globalState->metalCache.getReference(
            Ownership::BORROW,
            sourceType->location,
            sourceType->referend);

    auto isAliveLE = getIsAliveFromWeakRef(globalState, functionState, builder, sourceType, sourceLE);

    auto resultOptTypeLE = functionState->defaultRegion->translateType(lockWeak->resultOptType);

    auto resultOptLE =
        functionState->defaultRegion->lockWeak(functionState, builder,
            false, false, lockWeak->resultOptType,
            sourceTypeAsConstraintRefM,
            sourceType,
            sourceLE,
            [globalState, functionState, lockWeak, sourceLE](LLVMBuilderRef thenBuilder, Ref constraintRef) -> Ref {
              checkValidReference(FL(), globalState, functionState, thenBuilder,
                  lockWeak->someConstructor->params[0],
                  constraintRef);
              functionState->defaultRegion->alias(
                  FL(), functionState, thenBuilder,
                  lockWeak->someConstructor->params[0],
                  constraintRef);
              // If we get here, object is alive, return a Some.
              auto someRef = buildCall(globalState, functionState, thenBuilder, lockWeak->someConstructor, {constraintRef});
              checkValidReference(FL(), globalState, functionState, thenBuilder,
                  lockWeak->someType,
                  someRef);
              return upcast(
                  globalState,
                  functionState,
                  thenBuilder,
                  lockWeak->someType,
                  lockWeak->someReferend,
                  someRef,
                  lockWeak->resultOptType,
                  lockWeak->resultOptReferend);
            },
            [globalState, functionState, lockWeak](LLVMBuilderRef elseBuilder) {
              auto noneConstructor = lockWeak->noneConstructor;
              // If we get here, object is dead, return a None.
              auto noneRef = buildCall(globalState, functionState, elseBuilder, noneConstructor, {});
              checkValidReference(
                  FL(), globalState, functionState, elseBuilder, lockWeak->noneType, noneRef);
              return upcast(
                  globalState,
                  functionState,
                  elseBuilder,
                  lockWeak->noneType,
                  lockWeak->noneReferend,
                  noneRef,
                  lockWeak->resultOptType,
                  lockWeak->resultOptReferend);
            });

    functionState->defaultRegion->dealias(
        AFL("LockWeak drop weak ref"),
        functionState, blockState, builder, sourceType, sourceLE);

    return resultOptLE;
  } else {
    std::string name = typeid(*expr).name();
    std::cout << name << std::endl;
    assert(false);
  }
  assert(false);
}
