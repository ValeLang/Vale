#include <iostream>

#include "translatetype.h"

#include "shared.h"
#include "members.h"
#include "branch.h"
#include "heap.h"


void dropReference(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* sourceRef,
    LLVMValueRef expr) {

  auto sourceRnd = sourceRef->referend;

  if (dynamic_cast<Int*>(sourceRnd) ||
      dynamic_cast<Bool*>(sourceRnd) ||
      dynamic_cast<Float*>(sourceRnd)) {
    // Do nothing for these, they're always inlined and copied.
  } else if (auto structRnd = dynamic_cast<StructReferend*>(sourceRnd)) {
    auto structM = globalState->program->getStruct(structRnd->fullName);

    if (sourceRef->ownership == Ownership::OWN) {
      // We can't discard owns, they must be destructured.
      assert(false);
    } else if (sourceRef->ownership == Ownership::BORROW) {
      adjustRC(builder, expr, -1);
    } else if (sourceRef->ownership == Ownership::SHARE) {
      if (isInlImm(globalState, sourceRef)) {
        // Do nothing, we can just let inline structs disappear
      } else {
        auto rcIsOne =
            rcEquals(
                builder, expr, LLVMConstInt(LLVMInt64Type(), 1, false));
        buildIf(
            functionState,
            builder,
            rcIsOne,
            [globalState, expr](LLVMBuilderRef thenBuilder) {
              freeStruct(globalState, thenBuilder, expr);
            });
        adjustRC(builder, expr, -1);
      }
    }
  } else {
    std::cerr << "Unimplemented type in dropReference: "
        << typeid(*sourceRef->referend).name() << std::endl;
    assert(false);
  }
}
