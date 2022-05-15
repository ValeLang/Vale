
#include "function/expressions/expressions.h"
#include "externs.h"
#include "globalstate.h"

Externs::Externs(LLVMModuleRef mod, LLVMContextRef context) {
  auto emptyLT = LLVMStructTypeInContext(context, nullptr, 0, false);
  auto voidLT = LLVMVoidTypeInContext(context);
  auto int1LT = LLVMInt1TypeInContext(context);
  auto int8LT = LLVMInt8TypeInContext(context);
  auto int32LT = LLVMInt32TypeInContext(context);
  auto int32PtrLT = LLVMPointerType(int32LT, 0);
  auto int64LT = LLVMInt64TypeInContext(context);
  auto voidPtrLT = LLVMPointerType(int8LT, 0);
  auto int8PtrLT = LLVMPointerType(int8LT, 0);
  auto metadataLT = LLVMMetadataTypeInContext(context);

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


  fopen = addExtern(mod, "fopen", int8PtrLT, {int8PtrLT, int8PtrLT});
  fclose = addExtern(mod, "fclose", int64LT, {int8PtrLT});
  fread = addExtern(mod, "fread", int64LT, {int8PtrLT, int64LT, int64LT, int8PtrLT});
  fwrite = addExtern(mod, "fwrite", int64LT, {int8PtrLT, int64LT, int64LT, int8PtrLT});

  strHasherCallLF = addExtern(mod, "strHasherCall", int64LT, {emptyLT, int8PtrLT});
  strEquatorCallLF = addExtern(mod, "strEquatorCall", int64LT, {emptyLT, int8PtrLT, int8PtrLT});

  // https://llvm.org/docs/LangRef.html#llvm-read-register-llvm-read-volatile-register-and-llvm-write-register-intrinsics
  // Warning from docs:
  //   WARNING: So far it only works with the stack pointer on selected architectures
  //   (ARM, AArch64, PowerPC and x86_64). Significant amount of work is needed to support other
  //   registers and even more so, allocatable registers.
  // So, only use it for stack pointer, on those architectures.
  readRegisterI64Intrinsic = addExtern(mod, "llvm.read_register.i64", int64LT, {metadataLT});
  assert(LLVMGetIntrinsicID(readRegisterI64Intrinsic));
  writeRegisterI64Intrinsinc = addExtern(mod, "llvm.write_register.i64", voidLT, {metadataLT, int64LT});
  assert(LLVMGetIntrinsicID(writeRegisterI64Intrinsinc));

  setjmpIntrinsic = addExtern(mod, "llvm.eh.sjlj.setjmp", int32LT, {int8PtrLT});
  assert(LLVMGetIntrinsicID(setjmpIntrinsic));
  longjmpIntrinsic = addExtern(mod, "llvm.eh.sjlj.longjmp", voidLT, {int8PtrLT});
  assert(LLVMGetIntrinsicID(longjmpIntrinsic));

  stacksaveIntrinsic = addExtern(mod, "llvm.stacksave", int8PtrLT, {});
  assert(LLVMGetIntrinsicID(setjmpIntrinsic));
  stackrestoreIntrinsic = addExtern(mod, "llvm.stackrestore", voidLT, {int8PtrLT});
  assert(LLVMGetIntrinsicID(longjmpIntrinsic));

//  initTwinPages = addExtern(mod, "__vale_initTwinPages", int8PtrLT, {});
}

bool hasEnding(std::string const &fullString, std::string const &ending) {
  if (fullString.length() >= ending.length()) {
    return (0 == fullString.compare(fullString.length() - ending.length(), ending.length(), ending));
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

