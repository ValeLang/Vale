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

LLVMValueRef makeMidasLocal(
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
  buildIf(
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
  buildIf(
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
  buildFlare(FL(), globalState, functionState, builder);

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
  auto localAddr = makeMidasLocal(functionState, builder, LLVMTypeOf(constExpr), "", constExpr);
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
    buildIf(globalState, functionState, builder, isNullLE,
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
    buildIf(globalState, functionState, builder, isZeroLE(builder, isRegisteredBoolLE),
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

Ref buildCall(
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


LLVMValueRef addExtern(LLVMModuleRef mod, const std::string& name, LLVMTypeRef retType, std::vector<LLVMTypeRef> paramTypes) {
  LLVMTypeRef funcType = LLVMFunctionType(retType, paramTypes.data(), paramTypes.size(), 0);
  return LLVMAddFunction(mod, name.c_str(), funcType);
}
