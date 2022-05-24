#include "../../../region/common/common.h"
#include "../../../region/common/fatweaks/fatweaks.h"
#include "../../../utils/counters.h"
#include "shared.h"

#include "../../../translatetype.h"
#include "../../../region/common/controlblock.h"
#include "../../../region/linear/linear.h"
#include "../../../region/rcimm/rcimm.h"
#include "../../../utils/branch.h"

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
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
      break;
    case RegionOverride::FAST:
      assert(refM->ownership == Ownership::SHARE);
      break;
    case RegionOverride::RESILIENT_V3: case RegionOverride::RESILIENT_V4:
      assert(refM->ownership == Ownership::SHARE);
      break;
    default:
      assert(false);
  }

  auto controlBlockPtrLE =
      kindStructsSource->getControlBlockPtr(from, functionState, builder, exprRef, refM);
  auto rcPtrLE = kindStructsSource->getStrongRcPtrFromControlBlockPtr(builder, refM, controlBlockPtrLE);
//  auto oldRc = LLVMBuildLoad(builder, rcPtrLE, "oldRc");
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
    case RegionOverride::ASSIST:
    case RegionOverride::NAIVE_RC:
      break;
    case RegionOverride::FAST:
      assert(refM->ownership == Ownership::SHARE);
      break;
    case RegionOverride::RESILIENT_V3: case RegionOverride::RESILIENT_V4:
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
  std::vector<LLVMValueRef> indices = { constI64LE(globalState, 0) };
  auto s = LLVMBuildGEP(builder, globalState->getOrMakeStringConstant(first), indices.data(), indices.size(), "stringptr");
  assert(LLVMTypeOf(s) == LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0));
  LLVMBuildCall(builder, globalState->externs->printCStr, &s, 1, "");
}

void buildPrint(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef exprLE) {
  if (LLVMTypeOf(exprLE) == LLVMInt64TypeInContext(globalState->context)) {
    LLVMBuildCall(builder, globalState->externs->printInt, &exprLE, 1, "");
  } else if (LLVMGetTypeKind(LLVMTypeOf(exprLE)) == LLVMIntegerTypeKind) {
    assert(LLVMSizeOfTypeInBits(globalState->dataLayout, LLVMTypeOf(exprLE)) <= 64);
    auto int64LE = LLVMBuildZExt(builder, exprLE, LLVMInt64TypeInContext(globalState->context), "");
    LLVMBuildCall(builder, globalState->externs->printInt, &int64LE, 1, "");
  } else if (LLVMTypeOf(exprLE) == LLVMInt32TypeInContext(globalState->context)) {
    auto i64LE = LLVMBuildZExt(builder, exprLE, LLVMInt64TypeInContext(globalState->context), "asI64");
    LLVMBuildCall(builder, globalState->externs->printInt, &i64LE, 1, "");
  } else if (LLVMTypeOf(exprLE) == LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0)) {
    LLVMBuildCall(builder, globalState->externs->printCStr, &exprLE, 1, "");
//  } else if (LLVMTypeOf(exprLE) == LLVMPointerType(LLVMInt8TypeInContext(globalState->context), 0)) {
//    auto asIntLE = LLVMBuildPointerCast(builder, exprLE, LLVMInt64TypeInContext(globalState->context), "asI64");
//    LLVMBuildCall(builder, globalState->printInt, &asIntLE, 1, "");
  } else {
    assert(false);
//    buildPrint(
//        globalState,
//        builder,
//        LLVMBuildPointerCast(builder, exprLE, LLVMInt64TypeInContext(globalState->context), ""));
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

// We'll assert if conditionLE is false.
void buildAssert(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    LLVMValueRef conditionLE,
    const std::string& failMessage) {
  buildAssertWithExitCode(globalState, functionState, builder, conditionLE, 1, failMessage);
}

void buildAssertWithExitCode(
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
        LLVMBuildCall(thenBuilder, globalState->externs->exit, &exitCodeIntLE, 1, "");
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
        LLVMBuildCall(thenBuilder, globalState->externs->exit, &exitCodeIntLE, 1, "");
      });
}

Ref buildInterfaceCall(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Prototype* prototype,
    LLVMValueRef methodFunctionPtrLE,
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
                functionState, builder, prototype->params[i], argRefs[i]));
  }
  argsLE[virtualParamIndex] = newVirtualArgLE;

  buildFlare(FL(), globalState, functionState, builder);
  //buildFlare(FL(), globalState, functionState, builder, interfaceKindM->fullName->name, " ", ptrToIntLE(globalState, builder, methodFunctionPtrLE));

