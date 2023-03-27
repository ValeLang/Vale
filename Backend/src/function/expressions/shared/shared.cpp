#include "../../../region/common/common.h"
#include "../../../region/common/fatweaks/fatweaks.h"
#include "../../../utils/counters.h"
#include "shared.h"

#include "../../../translatetype.h"
#include "../../../region/common/controlblock.h"
#include "../../../region/linear/linear.h"
#include "../../../region/rcimm/rcimm.h"
#include "../../../utils/branch.h"
#include <region/common/migration.h>

// A "Never" is something that should never be read.
// This is useful in a lot of situations, for example:
// - The return type of Panic()
// - The result of the Discard node
LLVMTypeRef makeNeverType(GlobalState* globalState) {
  // We arbitrarily use a zero-len array of i57 here because it's zero sized and
  // very unlikely to be used anywhere else.
  // We could use an empty struct instead, but this'll do.
  return LLVMArrayType(LLVMIntTypeInContext(globalState->context, NEVER_INT_BITS), 0);
}

LLVMValueRef makeVoid(GlobalState* globalState) {
  return LLVMGetUndef(globalState->rcImm->translateType(globalState->metalCache->voidRef));
}

LLVMTypeRef makeEmptyStructType(GlobalState* globalState) {
  return LLVMStructTypeInContext(globalState->context, nullptr, 0, false);
}
LLVMValueRef makeEmptyStruct(GlobalState* globalState) {
  return LLVMConstNamedStruct(makeEmptyStructType(globalState), nullptr, 0);
}

Ref makeVoidRef(GlobalState* globalState) {
  auto voidLE = makeVoid(globalState);
  auto refMT = globalState->metalCache->voidRef;
  return wrap(globalState->rcImm, refMT, voidLE);
}

LLVMValueRef makeBackendLocal(
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMTypeRef typeL,
    const std::string& name,
    LLVMValueRef valueToStore) {
  auto localAddr = LLVMBuildAlloca(functionState->localsBuilder, typeL, name.c_str());
  LLVMBuildStore(builder, valueToStore, localAddr);
  return localAddr;
}

void makeHammerLocal(
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Local* local,
    Ref refToStore,
    bool knownLive) {
  auto localAddr = globalState->getRegion(local->type)->stackify(functionState, builder, local, refToStore, knownLive);
  blockState->addLocal(local->id, localAddr);
}

// Returns the new RC
LLVMValueRef adjustStrongRc(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    KindStructs* kindStructsSource,
    LLVMBuilderRef builder,
    Ref exprRef,
    Reference* refM,
    int amount) {
  switch (globalState->opt->regionOverride) {
    case RegionOverride::NAIVE_RC:
      break;
    case RegionOverride::FAST:
      assert(refM->ownership == Ownership::SHARE);
      break;
    case RegionOverride::RESILIENT_V3:
      assert(refM->ownership == Ownership::SHARE);
      break;
    default:
      assert(false);
  }

  auto controlBlockPtrLE =
      kindStructsSource->getControlBlockPtr(from, functionState, builder, exprRef, refM);
  auto rcPtrLE = kindStructsSource->getStrongRcPtrFromControlBlockPtr(builder, refM, controlBlockPtrLE);
//  auto oldRc = unmigratedLLVMBuildLoad(builder, rcPtrLE, "oldRc");
  auto newRc = adjustCounter(globalState, builder, globalState->metalCache->i32, rcPtrLE, amount);
//  flareAdjustStrongRc(from, globalState, functionState, builder, refM, controlBlockPtrLE, oldRc, newRc);
  return newRc;
}

LLVMValueRef strongRcIsZero(
    GlobalState* globalState,
    KindStructs* structs,
    LLVMBuilderRef builder,
    Reference* refM,
    ControlBlockPtrLE controlBlockPtrLE) {

  switch (globalState->opt->regionOverride) {
//    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
      break;
    case RegionOverride::FAST:
      assert(refM->ownership == Ownership::SHARE);
      break;
    case RegionOverride::RESILIENT_V3:
      assert(refM->ownership == Ownership::SHARE);
      break;
    default:
      assert(false);
  }

  return isZeroLE(builder, structs->getStrongRcFromControlBlockPtr(builder, refM, controlBlockPtrLE));
}


