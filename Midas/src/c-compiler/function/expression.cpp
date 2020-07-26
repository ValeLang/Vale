#include <iostream>
#include "function/expressions/shared/branch.h"
#include "function/expressions/shared/elements.h"
#include "function/expressions/shared/controlblock.h"
#include "function/expressions/shared/members.h"
#include "function/expressions/shared/heap.h"

#include "translatetype.h"

#include "expressions/expressions.h"
#include "expressions/shared/shared.h"
#include "expressions/shared/members.h"
#include "expression.h"

LLVMValueRef translateExpressionInner(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Expression* expr);

std::vector<LLVMValueRef> translateExpressions(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    std::vector<Expression*> exprs) {
  auto result = std::vector<LLVMValueRef>{};
  result.reserve(exprs.size());
  for (auto expr : exprs) {
    result.push_back(
        translateExpression(globalState, functionState, blockState, builder, expr));
  }
  return result;
}

LLVMValueRef translateExpression(
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

LLVMValueRef assembleStructWeakRef(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Reference* structTypeM,
    StructReferend* structReferendM,
    LLVMValueRef objPtrLE) {
  auto controlBlockPtrLE = getControlBlockPtr(builder, objPtrLE, structTypeM);
  auto wrciLE = getWrciFromControlBlockPtr(globalState, builder, controlBlockPtrLE);
  LLVMBuildCall(builder, globalState->incrementWrc, &wrciLE, 1, "");

  auto weakRefLE = LLVMGetUndef(globalState->getStructWeakRefStruct(structReferendM->fullName));
  weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, wrciLE, WEAK_REF_RCINDEX_MEMBER_INDEX, "");
  weakRefLE = LLVMBuildInsertValue(builder, weakRefLE, objPtrLE, WEAK_REF_OBJPTR_MEMBER_INDEX, "");
  return weakRefLE;
}