//  assert(LLVMGetTypeKind(LLVMTypeOf(itablePtrLE)) == LLVMPointerTypeKind);
//  auto funcPtrPtrLE =
//      LLVMBuildStructGEP(
//          builder, itablePtrLE, indexInEdge, "methodPtrPtr");

//  auto funcPtrLE = LLVMBuildLoad(builder, funcPtrPtrLE, "methodPtr");



  auto resultLE =
      LLVMBuildCall(builder, methodFunctionPtrLE, argsLE.data(), argsLE.size(), "");
  buildFlare(FL(), globalState, functionState, builder);
  return wrap(globalState->getRegion(prototype->returnType), prototype->returnType, resultLE);
}

LLVMValueRef makeConstExpr(FunctionState* functionState, LLVMBuilderRef builder, LLVMValueRef constExpr) {
  auto localAddr = makeBackendLocal(functionState, builder, LLVMTypeOf(constExpr), "", constExpr);
  return LLVMBuildLoad(builder, localAddr, "");
}

LLVMValueRef makeConstIntExpr(FunctionState* functionState, LLVMBuilderRef builder, LLVMTypeRef type, int64_t value) {
  return makeConstExpr(functionState, builder, LLVMConstInt(type, value, false));
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
          LLVMBuildCall(thenBuilder, globalState->externs->exit, &exitCodeIntLE, 1, "");
        });

    auto isRegisteredIntLE =
        LLVMBuildCall(
            builder, globalState->externs->censusContains, &resultAsVoidPtrLE, 1, "");
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
          LLVMBuildCall(thenBuilder, globalState->externs->exit, &exitCodeIntLE, 1, "");
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
                functionState, builder, prototype->params[i], argRefs[i]));
  }

  buildFlare(FL(), globalState, functionState, builder, "Doing call");

  auto resultLE = LLVMBuildCall(builder, funcL, argsLE.data(), argsLE.size(), "");

  buildFlare(FL(), globalState, functionState, builder, "Done with call");

  auto resultRef = wrap(globalState->getRegion(prototype->returnType), prototype->returnType, resultLE);
  globalState->getRegion(prototype->returnType)->checkValidReference(FL(), functionState, builder, prototype->returnType, resultRef);

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


LLVMValueRef buildCall(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    LLVMValueRef funcL,
    std::vector<LLVMValueRef> argsLE) {
  auto resultLE = LLVMBuildCall(builder, funcL, argsLE.data(), argsLE.size(), "");

  auto returnLT = LLVMTypeOf(resultLE);
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
  auto argsStructPtrLT = LLVMPointerType(argsStructLT, 0);

  auto offsetIntoNewStackLE = constI64LE(globalState, -argsAreaSize);
  auto sideStackPtrBeforeSwitchAsI8PtrLE =
      LLVMBuildGEP(builder, sideStackStartPtrAsI8PtrLE, &offsetIntoNewStackLE, 1, "sideStackPtr");

  auto argsAreaPtrBeforeSwitchLE =
      LLVMBuildPointerCast(builder, sideStackPtrBeforeSwitchAsI8PtrLE, argsStructPtrLT, "");

  return argsAreaPtrBeforeSwitchLE;
}