void buildPrint(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    const std::string& first) {
  auto voidLT = LLVMVoidTypeInContext(globalState->context);
  auto int8LT = LLVMInt8TypeInContext(globalState->context);
  std::vector<LLVMValueRef> indices = { constI64LE(globalState, 0) };
  auto s = LLVMBuildGEP2(builder, int8LT, globalState->getOrMakeStringConstant(first), indices.data(), indices.size(), "stringptr");
  assert(LLVMTypeOf(s) == LLVMPointerType(int8LT, 0));
  globalState->externs->printCStr.call(builder, {s}, "");
}

void buildPrint(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef exprLE) {
  auto voidLT = LLVMVoidTypeInContext(globalState->context);

  if (LLVMTypeOf(exprLE) == LLVMInt64TypeInContext(globalState->context)) {
    globalState->externs->printInt.call(builder, {exprLE}, "");
  } else if (LLVMGetTypeKind(LLVMTypeOf(exprLE)) == LLVMIntegerTypeKind) {
    assert(LLVMSizeOfTypeInBits(globalState->dataLayout, LLVMTypeOf(exprLE)) <= 64);
    auto int64LE = LLVMBuildZExt(builder, exprLE, LLVMInt64TypeInContext(globalState->context), "");
    globalState->externs->printInt.call(builder, {int64LE}, "");
  } else if (LLVMTypeOf(exprLE) == LLVMInt32TypeInContext(globalState->context)) {
    auto i64LE = LLVMBuildZExt(builder, exprLE, LLVMInt64TypeInContext(globalState->context), "asI64");
    globalState->externs->printInt.call(builder, {i64LE}, "");
  } else if (LLVMGetTypeKind(LLVMTypeOf(exprLE)) == LLVMPointerTypeKind) {
    // It's a pointer, so interpret it as a char* and print it as a string.
    globalState->externs->printCStr.call(builder, {exprLE}, "");
  } else {
    assert(false);
  }
}

void buildPrint(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    Ref ref) {
  buildPrint(globalState, builder, ref.refLE);
}

void buildPrint(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    int num) {
  buildPrint(globalState, builder, LLVMConstInt(LLVMInt64TypeInContext(globalState->context), num, false));
}

void buildAssertWithExitCode(
    GlobalState* globalState,
    LLVMValueRef function,
    LLVMBuilderRef builder,
    LLVMValueRef conditionLE,
    int exitCode,
    const std::string& failMessage) {
  buildIf(
      globalState, function, builder, isZeroLE(builder, conditionLE),
      [globalState, exitCode, failMessage](LLVMBuilderRef thenBuilder) {
        buildPrint(globalState, thenBuilder, failMessage + " Exiting!\n");
        auto exitCodeIntLE = LLVMConstInt(LLVMInt64TypeInContext(globalState->context), exitCode, false);
        globalState->externs->exit.call(thenBuilder, {exitCodeIntLE}, "");
      });
}

// We'll assert if conditionLE is false.
void buildAssert(
    GlobalState* globalState,
    LLVMValueRef function,
    LLVMBuilderRef builder,
    LLVMValueRef conditionLE,
    const std::string& failMessage) {
  buildAssertWithExitCode(globalState, function, builder, conditionLE, 1, failMessage);
}

// We'll assert if conditionLE is false.
void buildAssertV(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef conditionLE,
    const std::string& failMessage) {
  buildAssertWithExitCodeV(globalState, functionState, builder, conditionLE, 1, failMessage);
}


void buildAssertWithExitCodeV(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef conditionLE,
    int exitCode,
    const std::string& failMessage) {
  buildIfV(
      globalState, functionState, builder, isZeroLE(builder, conditionLE),
      [globalState, exitCode, failMessage](LLVMBuilderRef thenBuilder) {
        buildPrint(globalState, thenBuilder, failMessage + " Exiting!\n");
        auto exitCodeIntLE = LLVMConstInt(LLVMInt64TypeInContext(globalState->context), exitCode, false);
        globalState->externs->exit.call(thenBuilder, {exitCodeIntLE}, "");
      });
}

