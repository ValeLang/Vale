
#include <function/expressions/shared/shared.h>
#include "globalstate.h"

ControlBlockLayout* GlobalState::getControlBlockLayout(Referend* referend) {
  if (auto structReferend = dynamic_cast<StructReferend*>(referend)) {
    auto structM = program->getStruct(structReferend->fullName);
    if (structM->mutability == Mutability::IMMUTABLE) {
      return &immControlBlockLayout;
    } else {
      if (getEffectiveWeakability(this, structM) == Weakability::WEAKABLE) {
        return &mutWeakableControlBlockLayout;
      } else {
        return &mutNonWeakableControlBlockLayout;
      }
    }
  } else if (auto interfaceReferend = dynamic_cast<InterfaceReferend*>(referend)) {
    auto interfaceM = program->getInterface(interfaceReferend->fullName);
    if (interfaceM->mutability == Mutability::IMMUTABLE) {
      return &immControlBlockLayout;
    } else {
      if (getEffectiveWeakability(this, interfaceM) == Weakability::WEAKABLE) {
        return &mutWeakableControlBlockLayout;
      } else {
        return &mutNonWeakableControlBlockLayout;
      }
    }
  } else if (auto ksaMT = dynamic_cast<KnownSizeArrayT*>(referend)) {
    if (ksaMT->rawArray->mutability == Mutability::IMMUTABLE) {
      return &immControlBlockLayout;
    } else {
      if (getEffectiveWeakability(this, ksaMT->rawArray) == Weakability::WEAKABLE) {
        return &mutWeakableControlBlockLayout;
      } else {
        return &mutNonWeakableControlBlockLayout;
      }
    }
  } else if (auto usaMT = dynamic_cast<UnknownSizeArrayT*>(referend)) {
    if (usaMT->rawArray->mutability == Mutability::IMMUTABLE) {
      return &immControlBlockLayout;
    } else {
      if (getEffectiveWeakability(this, usaMT->rawArray) == Weakability::WEAKABLE) {
        return &mutWeakableControlBlockLayout;
      } else {
        return &mutNonWeakableControlBlockLayout;
      }
    }
  } else if (auto strMT = dynamic_cast<Str*>(referend)) {
    return &immControlBlockLayout;
  } else {
    assert(false);
  }
}
