#ifndef REGION_COMMON_PRIMITIVES_H_
#define REGION_COMMON_PRIMITIVES_H_

class DefaultPrimitives {
public:
  // We need to pick an arbitrary type to map "Never" to. It shouldn't matter,
  // because the type system uses Never to signal that the program will literally
  // never get there.
  // We arbitrarily use a zero-len array of i57, because it's zero sized and very
  // unlikely to be used anywhere else.
  // See rsages of this int to see where we make those zero-len arrays of these.
  static constexpr int NEVER_INT_BITS = 57;


  bool isPrimitive(Reference* referenceM) {
    return dynamic_cast<Int *>(referenceM->kind) != nullptr ||
        dynamic_cast<Bool *>(referenceM->kind) != nullptr ||
        dynamic_cast<Float *>(referenceM->kind) != nullptr;
  }

  LLVMTypeRef translatePrimitive(GlobalState* globalState, Reference* referenceM) {
    if (dynamic_cast<Int*>(referenceM->kind) != nullptr) {
      assert(referenceM->ownership == Ownership::SHARE);
      return LLVMInt64TypeInContext(globalState->context);
    } else if (dynamic_cast<Bool*>(referenceM->kind) != nullptr) {
      assert(referenceM->ownership == Ownership::SHARE);
      return LLVMInt1TypeInContext(globalState->context);
    } else if (dynamic_cast<Float*>(referenceM->kind) != nullptr) {
      assert(referenceM->ownership == Ownership::SHARE);
      return LLVMDoubleTypeInContext(globalState->context);
    } else if (dynamic_cast<Never*>(referenceM->kind) != nullptr) {
      return LLVMArrayType(LLVMIntTypeInContext(globalState->context, NEVER_INT_BITS), 0);
    } else {
      assert(false);
    }
  }
};

#endif