// We'll assert if conditionLE is false.
void buildAssertIntEq(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef aLE,
    LLVMValueRef bLE,
    const std::string& failMessage) {
  assert(LLVMTypeOf(aLE) == LLVMTypeOf(bLE));
  auto conditionLE = LLVMBuildICmp(builder, LLVMIntEQ, aLE, bLE, "assertCondition");
  buildIfV(
      globalState, functionState, builder, isZeroLE(builder, conditionLE),
      [globalState, functionState, failMessage, aLE, bLE](LLVMBuilderRef thenBuilder) {
        buildPrint(globalState, thenBuilder, "Assertion failed! Expected ");
        buildPrint(globalState, thenBuilder, aLE);
        buildPrint(globalState, thenBuilder, " to equal ");
        buildPrint(globalState, thenBuilder, bLE);
        buildPrint(globalState, thenBuilder, ".\n");
        buildPrint(globalState, thenBuilder, failMessage + " Exiting!\n");
        // See MPESC for status codes
        auto exitCodeIntLE = LLVMConstInt(LLVMInt64TypeInContext(globalState->context), 1, false);
        globalState->externs->exit.call(thenBuilder, {exitCodeIntLE}, "");
      });
}

Ref buildInterfaceCall(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Prototype* prototype,
    FuncPtrLE methodFunctionPtrLE,
    std::vector<Ref> argRefs,
    int virtualParamIndex) {
  auto virtualParamMT = prototype->params[virtualParamIndex];

  auto interfaceKindM = dynamic_cast<InterfaceKind*>(virtualParamMT->kind);
  assert(interfaceKindM);
//  int indexInEdge = globalState->getInterfaceMethod(interfaceKindM, prototype);

  auto virtualArgRef = argRefs[virtualParamIndex];

  LLVMValueRef itablePtrLE = nullptr;
  LLVMValueRef newVirtualArgLE = nullptr;
  std::tie(itablePtrLE, newVirtualArgLE) =
      globalState->getRegion(virtualParamMT)
          ->explodeInterfaceRef(
              functionState, builder, virtualParamMT, virtualArgRef);

  //buildFlare(FL(), globalState, functionState, builder, "Doing an interface call, objPtrLE: ", ptrToIntLE(globalState, builder, newVirtualArgLE), " itablePtrLE ", ptrToIntLE(globalState, builder, itablePtrLE));

  // We can't represent these arguments as refs, because this new virtual arg is a void*, and we
  // can't represent that as a ref.
  std::vector<LLVMValueRef> argsLE;
  for (int i = 0; i < argRefs.size(); i++) {
    argsLE.push_back(
        globalState->getRegion(prototype->params[i])
            ->checkValidReference(FL(),
                functionState, builder, false, prototype->params[i], argRefs[i]));
  }
  argsLE[virtualParamIndex] = newVirtualArgLE;

  buildFlare(FL(), globalState, functionState, builder);
  //buildFlare(FL(), globalState, functionState, builder, interfaceKindM->fullName->name, " ", ptrToIntLE(globalState, builder, methodFunctionPtrLE));

//  assert(LLVMGetTypeKind(LLVMTypeOf(itablePtrLE)) == LLVMPointerTypeKind);
//  auto funcPtrPtrLE =
//      unmigratedLLVMBuildStructGEP(
//          builder, itablePtrLE, indexInEdge, "methodPtrPtr");

//  auto funcPtrLE = unmigratedLLVMBuildLoad(builder, funcPtrPtrLE, "methodPtr");



  auto resultLE = methodFunctionPtrLE.call(builder, argsLE, "");
  assert(LLVMTypeOf(resultLE) == LLVMGetReturnType(methodFunctionPtrLE.funcLT));
  buildFlare(FL(), globalState, functionState, builder);
  return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, resultLE);
}

LLVMValueRef makeConstExpr(FunctionState* functionState, LLVMBuilderRef builder, LLVMTypeRef type, LLVMValueRef constExpr) {
  auto localAddr = makeBackendLocal(functionState, builder, type, "", constExpr);
  return LLVMBuildLoad2(builder, type, localAddr, "");
}

LLVMValueRef makeConstIntExpr(FunctionState* functionState, LLVMBuilderRef builder, LLVMTypeRef type, int64_t value) {
  return makeConstExpr(functionState, builder, type, LLVMConstInt(type, value, false));
}

