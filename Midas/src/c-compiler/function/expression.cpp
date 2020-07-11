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
  buildFlare(FL(), globalState, builder, typeid(*expr).name());
  if (auto constantI64 = dynamic_cast<ConstantI64*>(expr)) {
    // See ULTMCIE for why we load and store here.
    return makeConstIntExpr(builder, LLVMInt64Type(), constantI64->value);
  } else if (auto constantBool = dynamic_cast<ConstantBool*>(expr)) {
    // See ULTMCIE for why this is an add.
    return makeConstIntExpr(builder, LLVMInt1Type(), constantBool->value);
  } else if (auto discardM = dynamic_cast<Discard*>(expr)) {
    return translateDiscard(globalState, functionState, blockState, builder, discardM);
  } else if (auto ret = dynamic_cast<Return*>(expr)) {
    auto sourceLE = translateExpression(globalState, functionState, blockState, builder, ret->sourceExpr);
    checkValidReference(FL(), globalState, functionState, builder, ret->sourceType, sourceLE);
    if (ret->sourceType->referend == globalState->metalCache.never) {
      return sourceLE;
    } else {
      return LLVMBuildRet(builder, sourceLE);
    }
  } else if (auto stackify = dynamic_cast<Stackify*>(expr)) {
    auto valueToStoreLE =
        translateExpression(
            globalState, functionState, blockState, builder, stackify->sourceExpr);
    checkValidReference(FL(), globalState, functionState, builder, stackify->local->type, valueToStoreLE);
    makeLocal(
        globalState, functionState, blockState, builder, stackify->local, valueToStoreLE);
    return makeConstExpr(builder, makeNever());
  } else if (auto localStore = dynamic_cast<LocalStore*>(expr)) {
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
  } else if (auto localLoad = dynamic_cast<LocalLoad*>(expr)) {
    if (localLoad->local->type->ownership == Ownership::SHARE) {
      if (localLoad->local->type->location == Location::INLINE) {
        auto localAddr = blockState->getLocalAddr(localLoad->local->id);
        auto resultLE = LLVMBuildLoad(builder, localAddr, localLoad->localName.c_str());
        checkValidReference(FL(), globalState, functionState, builder, localLoad->local->type, resultLE);
        return resultLE;
      } else {
        auto localAddr = blockState->getLocalAddr(localLoad->local->id);
        auto resultLE =
            LLVMBuildLoad(builder, localAddr, localLoad->localName.c_str());
        checkValidReference(FL(), globalState, functionState, builder, localLoad->local->type, resultLE);
        adjustRc(AFL("LocalLoad"), globalState, builder, resultLE,
            localLoad->local->type, 1);
        return resultLE;
      }
    } else if (localLoad->local->type->ownership == Ownership::OWN) {
      assert(localLoad->targetOwnership != Ownership::OWN);

      // We do the same thing for inline and yonder muts, the only difference is
      // where the memory lives.

      auto localAddr = blockState->getLocalAddr(localLoad->local->id);
      auto resultLE = LLVMBuildLoad(builder, localAddr, localLoad->localName.c_str());
      checkValidReference(FL(), globalState, functionState, builder, localLoad->local->type, resultLE);
      adjustRc(AFL("LocalLoad"), globalState, builder, resultLE, localLoad->local->type, 1);
      return resultLE;
    } else if (localLoad->local->type->ownership == Ownership::BORROW) {
      assert(localLoad->local->type->location != Location::INLINE);

      auto localAddr = blockState->getLocalAddr(localLoad->local->id);
      auto resultLE =
          LLVMBuildLoad(builder, localAddr, localLoad->localName.c_str());
      checkValidReference(FL(), globalState, functionState, builder, localLoad->local->type, resultLE);
      adjustRc(AFL("LocalLoad"), globalState, builder, resultLE,
          localLoad->local->type, 1);
      return resultLE;
    } else {
      assert(false);
    }
  } else if (auto unstackify = dynamic_cast<Unstackify*>(expr)) {
    // The purpose of Unstackify is to destroy the local and give what was in
    // it, but in LLVM there's no instruction (or need) for destroying a local.
    // So, we just give what was in it. It's ironically identical to LocalLoad.
    auto localAddr = blockState->getLocalAddr(unstackify->local->id);
    blockState->markLocalUnstackified(unstackify->local->id);
    auto resultLE = LLVMBuildLoad(builder, localAddr, "");
    checkValidReference(FL(), globalState, functionState, builder, unstackify->local->type, resultLE);
    return resultLE;
  } else if (auto argument = dynamic_cast<Argument*>(expr)) {
    auto resultLE = LLVMGetParam(functionState->containingFunc, argument->argumentIndex);
    checkValidReference(FL(), globalState, functionState, builder, argument->resultType, resultLE);
    return resultLE;
  } else if (auto constantStr = dynamic_cast<ConstantStr*>(expr)) {
    auto resultLE = translateConstantStr(FL(), globalState, builder, constantStr);
    return resultLE;
  } else if (auto newStruct = dynamic_cast<NewStruct*>(expr)) {
    auto memberExprs =
        translateExpressions(
            globalState, functionState, blockState, builder, newStruct->sourceExprs);
    auto resultLE =
        translateConstruct(
            AFL("NewStruct"), globalState, builder, newStruct->resultType, memberExprs);
    checkValidReference(FL(), globalState, functionState, builder, newStruct->resultType, resultLE);
    return resultLE;
  } else if (auto consecutor = dynamic_cast<Consecutor*>(expr)) {
    auto exprs =
        translateExpressions(globalState, functionState, blockState, builder, consecutor->exprs);
    assert(!exprs.empty());
    return exprs.back();
  } else if (auto block = dynamic_cast<Block*>(expr)) {
    return translateBlock(globalState, functionState, blockState, builder, block);
  } else if (auto iff = dynamic_cast<If*>(expr)) {
    return translateIf(globalState, functionState, blockState, builder, iff);
  } else if (auto whiile = dynamic_cast<While*>(expr)) {
    return translateWhile(globalState, functionState, blockState, builder, whiile);
  } else if (auto destructureM = dynamic_cast<Destroy*>(expr)) {
    return translateDestructure(globalState, functionState, blockState, builder, destructureM);
  } else if (auto memberLoad = dynamic_cast<MemberLoad*>(expr)) {
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
          acquireReference(AFL("DestroyKSAIntoF consume iteration"), globalState, bodyBuilder, consumerType, consumerLE);

          std::vector<LLVMValueRef> indices = { constI64LE(0), indexLE };
          auto elementPtrLE = LLVMBuildGEP(bodyBuilder, arrayPtrLE, indices.data(), indices.size(), "elementPtr");
          auto elementLE = LLVMBuildLoad(bodyBuilder, elementPtrLE, "element");
          checkValidReference(FL(), globalState, functionState, bodyBuilder, arrayReferend->rawArray->elementType, elementLE);
          std::vector<LLVMValueRef> argExprsLE = { consumerLE, elementLE };
          buildInterfaceCall(bodyBuilder, argExprsLE, 0, 0);
        });

    if (arrayType->ownership == Ownership::OWN) {
      adjustRc(AFL("Destroy decrementing the owning ref"), globalState, builder, arrayWrapperLE, arrayType, -1);
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
          acquireReference(AFL("DestroyUSAIntoF consume iteration"), globalState, bodyBuilder, consumerType, consumerLE);

          std::vector<LLVMValueRef> indices = { constI64LE(0), indexLE };
          auto elementPtrLE = LLVMBuildGEP(bodyBuilder, arrayPtrLE, indices.data(), indices.size(), "elementPtr");
          auto elementLE = LLVMBuildLoad(bodyBuilder, elementPtrLE, "element");
          checkValidReference(FL(), globalState, functionState, bodyBuilder, arrayReferend->rawArray->elementType, elementLE);
          std::vector<LLVMValueRef> argExprsLE = { consumerLE, elementLE };
          buildInterfaceCall(bodyBuilder, argExprsLE, 0, 0);
        });

    if (arrayType->ownership == Ownership::OWN) {
      adjustRc(AFL("Destroy decrementing the owning ref"), globalState, builder, arrayWrapperLE, arrayType, -1);
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
    acquireReference(FL(), globalState, builder, arrayReferend->rawArray->elementType, resultLE);
    checkValidReference(FL(), globalState, functionState, builder, arrayReferend->rawArray->elementType, arrayPtrLE);
    return resultLE;
  } else if (auto unknownSizeArrayLoad = dynamic_cast<UnknownSizeArrayLoad*>(expr)) {
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

    buildFlare(FL(), globalState, builder, "Loading from USA ", arrayPtrLE, " index ", indexLE);
    acquireReference(FL(), globalState, builder, unknownSizeArrayLoad->resultType, resultLE);
    buildFlare(FL(), globalState, builder, "Loaded from USA ", arrayPtrLE, " index ", indexLE);

    checkValidReference(FL(), globalState, functionState, builder, unknownSizeArrayLoad->resultType, resultLE);

    discard(AFL("USALoad"), globalState, functionState, blockState, builder, arrayType, arrayWrapperPtrLE);

    return resultLE;
  } else if (auto unknownSizeArrayStore = dynamic_cast<UnknownSizeArrayStore*>(expr)) {
    auto arrayType = unknownSizeArrayStore->arrayType;
    auto arrayExpr = unknownSizeArrayStore->arrayExpr;
    auto indexExpr = unknownSizeArrayStore->indexExpr;
    auto arrayReferend = unknownSizeArrayStore->arrayReferend;

    buildFlare(FL(), globalState, builder, "Now doing array");

    auto arrayWrapperPtrLE = translateExpression(globalState, functionState, blockState, builder, arrayExpr);
    checkValidReference(FL(), globalState, functionState, builder, arrayType, arrayWrapperPtrLE);
    auto sizeLE = getUnknownSizeArrayLength(builder, arrayWrapperPtrLE);

    buildFlare(FL(), globalState, builder, "Now doing index");

    auto indexLE = translateExpression(globalState, functionState, blockState, builder, indexExpr);
    auto mutability = ownershipToMutability(arrayType->ownership);



    // The purpose of UnknownSizeArrayStore is to put a swap value into a spot, and give
    // what was in it.
    LLVMValueRef arrayPtrLE = getUnknownSizeArrayContentsPtr(builder, arrayWrapperPtrLE);

    buildFlare(FL(), globalState, builder, "Now loading oldElement");

    auto oldValueLE = loadElement(globalState, functionState, blockState, builder, arrayType, arrayReferend->rawArray->elementType, sizeLE, arrayPtrLE, mutability, indexLE);
    checkValidReference(FL(), globalState, functionState, builder, arrayReferend->rawArray->elementType, oldValueLE);
    // We dont acquireReference here because we aren't aliasing the reference, we're moving it out.

    buildFlare(FL(), globalState, builder, "Now doing valueToStore");

    auto valueToStoreLE =
        translateExpression(
            globalState, functionState, blockState, builder, unknownSizeArrayStore->sourceExpr);

    checkValidReference(FL(), globalState, functionState, builder, arrayReferend->rawArray->elementType, valueToStoreLE);

    buildFlare(FL(), globalState, builder, "Now storing");

    storeElement(globalState, functionState, blockState, builder, arrayType, arrayReferend->rawArray->elementType, sizeLE, arrayPtrLE, mutability, indexLE, valueToStoreLE);

    discard(AFL("USAStore"), globalState, functionState, blockState, builder, arrayType, arrayWrapperPtrLE);

    buildFlare(FL(), globalState, builder, "Returning oldValue");

    return oldValueLE;
  } else if (auto arrayLength = dynamic_cast<ArrayLength*>(expr)) {
    auto arrayType = arrayLength->sourceType;
    auto arrayExpr = arrayLength->sourceExpr;
//    auto indexExpr = arrayLength->indexExpr;

    auto arrayWrapperPtrLE = translateExpression(globalState, functionState, blockState, builder, arrayExpr);
    checkValidReference(FL(), globalState, functionState, builder, arrayType, arrayWrapperPtrLE);
    auto sizeLE = getUnknownSizeArrayLength(builder, arrayWrapperPtrLE);
    discard(AFL("USALen"), globalState, functionState, blockState, builder, arrayType, arrayWrapperPtrLE);

    return sizeLE;
  } else if (auto newArrayFromValues = dynamic_cast<NewArrayFromValues*>(expr)) {
    return translateNewArrayFromValues(globalState, functionState, blockState, builder, newArrayFromValues);
  } else if (auto constructUnknownSizeArray = dynamic_cast<ConstructUnknownSizeArray*>(expr)) {
    return translateConstructUnknownSizeArray(globalState, functionState, blockState, builder, constructUnknownSizeArray);
  } else if (auto call = dynamic_cast<Call*>(expr)) {
    auto resultLE = translateCall(globalState, functionState, blockState, builder, call);
    return resultLE;
  } else if (auto externCall = dynamic_cast<ExternCall*>(expr)) {
    buildFlare(FL(), globalState, builder, "Doing extern call: ", externCall->function->name->name);
    auto resultLE = translateExternCall(globalState, functionState, blockState, builder, externCall);
    return resultLE;
  } else if (auto interfaceCall = dynamic_cast<InterfaceCall*>(expr)) {
    auto resultLE = translateInterfaceCall(globalState, functionState, blockState, builder, interfaceCall);
    return resultLE;
  } else if (auto memberStore = dynamic_cast<MemberStore*>(expr)) {
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

    assert(structToInterfaceUpcast->sourceStructType->location != Location::INLINE);

    auto interfaceRefLT =
        globalState->getInterfaceRefStruct(
            structToInterfaceUpcast->targetInterfaceRef->fullName);

    auto interfaceRefLE = LLVMGetUndef(interfaceRefLT);
    interfaceRefLE =
        LLVMBuildInsertValue(
            builder,
            interfaceRefLE,
            getControlBlockPtr(builder, sourceLE, structToInterfaceUpcast->sourceStructType),
            0,
            "interfaceRefWithOnlyObj");
    interfaceRefLE =
        LLVMBuildInsertValue(
            builder,
            interfaceRefLE,
            globalState->getInterfaceTablePtr(
                globalState->program->getStruct(
                    structToInterfaceUpcast->sourceStructId->fullName)
                    ->getEdgeForInterface(structToInterfaceUpcast->targetInterfaceRef->fullName)),
            1,
            "interfaceRef");
    return interfaceRefLE;
  } else {
    std::string name = typeid(*expr).name();
    std::cout << name << std::endl;
    assert(false);
  }
  assert(false);
}
