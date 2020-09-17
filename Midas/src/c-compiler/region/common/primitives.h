#ifndef REGION_COMMON_PRIMITIVES_H_
#define REGION_COMMON_PRIMITIVES_H_

class DefaultPrimitives {
public:
  // We need to pick an arbitrary type to map "Never" to. It shouldn't matter,
  // because the type system uses Never to signal that the program will literally
  // never get there.
  // We arbitrarily use a zero-len array of i57, because it's zero sized and very
  // unlikely to be used anywhere else.
  // See usages of this int to see where we make those zero-len arrays of these.
  static constexpr int NEVER_INT_BITS = 57;


  bool isPrimitive(Reference* referenceM) {
    return dynamic_cast<Int *>(referenceM->referend) != nullptr ||
        dynamic_cast<Bool *>(referenceM->referend) != nullptr ||
        dynamic_cast<Float *>(referenceM->referend) != nullptr;
  }

  LLVMTypeRef translatePrimitive(Reference* referenceM) {
    if (dynamic_cast<Int*>(referenceM->referend) != nullptr) {
      assert(referenceM->ownership == Ownership::SHARE);
      return LLVMInt64Type();
    } else if (dynamic_cast<Bool*>(referenceM->referend) != nullptr) {
      assert(referenceM->ownership == Ownership::SHARE);
      return LLVMInt1Type();
    } else if (dynamic_cast<Float*>(referenceM->referend) != nullptr) {
      assert(referenceM->ownership == Ownership::SHARE);
      return LLVMDoubleType();
    } else if (dynamic_cast<Never*>(referenceM->referend) != nullptr) {
      return LLVMArrayType(LLVMIntType(NEVER_INT_BITS), 0);
    } else {
      assert(false);
    }
  }
};

#endif
