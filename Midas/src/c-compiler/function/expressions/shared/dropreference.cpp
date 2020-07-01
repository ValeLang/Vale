#include <iostream>

#include "translatetype.h"

#include "shared.h"


void dropReference(
    GlobalState* globalState,
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
    } else {
      bool inliine = true;//sourceRef->location == INLINE; TODO
      if (inliine) {
        // Do nothing, we can just let inline structs disappear
      } else {
        assert(false); // TODO implement
      }
    }
  } else {
    std::cerr << "Unimplemented type in dropReference: "
        << typeid(*sourceRef->referend).name() << std::endl;
    assert(false);
  }
}
