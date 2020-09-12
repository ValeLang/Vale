
#include <function/expressions/shared/shared.h>
#include "globalstate.h"

IReferendStructsSource* GlobalState::getReferendStructsSource() {
  return region->getReferendStructsSource();
}
IWeakRefStructsSource* GlobalState::getWeakRefStructsSource() {
  return region->getWeakRefStructsSource();
}
ControlBlock* GlobalState::getControlBlock(Referend* referend) {
  return region->getControlBlock(referend);
}

LLVMTypeRef GlobalState::getControlBlockStruct(Referend* referend) {
  return getControlBlock(referend)->getStruct();
}
