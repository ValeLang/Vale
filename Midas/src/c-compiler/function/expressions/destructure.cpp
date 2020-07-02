#include <iostream>

#include "translatetype.h"

#include "function/expression.h"
#include "function/expressions/shared/shared.h"

LLVMValueRef translateDestructure(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Destructure* destructureM) {
  auto mutability = ownershipToMutability(destructureM->structType->ownership);
  if (destructureM->structType->ownership == Ownership::OWN) {
    auto structLE =
        translateExpression(
            globalState, functionState, builder, destructureM->structExpr);

    auto structReferend =
        dynamic_cast<StructReferend *>(destructureM->structType->referend);
    assert(structReferend);

    auto structM = globalState->program->getStruct(structReferend->fullName);

    for (int i = 0; i < structM->members.size(); i++) {
      auto memberName = structM->members[i]->name;
      auto memberLE =
          loadMember(
              builder, structLE, mutability, i, memberName + "_local");
      makeLocal(
          globalState,
          functionState,
          builder,
          destructureM->localIndices[i],
          memberLE);
    }

    if (destructureM->structType->ownership == Ownership::OWN) {
      flareRc(globalState, builder, 13370009, structLE);

      LLVMValueRef rcIsZero =
          rcEquals(builder, structLE, LLVMConstInt(LLVMInt64Type(), 1, false));
      LLVMBuildCall(
          builder, globalState->assert, &rcIsZero, 1, "");

      auto structAsCharPtrLE =
          LLVMBuildBitCast(
              builder,
              structLE,
              LLVMPointerType(LLVMInt8Type(), 0),
              "charptrstruct");
      LLVMBuildCall(
          builder, globalState->free, &structAsCharPtrLE, 1, "");
    }

    return makeNever();
  } else {
    // TODO implement
    assert(false);
    return nullptr;
  }
}