LLVMValueRef translateExpressionInner(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Expression* expr) {
  if (auto constantI64 = dynamic_cast<ConstantI64*>(expr)) {
    // See ULTMCIE for why we load and store here.
    return makeConstIntExpr(builder, LLVMInt64Type(), constantI64->value);
  } else if (auto constantBool = dynamic_cast<ConstantBool*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    // See ULTMCIE for why this is an add.
    return makeConstIntExpr(builder, LLVMInt1Type(), constantBool->value);
  } else if (auto discardM = dynamic_cast<Discard*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name(), " type ", discardM->sourceResultType->str());
    return translateDiscard(globalState, functionState, blockState, builder, discardM);
  } else if (auto ret = dynamic_cast<Return*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto sourceLE = translateExpression(globalState, functionState, blockState, builder, ret->sourceExpr);
    checkValidReference(FL(), globalState, functionState, builder, ret->sourceType, sourceLE);
    if (ret->sourceType->referend == globalState->metalCache.never) {
      return sourceLE;
    } else {
      return LLVMBuildRet(builder, sourceLE);
    }
  } else if (auto stackify = dynamic_cast<Stackify*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name(), " ", stackify->local->id->maybeName, " type ", stackify->local->type->str());
    auto valueToStoreLE =
        translateExpression(
            globalState, functionState, blockState, builder, stackify->sourceExpr);
    checkValidReference(FL(), globalState, functionState, builder, stackify->local->type, valueToStoreLE);
    makeLocal(
        globalState, functionState, blockState, builder, stackify->local, valueToStoreLE);
    return makeConstExpr(builder, makeNever());
  } else if (auto localStore = dynamic_cast<LocalStore*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    // The purpose of LocalStore is to put a swap value into a local, and give
    // what was in it.
    auto localAddr = blockState->getLocalAddr(localStore->local->id);
    auto oldValueLE =
        LLVMBuildLoad(builder, localAddr, localStore->localName.c_str());
    checkValidReference(FL(), globalState, functionState, builder, localStore->local->type, oldValueLE);
    auto valueToStoreLE =
        translateExpression(
            globalState, functionState, blockState, builder, localStore->sourceExpr);
    checkValidReference(FL(), globalState, functionState, builder, localStore->local->type, valueToStoreLE);
    LLVMBuildStore(builder, valueToStoreLE, localAddr);
    return oldValueLE;
  } else if (auto weakAlias = dynamic_cast<WeakAlias*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name(), " ", weakAlias->sourceType->str());

    if (weakAlias->sourceType->ownership == Ownership::SHARE) {
      assert(false);
    } else if (weakAlias->sourceType->ownership == Ownership::OWN) {
      assert(false);
    } else if (weakAlias->sourceType->ownership == Ownership::BORROW) {
      if (auto structReferendM = dynamic_cast<StructReferend*>(weakAlias->sourceType->referend)) {
        auto objPtrLE =
            translateExpression(
                globalState, functionState, blockState, builder, weakAlias->sourceExpr);
        auto resultLE =
            assembleStructWeakRef(
                globalState, builder, weakAlias->sourceType, structReferendM, objPtrLE);
        discard(
            AFL("WeakAlias drop constraintref"),
            globalState, functionState, blockState, builder, weakAlias->sourceType, objPtrLE);
        return resultLE;
      } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend*>(weakAlias->sourceType->referend)) {
        assert(false); // impl
      } else assert(false);
    } else if (weakAlias->sourceType->ownership == Ownership::WEAK) {
      assert(false);
    } else {
      assert(false);
    }
  } else if (auto localLoad = dynamic_cast<LocalLoad*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name(), " ", localLoad->local->id->maybeName, " type ", localLoad->local->type->str());

    auto localAddr = blockState->getLocalAddr(localLoad->local->id);

    if (localLoad->local->type->ownership == Ownership::SHARE) {
      if (localLoad->local->type->location == Location::INLINE) {
        auto resultLE = LLVMBuildLoad(builder, localAddr, localLoad->localName.c_str());
        checkValidReference(FL(), globalState, functionState, builder, localLoad->local->type, resultLE);
        return resultLE;
      } else {
        auto resultLE =
            LLVMBuildLoad(builder, localAddr, localLoad->localName.c_str());
        checkValidReference(FL(), globalState, functionState, builder, localLoad->local->type, resultLE);
        adjustStrongRc(
            AFL("LocalLoad"),
            globalState, functionState, builder, resultLE, localLoad->local->type, 1);
        return resultLE;
      }
    } else if (localLoad->local->type->ownership == Ownership::OWN) {
      if (localLoad->targetOwnership == Ownership::OWN) {
        // Cant load an owning reference from a owning local. That would require an unstackify.
        assert(false);
      } else if (localLoad->targetOwnership == Ownership::BORROW) {
        // We do the same thing for inline and yonder muts, the only difference is
        // where the memory lives.

        auto resultLE = LLVMBuildLoad(builder, localAddr, localLoad->localName.c_str());
        checkValidReference(
            FL(), globalState, functionState, builder, localLoad->local->type, resultLE);
        adjustStrongRc(AFL("LocalLoad"), globalState, functionState, builder, resultLE, localLoad->local->type, 1);
        return resultLE;
      } else if (localLoad->targetOwnership == Ownership::WEAK) {
        auto sourceRefLE = LLVMBuildLoad(builder, localAddr, localLoad->localName.c_str());
        checkValidReference(
            FL(), globalState, functionState, builder, localLoad->local->type, sourceRefLE);

        // Now we need to package it up into a weak ref.
        if (auto structReferend = dynamic_cast<StructReferend*>(localLoad->local->type->referend)) {
          auto objPtrLE = sourceRefLE;

          return assembleStructWeakRef(
              globalState, builder, localLoad->local->type, structReferend, objPtrLE);
        } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend*>(localLoad->local->type->referend)) {
          assert(false); // impl
        } else assert(false);
      } else {
        assert(false);
      }
    } else if (localLoad->local->type->ownership == Ownership::BORROW) {
      if (localLoad->targetOwnership == Ownership::OWN) {
        assert(false); // Cant load an owning reference from a constraint ref local.
      } else if (localLoad->targetOwnership == Ownership::BORROW) {
        // We do the same thing for inline and yonder muts, the only difference is
        // where the memory lives.
        auto resultLE = LLVMBuildLoad(builder, localAddr, localLoad->localName.c_str());
        checkValidReference(
            FL(), globalState, functionState, builder, localLoad->local->type, resultLE);
        adjustStrongRc(AFL("LocalLoad"), globalState, functionState, builder, resultLE, localLoad->local->type, 1);
        return resultLE;
      } else if (localLoad->targetOwnership == Ownership::WEAK) {
        // Making a weak ref from a constraint ref local.

        if (auto structReferendM = dynamic_cast<StructReferend*>(localLoad->local->type->referend)) {
          // We do the same thing for inline and yonder muts, the only difference is
          // where the memory lives.
          auto objPtrLE = LLVMBuildLoad(builder, localAddr, localLoad->localName.c_str());
          return assembleStructWeakRef(
              globalState, builder, localLoad->local->type, structReferendM, objPtrLE);
        } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend*>(localLoad->local->type->referend)) {
          assert(false); // impl
        } else assert(false);
      } else {
        assert(false);
      }
    } else if (localLoad->local->type->ownership == Ownership::WEAK) {
      if (localLoad->targetOwnership == Ownership::OWN) {
        assert(false); // Cant load an owning reference from a weak ref local.
      } else if (localLoad->targetOwnership == Ownership::BORROW) {
        assert(false); // Can't implicitly make a constraint ref from a weak ref.
      } else if (localLoad->targetOwnership == Ownership::WEAK) {
        // We do the same thing for inline and yonder muts, the only difference is
        // where the memory lives.
        auto weakRefLE = LLVMBuildLoad(builder, localAddr, localLoad->localName.c_str());
        checkValidReference(
            FL(), globalState, functionState, builder, localLoad->local->type, weakRefLE);
        auto wrciLE = getWrciFromWeakRef(builder, weakRefLE);
        LLVMBuildCall(builder, globalState->incrementWrc, &wrciLE, 1, "");
        return weakRefLE;
      } else {
        assert(false);
      }
    } else {
      assert(false);
    }
  } else if (auto unstackify = dynamic_cast<Unstackify*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name(), " ", unstackify->local->id->maybeName, " type ", unstackify->local->type->str());
    // The purpose of Unstackify is to destroy the local and give what was in
    // it, but in LLVM there's no instruction (or need) for destroying a local.
    // So, we just give what was in it. It's ironically identical to LocalLoad.
    auto localAddr = blockState->getLocalAddr(unstackify->local->id);
    blockState->markLocalUnstackified(unstackify->local->id);
    auto resultLE = LLVMBuildLoad(builder, localAddr, "");
    checkValidReference(FL(), globalState, functionState, builder, unstackify->local->type, resultLE);
    return resultLE;
  } else if (auto argument = dynamic_cast<Argument*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name(), " ", argument->argumentIndex, " type ", argument->resultType->str());
    auto resultLE = LLVMGetParam(functionState->containingFunc, argument->argumentIndex);
    checkValidReference(FL(), globalState, functionState, builder, argument->resultType, resultLE);
    return resultLE;
  } else if (auto constantStr = dynamic_cast<ConstantStr*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto resultLE = translateConstantStr(FL(), globalState, functionState, builder, constantStr);
    return resultLE;
  } else if (auto newStruct = dynamic_cast<NewStruct*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto memberExprs =
        translateExpressions(
            globalState, functionState, blockState, builder, newStruct->sourceExprs);
    buildFlare(FL(), globalState, functionState, builder);
    auto resultLE =
        translateConstruct(
            AFL("NewStruct"), globalState, functionState, builder, newStruct->resultType, memberExprs);
    buildFlare(FL(), globalState, functionState, builder);
    checkValidReference(FL(), globalState, functionState, builder, newStruct->resultType, resultLE);
    buildFlare(FL(), globalState, functionState, builder);
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
    auto structLE =
        translateExpression(
            globalState, functionState, blockState, builder, memberLoad->structExpr);
    checkValidReference(FL(), globalState, functionState, builder, memberLoad->structType, structLE);
    auto mutability = ownershipToMutability(memberLoad->structType->ownership);
    auto memberIndex = memberLoad->memberIndex;
    auto memberName = memberLoad->memberName;
    auto resultLE =
        loadMember(
            AFL("MemberLoad"),
            globalState,
            functionState,
            builder,
            memberLoad->structType,
            structLE,
            mutability,
            memberLoad->expectedResultType,
            memberIndex,
            memberName);
    checkValidReference(FL(), globalState, functionState, builder, memberLoad->expectedResultType, resultLE);
    discard(
        AFL("MemberLoad drop struct"),
        globalState, functionState, blockState, builder, memberLoad->structType, structLE);
    return resultLE;
  } else if (auto destroyKnownSizeArrayIntoFunction = dynamic_cast<DestroyKnownSizeArrayIntoFunction*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto consumerType = destroyKnownSizeArrayIntoFunction->consumerType;
    auto arrayReferend = destroyKnownSizeArrayIntoFunction->arrayReferend;
    auto arrayExpr = destroyKnownSizeArrayIntoFunction->arrayExpr;
    auto consumerExpr = destroyKnownSizeArrayIntoFunction->consumerExpr;
    auto arrayType = destroyKnownSizeArrayIntoFunction->arrayType;

    auto arrayWrapperLE = translateExpression(globalState, functionState, blockState, builder, arrayExpr);
    checkValidReference(FL(), globalState, functionState, builder, arrayType, arrayWrapperLE);
    auto arrayPtrLE = getKnownSizeArrayContentsPtr(builder, arrayWrapperLE);

    auto consumerLE = translateExpression(globalState, functionState, blockState, builder, consumerExpr);
    checkValidReference(FL(), globalState, functionState, builder, consumerType, consumerLE);

    foreachArrayElement(
        functionState, builder, LLVMConstInt(LLVMInt64Type(), arrayReferend->size, false), arrayPtrLE,
        [globalState, functionState, blockState, consumerType, arrayReferend, arrayPtrLE, consumerLE](LLVMValueRef indexLE, LLVMBuilderRef bodyBuilder) {
          acquireReference(
              AFL("DestroyKSAIntoF consume iteration"),
              globalState, functionState, bodyBuilder, consumerType, consumerLE);

          std::vector<LLVMValueRef> indices = { constI64LE(0), indexLE };
          auto elementPtrLE = LLVMBuildGEP(bodyBuilder, arrayPtrLE, indices.data(), indices.size(), "elementPtr");
          auto elementLE = LLVMBuildLoad(bodyBuilder, elementPtrLE, "element");
          checkValidReference(FL(), globalState, functionState, bodyBuilder, arrayReferend->rawArray->elementType, elementLE);
          std::vector<LLVMValueRef> argExprsLE = { consumerLE, elementLE };
          buildInterfaceCall(bodyBuilder, argExprsLE, 0, 0);
        });

    if (arrayType->ownership == Ownership::OWN) {
      adjustStrongRc(
          AFL("Destroy decrementing the owning ref"),
          globalState, functionState, builder, arrayWrapperLE, arrayType, -1);
    } else if (arrayType->ownership == Ownership::SHARE) {
      // We dont decrement anything here, we're only here because we already hit zero.
    } else {
      assert(false);
    }

    freeConcrete(AFL("DestroyKSAIntoF"), globalState, functionState, blockState, builder,
        arrayWrapperLE, arrayType);

    discard(AFL("DestroyKSAIntoF"), globalState, functionState, blockState, builder, consumerType, consumerLE);

    return makeConstExpr(builder, makeNever());
  } else if (auto destroyUnknownSizeArrayIntoFunction = dynamic_cast<DestroyUnknownSizeArray*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto consumerType = destroyUnknownSizeArrayIntoFunction->consumerType;
    auto arrayReferend = destroyUnknownSizeArrayIntoFunction->arrayReferend;
    auto arrayExpr = destroyUnknownSizeArrayIntoFunction->arrayExpr;
    auto consumerExpr = destroyUnknownSizeArrayIntoFunction->consumerExpr;
    auto arrayType = destroyUnknownSizeArrayIntoFunction->arrayType;

    auto arrayWrapperLE = translateExpression(globalState, functionState, blockState, builder, arrayExpr);
    checkValidReference(FL(), globalState, functionState, builder, arrayType, arrayWrapperLE);
    auto arrayPtrLE = getUnknownSizeArrayContentsPtr(builder, arrayWrapperLE);
    auto arrayLenLE = getUnknownSizeArrayLength(builder, arrayWrapperLE);

    auto consumerLE = translateExpression(globalState, functionState, blockState, builder, consumerExpr);
    checkValidReference(FL(), globalState, functionState, builder, consumerType, consumerLE);

    foreachArrayElement(
        functionState, builder, arrayLenLE, arrayPtrLE,
        [globalState, functionState, blockState, consumerType, arrayReferend, arrayPtrLE, consumerLE](LLVMValueRef indexLE, LLVMBuilderRef bodyBuilder) {
          acquireReference(
              AFL("DestroyUSAIntoF consume iteration"),
              globalState, functionState, bodyBuilder, consumerType, consumerLE);

          std::vector<LLVMValueRef> indices = { constI64LE(0), indexLE };
          auto elementPtrLE = LLVMBuildGEP(bodyBuilder, arrayPtrLE, indices.data(), indices.size(), "elementPtr");
          auto elementLE = LLVMBuildLoad(bodyBuilder, elementPtrLE, "element");
          checkValidReference(FL(), globalState, functionState, bodyBuilder, arrayReferend->rawArray->elementType, elementLE);
          std::vector<LLVMValueRef> argExprsLE = { consumerLE, elementLE };
          buildInterfaceCall(bodyBuilder, argExprsLE, 0, 0);
        });

    if (arrayType->ownership == Ownership::OWN) {
      adjustStrongRc(
          AFL("Destroy decrementing the owning ref"),
          globalState, functionState, builder, arrayWrapperLE, arrayType, -1);
    } else if (arrayType->ownership == Ownership::SHARE) {
      // We dont decrement anything here, we're only here because we already hit zero.
    } else {
      assert(false);
    }

    freeConcrete(AFL("DestroyUSAIntoF"), globalState, functionState, blockState, builder,
        arrayWrapperLE, arrayType);

    discard(AFL("DestroyUSAIntoF"), globalState, functionState, blockState, builder, consumerType, consumerLE);

    return makeConstExpr(builder, makeNever());
  } else if (auto knownSizeArrayLoad = dynamic_cast<KnownSizeArrayLoad*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto arrayType = knownSizeArrayLoad->arrayType;
    auto arrayExpr = knownSizeArrayLoad->arrayExpr;
    auto indexExpr = knownSizeArrayLoad->indexExpr;
    auto arrayReferend = knownSizeArrayLoad->arrayReferend;

    auto arrayWrapperPtrLE = translateExpression(globalState, functionState, blockState, builder, arrayExpr);
    checkValidReference(FL(), globalState, functionState, builder, arrayType, arrayWrapperPtrLE);
    auto sizeLE = constI64LE(dynamic_cast<KnownSizeArrayT*>(knownSizeArrayLoad->arrayType->referend)->size);
    auto indexLE = translateExpression(globalState, functionState, blockState, builder, indexExpr);
    auto mutability = ownershipToMutability(arrayType->ownership);
    discard(AFL("KSALoad"), globalState, functionState, blockState, builder, arrayType, arrayWrapperPtrLE);

    LLVMValueRef arrayPtrLE = getKnownSizeArrayContentsPtr(builder, arrayWrapperPtrLE);
    auto resultLE = loadElement(globalState, functionState, blockState, builder, arrayType, arrayReferend->rawArray->elementType, sizeLE, arrayPtrLE, mutability, indexLE);
    acquireReference(FL(), globalState, functionState, builder, arrayReferend->rawArray->elementType, resultLE);
    checkValidReference(FL(), globalState, functionState, builder, arrayReferend->rawArray->elementType, arrayPtrLE);
    return resultLE;
  } else if (auto unknownSizeArrayLoad = dynamic_cast<UnknownSizeArrayLoad*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto arrayType = unknownSizeArrayLoad->arrayType;
    auto arrayExpr = unknownSizeArrayLoad->arrayExpr;
    auto indexExpr = unknownSizeArrayLoad->indexExpr;
    auto arrayReferend = unknownSizeArrayLoad->arrayReferend;

    auto arrayWrapperPtrLE = translateExpression(globalState, functionState, blockState, builder, arrayExpr);
    checkValidReference(FL(), globalState, functionState, builder, arrayType, arrayWrapperPtrLE);
    auto sizeLE = getUnknownSizeArrayLength(builder, arrayWrapperPtrLE);
    auto indexLE = translateExpression(globalState, functionState, blockState, builder, indexExpr);
    auto mutability = ownershipToMutability(arrayType->ownership);

    LLVMValueRef arrayPtrLE = getUnknownSizeArrayContentsPtr(builder, arrayWrapperPtrLE);
    auto resultLE = loadElement(globalState, functionState, blockState, builder, arrayType, arrayReferend->rawArray->elementType, sizeLE, arrayPtrLE, mutability, indexLE);

    buildFlare(FL(), globalState, functionState, builder, "Loading from USA ", arrayPtrLE, " index ", indexLE);
    acquireReference(FL(), globalState, functionState, builder, unknownSizeArrayLoad->resultType, resultLE);
    buildFlare(FL(), globalState, functionState, builder, "Loaded from USA ", arrayPtrLE, " index ", indexLE);

    checkValidReference(FL(), globalState, functionState, builder, unknownSizeArrayLoad->resultType, resultLE);

    discard(AFL("USALoad"), globalState, functionState, blockState, builder, arrayType, arrayWrapperPtrLE);

    return resultLE;
  } else if (auto unknownSizeArrayStore = dynamic_cast<UnknownSizeArrayStore*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto arrayType = unknownSizeArrayStore->arrayType;
    auto arrayExpr = unknownSizeArrayStore->arrayExpr;
    auto indexExpr = unknownSizeArrayStore->indexExpr;
    auto arrayReferend = unknownSizeArrayStore->arrayReferend;

    buildFlare(FL(), globalState, functionState, builder, "Now doing array");

    auto arrayWrapperPtrLE = translateExpression(globalState, functionState, blockState, builder, arrayExpr);
    checkValidReference(FL(), globalState, functionState, builder, arrayType, arrayWrapperPtrLE);
    auto sizeLE = getUnknownSizeArrayLength(builder, arrayWrapperPtrLE);

    buildFlare(FL(), globalState, functionState, builder, "Now doing index");

    auto indexLE = translateExpression(globalState, functionState, blockState, builder, indexExpr);
    auto mutability = ownershipToMutability(arrayType->ownership);



    // The purpose of UnknownSizeArrayStore is to put a swap value into a spot, and give
    // what was in it.
    LLVMValueRef arrayPtrLE = getUnknownSizeArrayContentsPtr(builder, arrayWrapperPtrLE);

    buildFlare(FL(), globalState, functionState, builder, "Now loading oldElement");

    auto oldValueLE = loadElement(globalState, functionState, blockState, builder, arrayType, arrayReferend->rawArray->elementType, sizeLE, arrayPtrLE, mutability, indexLE);
    checkValidReference(FL(), globalState, functionState, builder, arrayReferend->rawArray->elementType, oldValueLE);
    // We dont acquireReference here because we aren't aliasing the reference, we're moving it out.

    buildFlare(FL(), globalState, functionState, builder, "Now doing valueToStore");

    auto valueToStoreLE =
        translateExpression(
            globalState, functionState, blockState, builder, unknownSizeArrayStore->sourceExpr);

    checkValidReference(FL(), globalState, functionState, builder, arrayReferend->rawArray->elementType, valueToStoreLE);

    buildFlare(FL(), globalState, functionState, builder, "Now storing");

    storeElement(globalState, functionState, blockState, builder, arrayType, arrayReferend->rawArray->elementType, sizeLE, arrayPtrLE, mutability, indexLE, valueToStoreLE);

    discard(AFL("USAStore"), globalState, functionState, blockState, builder, arrayType, arrayWrapperPtrLE);

    buildFlare(FL(), globalState, functionState, builder, "Returning oldValue");

    return oldValueLE;
  } else if (auto arrayLength = dynamic_cast<ArrayLength*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto arrayType = arrayLength->sourceType;
    auto arrayExpr = arrayLength->sourceExpr;
//    auto indexExpr = arrayLength->indexExpr;

    auto arrayWrapperPtrLE = translateExpression(globalState, functionState, blockState, builder, arrayExpr);
    checkValidReference(FL(), globalState, functionState, builder, arrayType, arrayWrapperPtrLE);
    auto sizeLE = getUnknownSizeArrayLength(builder, arrayWrapperPtrLE);
    discard(AFL("USALen"), globalState, functionState, blockState, builder, arrayType, arrayWrapperPtrLE);

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
    buildFlare(FL(), globalState, functionState, builder, "Doing extern call: ", externCall->function->name->name);
    auto resultLE = translateExternCall(globalState, functionState, blockState, builder, externCall);
    return resultLE;
  } else if (auto interfaceCall = dynamic_cast<InterfaceCall*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto resultLE = translateInterfaceCall(globalState, functionState, blockState, builder, interfaceCall);
    return resultLE;
  } else if (auto memberStore = dynamic_cast<MemberStore*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto structReferend =
        dynamic_cast<StructReferend*>(memberStore->structType->referend);
    auto structDefM = globalState->program->getStruct(structReferend->fullName);
    auto memberIndex = memberStore->memberIndex;
    auto memberName = memberStore->memberName;
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
            builder, structDefM, structExpr, memberIndex, memberName, sourceExpr);
    checkValidReference(FL(), globalState, functionState, builder, memberType, structExpr);
    discard(
        AFL("MemberStore discard struct"), globalState, functionState, blockState, builder,
        memberStore->structType, structExpr);
    return oldMemberLE;
  } else if (auto structToInterfaceUpcast = dynamic_cast<StructToInterfaceUpcast*>(expr)) {
    buildFlare(FL(), globalState, functionState, builder, typeid(*expr).name());
    auto sourceLE =
        translateExpression(
            globalState, functionState, blockState, builder, structToInterfaceUpcast->sourceExpr);
    checkValidReference(FL(), globalState, functionState, builder, structToInterfaceUpcast->sourceStructType, sourceLE);

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

    return upcast2(
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
    auto sourceLE =
        translateExpression(
            globalState, functionState, blockState, builder, lockWeak->sourceExpr);
    checkValidReference(FL(), globalState, functionState, builder, lockWeak->resultOptType, sourceLE);

    auto isAliveLE = getIsAliveFromWeakRef(globalState, builder, sourceLE);

    auto resultOptTypeLE = translateType(globalState, lockWeak->resultOptType);

    auto resultOptLE =
        buildIfElse(functionState, builder, isAliveLE, resultOptTypeLE, false, false,
            [globalState, functionState, lockWeak, sourceLE](LLVMBuilderRef thenBuilder) {
              auto sourceTypeM = lockWeak->sourceType;
              auto someConstructor = lockWeak->someConstructor;
              auto objPtrLE =
                  getObjPtrFromWeakRef(
                      globalState,
                      functionState,
                      thenBuilder,
                      sourceTypeM,
                      sourceLE,
                      someConstructor->params[0]);
              // If we get here, object is alive, return a Some.
              auto someLE =
                  buildCall(globalState, functionState, thenBuilder, someConstructor, {objPtrLE});
              return upcast2(
                  globalState,
                  functionState,
                  thenBuilder,
                  lockWeak->someType,
                  lockWeak->someReferend,
                  someLE,
                  lockWeak->resultOptType,
                  lockWeak->resultOptReferend);
            },
            [globalState, functionState, lockWeak](LLVMBuilderRef elseBuilder) {
              auto noneConstructor = lockWeak->noneConstructor;
              // If we get here, object is dead, return a None.
              auto noneLE = buildCall(globalState, functionState, elseBuilder, noneConstructor, {});
              return upcast2(
                  globalState,
                  functionState,
                  elseBuilder,
                  lockWeak->noneType,
                  lockWeak->noneReferend,
                  noneLE,
                  lockWeak->resultOptType,
                  lockWeak->resultOptReferend);
            });

    discard(
        AFL("LockWeak drop weak ref"),
        globalState, functionState, blockState, builder, lockWeak->sourceType, sourceLE);

    return resultOptLE;
  } else {
    std::string name = typeid(*expr).name();
    std::cout << name << std::endl;
    assert(false);
  }
  assert(false);
}