void buildAssertCensusContains(
    AreaAndFileAndLine checkerAFL,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef ptrLE) {
  if (globalState->opt->census) {
    LLVMValueRef resultAsVoidPtrLE =
        LLVMBuildPointerCast(
            builder, ptrLE, LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0), "");

    auto isNullLE = LLVMBuildIsNull(builder, resultAsVoidPtrLE, "isNull");
    buildIfV(
        globalState, functionState, builder, isNullLE,
        [globalState, checkerAFL, ptrLE](LLVMBuilderRef thenBuilder) {
          buildPrintAreaAndFileAndLine(globalState, thenBuilder, checkerAFL);
          buildPrint(globalState, thenBuilder, "Object null, so not in census, exiting!\n");
          // See MPESC for status codes
          auto exitCodeIntLE = LLVMConstInt(LLVMInt64TypeInContext(globalState->context), 14, false);
          globalState->externs->exit.call(thenBuilder, {exitCodeIntLE}, "");
        });

    auto isRegisteredIntLE =
        globalState->externs->censusContains.call(builder, {resultAsVoidPtrLE}, "");
    auto isRegisteredBoolLE =
        LLVMBuildTruncOrBitCast(
            builder, isRegisteredIntLE, LLVMInt1TypeInContext(globalState->context), "");
    buildIfV(
        globalState, functionState, builder, isZeroLE(builder, isRegisteredBoolLE),
        [globalState, checkerAFL, ptrLE](LLVMBuilderRef thenBuilder) {
          buildPrintAreaAndFileAndLine(globalState, thenBuilder, checkerAFL);
          buildPrint(globalState, thenBuilder, "Object &");
          buildPrint(globalState, thenBuilder, ptrToIntLE(globalState, thenBuilder, ptrLE));
          buildPrint(globalState, thenBuilder, " not registered with census, exiting!\n");
          // See MPESC for status codes
          auto exitCodeIntLE = LLVMConstInt(LLVMInt64TypeInContext(globalState->context), 14, false);
          globalState->externs->exit.call(thenBuilder, {exitCodeIntLE}, "");
        });
  }
}

Ref buildCallV(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Prototype* prototype,
    std::vector<Ref> argRefs) {
  auto funcL = globalState->lookupFunction(prototype);

  buildFlare(FL(), globalState, functionState, builder, "Suspending function ", functionState->containingFuncName);
  buildFlare(FL(), globalState, functionState, builder, "Calling function ", prototype->name->name);

  std::vector<LLVMValueRef> argsLE;
  for (int i = 0; i < argRefs.size(); i++) {
    argsLE.push_back(
        globalState->getRegion(prototype->params[i])
            ->checkValidReference(FL(),
                functionState, builder, false, prototype->params[i], argRefs[i]));
  }

  buildFlare(FL(), globalState, functionState, builder, "Doing call");

  auto resultLE = funcL.call(builder, argsLE, "");

  buildFlare(FL(), globalState, functionState, builder, "Done with call");

  auto resultRef = wrap(globalState->getRegion(prototype->returnType), prototype->returnType, resultLE);
  globalState->getRegion(prototype->returnType)
      ->checkValidReference(FL(), functionState, builder, false, prototype->returnType, resultRef);

  if (prototype->returnType->kind == globalState->metalCache->never) {
    buildFlare(FL(), globalState, functionState, builder, "Done calling function ", prototype->name->name);
    buildFlare(FL(), globalState, functionState, builder, "Resuming function ", functionState->containingFuncName);
    LLVMBuildRet(builder, LLVMGetUndef(functionState->returnTypeL));
    return wrap(globalState->getRegion(globalState->metalCache->neverRef), globalState->metalCache->neverRef, globalState->neverPtr);
  } else {
    buildFlare(FL(), globalState, functionState, builder, "Done calling function ", prototype->name->name);
    buildFlare(FL(), globalState, functionState, builder, "Resuming function ", functionState->containingFuncName);
    return resultRef;
  }
}


LLVMValueRef buildMaybeNeverCall(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    FuncPtrLE funcL,
    std::vector<LLVMValueRef> argsLE) {
  auto resultLE = funcL.call(builder, argsLE, "");

  auto returnLT = LLVMGetReturnType(funcL.funcLT);
  if (returnLT == makeNeverType(globalState)) {
    LLVMBuildRet(builder, LLVMGetUndef(returnLT));
    return globalState->neverPtr;
  } else {
    return resultLE;
  }
}