// A side call is a call using different stack memory
LLVMValueRef buildSideCall(
    GlobalState* globalState,
    LLVMTypeRef returnLT,
    LLVMBuilderRef entryBuilder,
    LLVMValueRef sideStackStartPtrAsI8PtrLE,
    LLVMValueRef calleeFuncLE,
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
      buildCall(globalState, entryBuilder, globalState->externs->stacksaveIntrinsic, {
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
  argsLT.push_back(LLVMTypeOf(calleeFuncLE));
  if (returnLT != voidLT) {
    argsLT.push_back(returnLT);
  }
  auto argsStructLT =
      LLVMStructTypeInContext(
          globalState->context, argsLT.data(), argsLT.size(), false);
  auto argsStructPtrLT = LLVMPointerType(argsStructLT, 0);

  // We hex round up because we get a bus error if we set the PC to something
  // that's not a multiple of 16 before a call.
  // It seems to be part of the calling convention, see
  // https://staffwww.fullcoll.edu/aclifton/cs241/lecture-stack-c-functions.html
  int64_t argsAreaSize =
      (((LLVMABISizeOfType(globalState->dataLayout, argsStructLT) - 1) | 15) + 1);

  //buildPrint(globalState, entryBuilder, "Starting!\n");

  auto sideStackPtrBeforeSwitchAsI64LE =
      buildCall(
          globalState, entryBuilder, globalState->externs->readRegisterI64Intrinsic,
          {metadata});
  //buildPrint(globalState, entryBuilder, "Before switch stack pointer:");
  //buildPrint(globalState, entryBuilder, sideStackPtrBeforeSwitchAsI64LE);
  //buildPrint(globalState, entryBuilder, "\n");

  {
    // Couldn't use the setjmp/longjmp pattern here of branching off the
    // setjmp return, because LLVM's longjmp intrinsic doesn't allow us
    // to give a result value for the second setjmp return.
    //   buildIf(
    //       globalState, entryFunctionL, entryBuilder,
    //       LLVMBuildLoad(entryBuilder, ""),
    //       [globalState, int64LT, metadata](LLVMBuilderRef thenBuilder) {
    //         //buildPrint(globalState, thenBuilder, "Inside then!\n");

    // NO INSTRUCTIONS should be reordered past here!
    // Otherwise, they might spill onto the old stack, rather than
    // the side stack, where they're probably expected.
    // We don't want the RSP change to be further up, or any above instructions to come down to after the RSP change.
    // We *hope* this fence works, but there is doubt, see
    // https://preshing.com/20131125/acquire-and-release-fences-dont-work-the-way-youd-expect/
    LLVMBuildFence(entryBuilder, LLVMAtomicOrderingAcquireRelease, /*singleThread=*/true, "");

    //buildPrint(globalState, entryBuilder, "After fence!\n");

    //buildPrint(globalState, entryBuilder, "Stack addr:");
    //buildPrint(globalState, entryBuilder, ptrToIntLE(globalState, entryBuilder, sideStackStartPtrAsI8PtrLE));
    //buildPrint(globalState, entryBuilder, "\n");

    //buildPrint(globalState, entryBuilder, "Size:");
    //buildPrint(globalState, entryBuilder, constI64LE(globalState, argsAreaSize));
    //buildPrint(globalState, entryBuilder, "\n");

//    auto offsetIntoNewStackLE = constI64LE(globalState, -argsAreaSize);
//    auto sideStackPtrBeforeSwitchAsI8PtrLE =
//        LLVMBuildGEP(entryBuilder, sideStackStartPtrAsI8PtrLE, &offsetIntoNewStackLE, 1, "sideStackPtr");

    auto argsAreaPtrBeforeSwitchLE =
        getArgsAreaPtr(globalState, argsStructLT, argsAreaSize, entryBuilder, sideStackStartPtrAsI8PtrLE);

    //buildPrint(globalState, entryBuilder, "Args addr:");
    //buildPrint(globalState, entryBuilder, ptrToIntLE(globalState, entryBuilder, argsAreaPtrBeforeSwitchLE));
    //buildPrint(globalState, entryBuilder, "\n");

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
            entryBuilder, argsStructBeforeSwitchLE, calleeFuncLE, numPaddingInts + userArgsLE.size() + 1, "argsStruct");
    // We dont put anything in the return slot yet, so dont need to fill that part of the args area here

    //buildPrint(globalState, entryBuilder, "Callee func ptr beforehand:");
    //buildPrint(globalState, entryBuilder, ptrToIntLE(globalState, entryBuilder, calleeFuncLE));
    //buildPrint(globalState, entryBuilder, "\n");


    //buildPrint(globalState, entryBuilder, "Before args store!\n");

    LLVMBuildStore(entryBuilder, argsStructBeforeSwitchLE, argsAreaPtrBeforeSwitchLE);

    //buildPrint(globalState, entryBuilder, "Before first rsp write!\n");

    auto sideStackPtrAsI64BeforeSwitchLE =
        LLVMBuildPointerCast(entryBuilder, argsAreaPtrBeforeSwitchLE, int64LT, "");
    // Write the side stack pointer to the stack pointer register.
    buildCall(
        globalState, entryBuilder, globalState->externs->writeRegisterI64Intrinsinc,
        { metadata, sideStackPtrAsI64BeforeSwitchLE });
  }
  // We don't want the RSP change to be further down, or any below instructions to come up to before the RSP change.
  // We *hope* this fence works, but there is doubt, see
  // https://preshing.com/20131125/acquire-and-release-fences-dont-work-the-way-youd-expect/
  LLVMBuildFence(entryBuilder, LLVMAtomicOrderingAcquireRelease, /*singleThread=*/true, "");

  // We must LOAD NO LOCALS here that were created before the above stack switch.
  // We might be loading them from the wrong stack.
  // Normally, we load locals from %rbp which is still good at this point,
  // but if we specify -fomit-frame-pointer we'll be loading it from %rsp,
  // *which we just changed*.

  // So, we load them from the side stack.

  auto sideStackPtrAfterSwitchAsI64LE =
      buildCall(
          globalState, entryBuilder, globalState->externs->readRegisterI64Intrinsic,
          {metadata});

  // We dont want the above RSP read to happen after the below call.
  // We *hope* this fence works, but there is doubt, see
  // https://preshing.com/20131125/acquire-and-release-fences-dont-work-the-way-youd-expect/
  LLVMBuildFence(entryBuilder, LLVMAtomicOrderingAcquireRelease, /*singleThread=*/true, "");

  //buildPrint(globalState, entryBuilder, "Loaded stack pointer after switch:");
  //buildPrint(globalState, entryBuilder, sideStackPtrAfterSwitchAsI64LE);
  //buildPrint(globalState, entryBuilder, "\n");

  auto sideStackPtrAsI8PtrAfterSwitchLE =
      LLVMBuildIntToPtr(entryBuilder, sideStackPtrAfterSwitchAsI64LE, int8PtrLT, "");
  auto argsAreaPtrAfterSwitchLE =
      LLVMBuildPointerCast(
          entryBuilder,
          sideStackPtrAsI8PtrAfterSwitchLE, LLVMPointerType(argsStructLT, 0), "");

  //buildPrint(globalState, entryBuilder, "Args area pointer after switch:");
  //buildPrint(globalState, entryBuilder, ptrToIntLE(globalState, entryBuilder, argsAreaPtrAfterSwitchLE));
  //buildPrint(globalState, entryBuilder, "\n");

  auto argsStructAfterSwitchLE =
      LLVMBuildLoad(entryBuilder, argsAreaPtrAfterSwitchLE, "argsAreaPtrAfterSwitch");

  std::vector<LLVMValueRef> argsAfterSwitchLE;
  for (int i = 0; i < userArgsLE.size(); i++) {
    std::string argRegisterName = std::string("userArg") + std::to_string(i) + "AfterSwitch";
    argsAfterSwitchLE.push_back(
        LLVMBuildExtractValue(
            entryBuilder, argsStructAfterSwitchLE, numPaddingInts + i, argRegisterName.c_str()));
  }

  auto sideStackArgReturnDestPtrAfterSwitchLE =
      LLVMBuildExtractValue(entryBuilder, argsStructAfterSwitchLE, numPaddingInts + userArgsLE.size() + 0, "returnDest");
  auto calleeFuncPtrAfterSwitchLE =
      LLVMBuildExtractValue(entryBuilder, argsStructAfterSwitchLE, numPaddingInts + userArgsLE.size() + 1, "calleeFuncPtr");


  //buildPrint(globalState, entryBuilder, "Callee func ptr:");
  //buildPrint(globalState, entryBuilder, ptrToIntLE(globalState, entryBuilder, calleeFuncPtrAfterSwitchLE));
  //buildPrint(globalState, entryBuilder, "\n");

  //buildPrint(globalState, entryBuilder, "Return dest ptr:");
  //buildPrint(globalState, entryBuilder, ptrToIntLE(globalState, entryBuilder, sideStackArgReturnDestPtrAfterSwitchLE));
  //buildPrint(globalState, entryBuilder, "\n");

  //buildPrint(globalState, entryBuilder, "Before inner call!\n");

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
      buildCall(globalState, entryBuilder, calleeFuncPtrAfterSwitchLE, argsAfterSwitchLE);

  //buildPrint(globalState, entryBuilder, "After inner call!\n");

  if (returnLT == int64LT || returnLT == int32LT) {
    //buildPrint(globalState, entryBuilder, "Result:");
    //buildPrint(globalState, entryBuilder, callResultBeforeReturnLE);
    //buildPrint(globalState, entryBuilder, "\n");
  }
  if (returnLT != voidLT) {
    auto callResultPtrBeforeReturnLE =
        LLVMBuildStructGEP(entryBuilder, argsAreaPtrAfterSwitchLE, numPaddingInts + userArgsLE.size() + 2, "");

    //buildPrint(globalState, entryBuilder, "Result addr:");
    //buildPrint(globalState, entryBuilder, ptrToIntLE(globalState, entryBuilder, callResultPtrBeforeReturnLE));
    //buildPrint(globalState, entryBuilder, "\n");

    LLVMBuildStore(entryBuilder, callResultBeforeReturnLE, callResultPtrBeforeReturnLE);
  }

  // NO INSTRUCTIONS should be reordered past here!
  // Otherwise, they might spill onto the side stack, rather than
  // the original stack, where they're probably expected.
  // We *hope* this fence works, but there is doubt, see
  // https://preshing.com/20131125/acquire-and-release-fences-dont-work-the-way-youd-expect/
  LLVMBuildFence(entryBuilder, LLVMAtomicOrderingAcquireRelease, /*singleThread=*/true, "");

  // Load the return stack address from the thread local. We shouldn't load it
  // from the stack because the stack pointer register still points to the other stack.
  //auto sideStackArgReturnDest = LLVMBuildLoad(entryBuilder, globalState->sideStackArgReturnDestPtr, "");
  auto originalStackAddrAsIntLE =
      LLVMBuildPointerCast(entryBuilder, sideStackArgReturnDestPtrAfterSwitchLE, int64LT, "");
  // Restore the original stack address.
  buildCall(
      globalState, entryBuilder, globalState->externs->writeRegisterI64Intrinsinc,
      { metadata, originalStackAddrAsIntLE });

  // We don't want the RSP change to be further down, or any below instructions to come up to before the RSP change.
  LLVMBuildFence(entryBuilder, LLVMAtomicOrderingAcquireRelease, /*singleThread=*/true, "");

  //buildPrint(globalState, entryBuilder, "After return switch!\n");

  auto sideStackPtrAfterReturnAsI64LE =
      buildCall(
          globalState, entryBuilder, globalState->externs->readRegisterI64Intrinsic,
          {metadata});
  //buildPrint(globalState, entryBuilder, "After return stack pointer:");
  //buildPrint(globalState, entryBuilder, sideStackPtrAfterReturnAsI64LE);
  //buildPrint(globalState, entryBuilder, "\n");

  LLVMValueRef callResultAfterReturnLE = nullptr;
  if (returnLT != voidLT) {
    auto argsAreaPtrAfterReturnLE =
        getArgsAreaPtr(globalState, argsStructLT, argsAreaSize, entryBuilder, sideStackStartPtrAsI8PtrLE);

    //buildPrint(globalState, entryBuilder, "After return side stack pointer:");
    //buildPrint(globalState, entryBuilder, ptrToIntLE(globalState, entryBuilder, sideStackStartPtrAsI8PtrLE));
    //buildPrint(globalState, entryBuilder, "\n");

    auto callResultPtrAfterReturnLE =
        LLVMBuildStructGEP(entryBuilder, argsAreaPtrAfterReturnLE, numPaddingInts + userArgsLE.size() + 2, "callResultPtrAfterReturn");
    //buildPrint(globalState, entryBuilder, "Result addr:");
    //buildPrint(globalState, entryBuilder, ptrToIntLE(globalState, entryBuilder, callResultPtrAfterReturnLE));
    //buildPrint(globalState, entryBuilder, "\n");
    callResultAfterReturnLE = LLVMBuildLoad(entryBuilder, callResultPtrAfterReturnLE, "callResultAfterReturn");

    if (returnLT == int64LT || returnLT == int32LT) {
      //buildPrint(globalState, entryBuilder, "Result after:");
      //buildPrint(globalState, entryBuilder, callResultAfterReturnLE);
      //buildPrint(globalState, entryBuilder, "\n");
    }
  } else {
    callResultAfterReturnLE = LLVMGetUndef(LLVMVoidTypeInContext(globalState->context));
  }

  //buildPrint(globalState, entryBuilder, "Done!\n");

  return callResultAfterReturnLE;
}


LLVMValueRef addExtern(LLVMModuleRef mod, const std::string& name, LLVMTypeRef retType, std::vector<LLVMTypeRef> paramTypes) {
  LLVMTypeRef funcType = LLVMFunctionType(retType, paramTypes.data(), paramTypes.size(), 0);
  return LLVMAddFunction(mod, name.c_str(), funcType);
}
