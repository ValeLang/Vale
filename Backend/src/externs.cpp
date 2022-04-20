
#include "function/expressions/expressions.h"
#include "externs.h"
#include "globalstate.h"

Externs::Externs(LLVMModuleRef mod, LLVMContextRef context) {
  auto voidLT = LLVMVoidTypeInContext(context);
  auto int1LT = LLVMInt1TypeInContext(context);
  auto int8LT = LLVMInt8TypeInContext(context);
  auto int32LT = LLVMInt32TypeInContext(context);
  auto int32PtrLT = LLVMPointerType(int32LT, 0);
  auto int64LT = LLVMInt64TypeInContext(context);
  auto voidPtrLT = LLVMPointerType(int8LT, 0);
  auto int8PtrLT = LLVMPointerType(int8LT, 0);

  censusContains = addExtern(mod, "__vcensusContains", int64LT, {voidPtrLT});
  censusAdd = addExtern(mod, "__vcensusAdd", voidLT, {voidPtrLT});
  censusRemove = addExtern(mod, "__vcensusRemove", voidLT, {voidPtrLT});
  malloc = addExtern(mod, "malloc", int8PtrLT, {int64LT});
  free = addExtern(mod, "free", voidLT, {int8PtrLT});
  exit = addExtern(mod, "exit", voidLT, {int64LT});
  assert = addExtern(mod, "__vassert", voidLT, {int1LT, int8PtrLT});
  assertI64Eq = addExtern(mod, "__vassertI64Eq", voidLT, {int64LT, int64LT, int8PtrLT});
  printCStr = addExtern(mod, "__vprintCStr", voidLT, {int8PtrLT});
  getch = addExtern(mod, "getchar", int64LT, {});
  printInt = addExtern(mod, "__vprintI64", voidLT, {int64LT});
  strlen = addExtern(mod, "strlen", int32LT, {int8PtrLT});
  strncpy = addExtern(mod, "strncpy", voidLT, {int8PtrLT, int8PtrLT, int64LT});
  strncmp = addExtern(mod, "strncmp", int64LT, {int8PtrLT, int8PtrLT, int64LT});
  memcpy = addExtern(mod, "memcpy", int8PtrLT, {int8PtrLT, int8PtrLT, int64LT});
  memset = addExtern(mod, "memset", voidLT, {int8PtrLT, int8LT, int64LT});

//  initTwinPages = addExtern(mod, "__vale_initTwinPages", int8PtrLT, {});
}

bool hasEnding (std::string const &fullString, std::string const &ending) {
  if (fullString.length() >= ending.length()) {
    return (0 == fullString.compare (fullString.length() - ending.length(), ending.length(), ending));
  } else {
    return false;
  }
}

bool includeSizeParam(GlobalState* globalState, Prototype* prototype, int paramIndex) {
  // See SASP for what this is all about.
  if (hasEnding(prototype->name->name, "_vasp")) {
    auto paramMT = prototype->params[paramIndex];
    if (dynamic_cast<StructKind*>(paramMT->kind) ||
        dynamic_cast<InterfaceKind*>(paramMT->kind) ||
        dynamic_cast<StaticSizedArrayT*>(paramMT->kind) ||
        dynamic_cast<RuntimeSizedArrayT*>(paramMT->kind)) {
      return true;
    }
  }
  return false;
}