LLVMValueRef getArgsAreaPtr(
    GlobalState* globalState,
    LLVMTypeRef argsStructLT,
    int64_t argsAreaSize,
    LLVMBuilderRef builder,
    LLVMValueRef sideStackStartPtrAsI8PtrLE) {
  auto int8LT = LLVMInt8TypeInContext(globalState->context);
  auto argsStructPtrLT = LLVMPointerType(argsStructLT, 0);

  auto offsetIntoNewStackLE = constI64LE(globalState, -argsAreaSize);
  auto sideStackPtrBeforeSwitchAsI8PtrLE =
      LLVMBuildGEP2(builder, int8LT, sideStackStartPtrAsI8PtrLE, &offsetIntoNewStackLE, 1, "sideStackPtr");

  auto argsAreaPtrBeforeSwitchLE =
      LLVMBuildPointerCast(builder, sideStackPtrBeforeSwitchAsI8PtrLE, argsStructPtrLT, "");

  return argsAreaPtrBeforeSwitchLE;
}

// A side call is a call using different stack memory
LLVMValueRef buildSideCall(
    GlobalState* globalState,
    LLVMBuilderRef entryBuilder,
    LLVMValueRef sideStackStartPtrAsI8PtrLE,
    FuncPtrLE calleeFuncLE,
    const std::vector<LLVMValueRef>& userArgsLE) {
  buildPrint(globalState, entryBuilder, "In buildSideCall!\n");

  auto int8LT = LLVMInt8TypeInContext(globalState->context);
  auto int32LT = LLVMInt32TypeInContext(globalState->context);
  auto int64LT = LLVMInt64TypeInContext(globalState->context);
  auto voidLT = LLVMVoidTypeInContext(globalState->context);
  auto voidPtrLT = LLVMPointerType(int8LT, 0);
  auto int8PtrLT = LLVMPointerType(int8LT, 0);

  assert(LLVMTypeOf(sideStackStartPtrAsI8PtrLE) == int8PtrLT);
  //assert(LLVMTypeOf(calleeFuncLE) == LLVMPointerType(calleeFuncLT, 0));

  // Eventually we'll make this point at a tiny struct that contains
  // the other arguments for the native call, plus this pointer.
  // For now, it just has the jmp_buf pointer from the setjmp call.
  auto originalStackPtrLE =
      buildMaybeNeverCall(
          globalState, entryBuilder, globalState->externs->stacksaveIntrinsic, {
              // If this was setjmp, we would supply the jmpBufPtrLE here.
          });


  auto mdstring = LLVMMDString("rsp\00", 4);
  auto metadata = LLVMMDNodeInContext(globalState->context, &mdstring, 1);

  int numPaddingInts = 0;//32;

  //assert(userArgsLE.size() == LLVMCountParamTypes(calleeFuncLT));
  std::vector<LLVMTypeRef> argsLT;
  // Padding for any spilling that needs to happen
  for (int i = 0; i < numPaddingInts; i++) {
    argsLT.push_back(int64LT);
  }
  for (auto userArgLE : userArgsLE) {
    argsLT.push_back(LLVMTypeOf(userArgLE));
  }
  argsLT.push_back(int8PtrLT);
  argsLT.push_back(calleeFuncLE.funcLT);
  auto returnLT = LLVMGetReturnType(calleeFuncLE.funcLT);
  if (returnLT != voidLT) {
    argsLT.push_back(returnLT);
  }
  auto argsStructLT =
      LLVMStructTypeInContext(
          globalState->context, argsLT.data(), argsLT.size(), false);
  // auto argsStructPtrLT = LLVMPointerType(argsStructLT, 0);

  // We hex round up because we get a bus error if we set the PC to something
  // that's not a multiple of 16 before a call.
  // It seems to be part of the calling convention, see
  // https://staffwww.fullcoll.edu/aclifton/cs241/lecture-stack-c-functions.html
  int64_t argsAreaSize =
      (((LLVMABISizeOfType(globalState->dataLayout, argsStructLT) - 1) | 15) + 1);

  // auto sideStackPtrBeforeSwitchAsI64LE =
  //     buildMaybeNeverCall(
  //         globalState, entryBuilder, globalState->externs->readRegisterI64Intrinsic,
  //         {metadata});

  // Careful, adding any instructions, INCLUDING PRINTING FOR DEBUGGING, could
  // change our registers and maybe spill onto the stack, causing havoc.

  {
    // Couldn't use the setjmp/longjmp pattern here of branching off the
    // setjmp return, because LLVM's longjmp intrinsic doesn't allow us
    // to give a result value for the second setjmp return.
    //   buildIf(
    //       globalState, entryFunctionL, entryBuilder,
    //       unmigratedLLVMBuildLoad(entryBuilder, ""),
    //       [globalState, int64LT, metadata](LLVMBuilderRef thenBuilder) {
    //         //buildPrint(globalState, thenBuilder, "Inside then!\n");

    // NO INSTRUCTIONS should be reordered past here!
    // Otherwise, they might spill onto the old stack, rather than
    // the side stack, where they're probably expected.
    // We don't want the RSP change to be further up, or any above instructions to come down to after the RSP change.
    // We *hope* this fence works, but there is doubt, see
    // https://preshing.com/20131125/acquire-and-release-fences-dont-work-the-way-youd-expect/
    LLVMBuildFence(entryBuilder, LLVMAtomicOrderingAcquireRelease, /*singleThread=*/true, "");

    // Careful, adding any instructions, INCLUDING PRINTING FOR DEBUGGING, could
    // change our registers and maybe spill onto the stack, causing havoc.

    auto argsAreaPtrBeforeSwitchLE =
        getArgsAreaPtr(globalState, argsStructLT, argsAreaSize, entryBuilder, sideStackStartPtrAsI8PtrLE);

    // Careful, adding any instructions, INCLUDING PRINTING FOR DEBUGGING, could
    // change our registers and maybe spill onto the stack, causing havoc.

    auto argsStructBeforeSwitchLE = LLVMGetUndef(argsStructLT);
    for (int i = 0; i < userArgsLE.size(); i++) {
      argsStructBeforeSwitchLE =
          LLVMBuildInsertValue(
              entryBuilder, argsStructBeforeSwitchLE, userArgsLE[i], numPaddingInts + i, "argsStruct");
    }
    argsStructBeforeSwitchLE =
        LLVMBuildInsertValue(
            entryBuilder, argsStructBeforeSwitchLE, originalStackPtrLE, numPaddingInts + userArgsLE.size() + 0, "argsStruct");
    argsStructBeforeSwitchLE =
        LLVMBuildInsertValue(
            entryBuilder, argsStructBeforeSwitchLE, calleeFuncLE.ptrLE, numPaddingInts + userArgsLE.size() + 1, "argsStruct");
    // We dont put anything in the return slot yet, so dont need to fill that part of the args area here

    // Careful, adding any instructions, INCLUDING PRINTING FOR DEBUGGING, could
    // change our registers and maybe spill onto the stack, causing havoc.

    LLVMBuildStore(entryBuilder, argsStructBeforeSwitchLE, argsAreaPtrBeforeSwitchLE);

    // Careful, adding any instructions, INCLUDING PRINTING FOR DEBUGGING, could
    // change our registers and maybe spill onto the stack, causing havoc.

    auto sideStackPtrAsI64BeforeSwitchLE =
        LLVMBuildPointerCast(entryBuilder, argsAreaPtrBeforeSwitchLE, int64LT, "");
    // Write the side stack pointer to the stack pointer register.
    buildMaybeNeverCall(
        globalState, entryBuilder, globalState->externs->writeRegisterI64Intrinsinc,
        { metadata, sideStackPtrAsI64BeforeSwitchLE });
  }
  // We don't want the RSP change to be further down, or any below instructions to come up to before the RSP change.
  // We *hope* this fence works, but there is doubt, see
  // https://preshing.com/20131125/acquire-and-release-fences-dont-work-the-way-youd-expect/
  LLVMBuildFence(entryBuilder, LLVMAtomicOrderingAcquireRelease, /*singleThread=*/true, "");

  // Careful, adding any instructions, INCLUDING PRINTING FOR DEBUGGING, could
  // change our registers and maybe spill onto the stack, causing havoc.

  // We must LOAD NO LOCALS here that were created before the above stack switch.
  // We might be loading them from the wrong stack.
  // Normally, we load locals from %rbp which is still good at this point,
  // but if we specify -fomit-frame-pointer we'll be loading it from %rsp,
  // *which we just changed*.

  // So, we load them from the side stack.

  auto sideStackPtrAfterSwitchAsI64LE =
      buildMaybeNeverCall(
          globalState, entryBuilder, globalState->externs->readRegisterI64Intrinsic,
          {metadata});

  // Careful, adding any instructions, INCLUDING PRINTING FOR DEBUGGING, could
  // change our registers and maybe spill onto the stack, causing havoc.

  // We dont want the above RSP read to happen after the below call.
  // We *hope* this fence works, but there is doubt, see
  // https://preshing.com/20131125/acquire-and-release-fences-dont-work-the-way-youd-expect/
  LLVMBuildFence(entryBuilder, LLVMAtomicOrderingAcquireRelease, /*singleThread=*/true, "");

  // Careful, adding any instructions, INCLUDING PRINTING FOR DEBUGGING, could
  // change our registers and maybe spill onto the stack, causing havoc.

  auto sideStackPtrAsI8PtrAfterSwitchLE =
      LLVMBuildIntToPtr(entryBuilder, sideStackPtrAfterSwitchAsI64LE, int8PtrLT, "");
  auto argsAreaPtrAfterSwitchLE =
      LLVMBuildPointerCast(
          entryBuilder,
          sideStackPtrAsI8PtrAfterSwitchLE, LLVMPointerType(argsStructLT, 0), "");

  // Careful, adding any instructions, INCLUDING PRINTING FOR DEBUGGING, could
  // change our registers and maybe spill onto the stack, causing havoc.

  auto argsStructAfterSwitchLE =
      LLVMBuildLoad2(entryBuilder, argsStructLT, argsAreaPtrAfterSwitchLE, "argsAreaPtrAfterSwitch");

  std::vector<LLVMValueRef> argsAfterSwitchLE;
  for (int i = 0; i < userArgsLE.size(); i++) {
    std::string argRegisterName = std::string("userArg") + std::to_string(i) + "AfterSwitch";
    argsAfterSwitchLE.push_back(
        LLVMBuildExtractValue(
            entryBuilder, argsStructAfterSwitchLE, numPaddingInts + i, argRegisterName.c_str()));
  }

  // Careful, adding any instructions, INCLUDING PRINTING FOR DEBUGGING, could
  // change our registers and maybe spill onto the stack, causing havoc.

  auto sideStackArgReturnDestPtrAfterSwitchLE =
      LLVMBuildExtractValue(
          entryBuilder, argsStructAfterSwitchLE, numPaddingInts + userArgsLE.size() + 0, "returnDest");
  auto calleeFuncPtrAfterSwitchLE =
      FuncPtrLE(
          calleeFuncLE.funcLT,
          LLVMBuildExtractValue(
              entryBuilder, argsStructAfterSwitchLE, numPaddingInts + userArgsLE.size() + 1, "calleeFuncPtr"));

  // Careful, adding any instructions, INCLUDING PRINTING FOR DEBUGGING, could
  // change our registers and maybe spill onto the stack, causing havoc.

  // Now that we're on the side stack, call the other function.
  // WARNING: If this is inlined, there might be some catastrophic instruction
  // reordering to before the above stack pointer switch.
  // Hopefully this is helped by the fences.
  // If it's inlined (which we have no way to prevent), but not reordered past
  // the fences, things might work fine. It will be writing and reading
  // at a bit higher offsets from %rbp, but that should just result in some
  // wasted bytes at the base of the stack.
  // Don't take my word for it though, low level = black magic. - Verdagon
  auto callResultBeforeReturnLE =
      buildMaybeNeverCall(
          globalState, entryBuilder, calleeFuncPtrAfterSwitchLE, argsAfterSwitchLE);

  // Careful, adding any instructions, INCLUDING PRINTING FOR DEBUGGING, could
  // change our registers and maybe spill onto the stack, causing havoc.

  auto calleeFuncReturnLT = LLVMGetReturnType(calleeFuncLE.funcLT);
  if (calleeFuncReturnLT != voidLT) {
    auto callResultPtrBeforeReturnLE =
        LLVMBuildStructGEP2(
            entryBuilder, argsStructLT, argsAreaPtrAfterSwitchLE, numPaddingInts + userArgsLE.size() + 2, "");

    // Careful, adding any instructions, INCLUDING PRINTING FOR DEBUGGING, could
    // change our registers and maybe spill onto the stack, causing havoc.

    LLVMBuildStore(entryBuilder, callResultBeforeReturnLE, callResultPtrBeforeReturnLE);
  }

  // Careful, adding any instructions, INCLUDING PRINTING FOR DEBUGGING, could
  // change our registers and maybe spill onto the stack, causing havoc.

  // NO INSTRUCTIONS should be reordered past here!
  // Otherwise, they might spill onto the side stack, rather than
  // the original stack, where they're probably expected.
  // We *hope* this fence works, but there is doubt, see
  // https://preshing.com/20131125/acquire-and-release-fences-dont-work-the-way-youd-expect/
  LLVMBuildFence(entryBuilder, LLVMAtomicOrderingAcquireRelease, /*singleThread=*/true, "");

  // Careful, adding any instructions, INCLUDING PRINTING FOR DEBUGGING, could
  // change our registers and maybe spill onto the stack, causing havoc.

  // Load the return stack address from the thread local. We shouldn't load it
  // from the stack because the stack pointer register still points to the other stack.
  //auto sideStackArgReturnDest = unmigratedLLVMBuildLoad(entryBuilder, globalState->sideStackArgReturnDestPtr, "");
  auto originalStackAddrAsIntLE =
      LLVMBuildPointerCast(entryBuilder, sideStackArgReturnDestPtrAfterSwitchLE, int64LT, "");
  // Restore the original stack address.
  buildMaybeNeverCall(
      globalState, entryBuilder, globalState->externs->writeRegisterI64Intrinsinc,
      { metadata, originalStackAddrAsIntLE });

  // Careful, adding any instructions, INCLUDING PRINTING FOR DEBUGGING, could
  // change our registers and maybe spill onto the stack, causing havoc.

  // We don't want the RSP change to be further down, or any below instructions to come up to before the RSP change.
  LLVMBuildFence(entryBuilder, LLVMAtomicOrderingAcquireRelease, /*singleThread=*/true, "");

  // Careful, adding any instructions, INCLUDING PRINTING FOR DEBUGGING, could
  // change our registers and maybe spill onto the stack, causing havoc.

//  auto sideStackPtrAfterReturnAsI64LE =
//      buildMaybeNeverCall(
//          globalState, entryBuilder, globalState->externs->readRegisterI64Intrinsic,
//          {metadata});

  // Careful, adding any instructions, INCLUDING PRINTING FOR DEBUGGING, could
  // change our registers and maybe spill onto the stack, causing havoc.

  LLVMValueRef callResultAfterReturnLE = nullptr;
  if (calleeFuncReturnLT != voidLT) {
    auto argsAreaPtrAfterReturnLE =
        getArgsAreaPtr(globalState, argsStructLT, argsAreaSize, entryBuilder, sideStackStartPtrAsI8PtrLE);

    // Careful, adding any instructions, INCLUDING PRINTING FOR DEBUGGING, could
    // change our registers and maybe spill onto the stack, causing havoc.

    auto callResultPtrAfterReturnLE =
        LLVMBuildStructGEP2(entryBuilder, argsStructLT, argsAreaPtrAfterReturnLE, numPaddingInts + userArgsLE.size() + 2, "callResultPtrAfterReturn");
    callResultAfterReturnLE =
        LLVMBuildLoad2(entryBuilder, returnLT, callResultPtrAfterReturnLE, "callResultAfterReturn");

    // Careful, adding any instructions, INCLUDING PRINTING FOR DEBUGGING, could
    // change our registers and maybe spill onto the stack, causing havoc.
  } else {
    callResultAfterReturnLE = LLVMGetUndef(LLVMVoidTypeInContext(globalState->context));
  }

  return callResultAfterReturnLE;
}


FuncPtrLE addExtern(LLVMModuleRef mod, const std::string& name, LLVMTypeRef retType, std::vector<LLVMTypeRef> paramTypes) {
  auto funcLT = LLVMFunctionType(retType, paramTypes.data(), paramTypes.size(), 0);
  auto funcLE = LLVMAddFunction(mod, name.c_str(), funcLT);
  return FuncPtrLE(funcLT, funcLE);
}
