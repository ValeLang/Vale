#include "ast.h"

UnconvertedWeakability Program::getReferendWeakability(Referend* referend) {
  if (auto structReferend = dynamic_cast<StructReferend*>(referend)) {
    return getStruct(structReferend->fullName)->weakability;
  } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend*>(referend)) {
    return getInterface(interfaceReferend->fullName)->weakability;
  } else {
    return UnconvertedWeakability::NON_WEAKABLE;
  }
}