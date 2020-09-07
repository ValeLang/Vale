#include <iostream>

#include "translatetype.h"

#include "shared.h"
#include "members.h"
#include "branch.h"
#include "controlblock.h"
#include "weaks.h"
#include "heap.h"


// Increments the RC, if needed
void acquireReference(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* resultRef,
    Ref expr) {

  auto sourceRnd = resultRef->referend;

  if (dynamic_cast<Int*>(sourceRnd) ||
      dynamic_cast<Bool*>(sourceRnd) ||
      dynamic_cast<Float*>(sourceRnd)) {
    // Do nothing for these, they're always inlined and copied.
  } else if (dynamic_cast<InterfaceReferend*>(sourceRnd) ||
      dynamic_cast<StructReferend*>(sourceRnd) ||
      dynamic_cast<KnownSizeArrayT*>(sourceRnd) ||
      dynamic_cast<UnknownSizeArrayT*>(sourceRnd) ||
      dynamic_cast<Str*>(sourceRnd)) {
    switch (globalState->opt->regionOverride) {
      case RegionOverride::ASSIST:
      case RegionOverride::NAIVE_RC: {
        if (resultRef->ownership == Ownership::OWN) {
          // We might be loading a member as an own if we're destructuring.
          // Don't adjust the RC, since we're only moving it.
        } else if (resultRef->ownership == Ownership::BORROW) {
          adjustStrongRc(from, globalState, functionState, builder, expr, resultRef, 1);
        } else if (resultRef->ownership == Ownership::WEAK) {
          aliasWeakRef(from, globalState, functionState, builder, resultRef, expr);
        } else if (resultRef->ownership == Ownership::SHARE) {
          if (resultRef->location == Location::INLINE) {
            // Do nothing, we can just let inline structs disappear
          } else {
            adjustStrongRc(from, globalState, functionState, builder, expr, resultRef, 1);
          }
        } else
          assert(false);
        break;
      }
      case RegionOverride::FAST: {
        if (resultRef->ownership == Ownership::OWN) {
          // We might be loading a member as an own if we're destructuring.
          // Don't adjust the RC, since we're only moving it.
        } else if (resultRef->ownership == Ownership::BORROW) {
          // Do nothing, fast mode doesn't do stuff for borrow refs.
        } else if (resultRef->ownership == Ownership::WEAK) {
          aliasWeakRef(from, globalState, functionState, builder, resultRef, expr);
        } else if (resultRef->ownership == Ownership::SHARE) {
          if (resultRef->location == Location::INLINE) {
            // Do nothing, we can just let inline structs disappear
          } else {
            adjustStrongRc(from, globalState, functionState, builder, expr, resultRef, 1);
          }
        } else
          assert(false);
        break;
      }
      case RegionOverride::RESILIENT_V0:
      case RegionOverride::RESILIENT_V1:
      case RegionOverride::RESILIENT_V2: {
        if (resultRef->ownership == Ownership::OWN) {
          // We might be loading a member as an own if we're destructuring.
          // Don't adjust the RC, since we're only moving it.
        } else if (resultRef->ownership == Ownership::BORROW ||
            resultRef->ownership == Ownership::WEAK) {
          aliasWeakRef(from, globalState, functionState, builder, resultRef, expr);
        } else if (resultRef->ownership == Ownership::SHARE) {
          if (resultRef->location == Location::INLINE) {
            // Do nothing, we can just let inline structs disappear
          } else {
            adjustStrongRc(from, globalState, functionState, builder, expr, resultRef, 1);
          }
        } else
          assert(false);
        break;
      }
      default:
        assert(false);
    }
  } else {
    std::cerr << "Unimplemented type in acquireReference: "
        << typeid(*resultRef->referend).name() << std::endl;
    assert(false);
  }
}

void discardOwningRef(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Reference* sourceTypeM,
    Ref exprLE) {
  auto exprWrapperPtrLE =
      WrapperPtrLE(
          sourceTypeM,
          checkValidReference(FL(), globalState, functionState, builder, sourceTypeM, exprLE));

  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST: {
      adjustStrongRc(
          AFL("Destroy decrementing the owning ref"),
          globalState, functionState, builder, exprLE, sourceTypeM, -1);
      // No need to check the RC, we know we're freeing right now.

      // Free it!
      freeConcrete(AFL("discardOwningRef"), globalState, functionState, blockState, builder,
          exprWrapperPtrLE, sourceTypeM);
      break;
    }
    case RegionOverride::NAIVE_RC: {
      auto rcLE =
          adjustStrongRc(
              AFL("Destroy decrementing the owning ref"),
              globalState, functionState, builder, exprLE, sourceTypeM, -1);
      buildIf(
          functionState, builder, isZeroLE(builder, rcLE),
          [globalState, functionState, blockState, exprLE, exprWrapperPtrLE, sourceTypeM](
              LLVMBuilderRef thenBuilder) {
            freeConcrete(AFL("discardOwningRef"), globalState, functionState, blockState, thenBuilder,
                exprWrapperPtrLE, sourceTypeM);
          });
      break;
    }
    case RegionOverride::FAST:
      // Do nothing

      // Free it!
      freeConcrete(AFL("discardOwningRef"), globalState, functionState, blockState, builder,
          exprWrapperPtrLE, sourceTypeM);
      break;
    case RegionOverride::RESILIENT_V0:
      // Mutables in resilient mode dont have strong RC, and also, they dont adjust
      // weak RC for owning refs

      // Free it!
      freeConcrete(AFL("discardOwningRef"), globalState, functionState, blockState, builder,
          exprWrapperPtrLE, sourceTypeM);
      break;
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      // Mutables in resilient v1+2 dont have strong RC, and also, they dont adjust
      // weak RC for owning refs

      // Free it!
      freeConcrete(AFL("discardOwningRef"), globalState, functionState, blockState, builder,
          exprWrapperPtrLE, sourceTypeM);
      break;
    default:
      assert(false);
      break;
    }
  }
}

void discard(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Reference* sourceMT,
    Ref sourceRef) {
  auto sourceRnd = sourceMT->referend;

  if (dynamic_cast<Int*>(sourceRnd) ||
      dynamic_cast<Bool*>(sourceRnd) ||
      dynamic_cast<Float*>(sourceRnd)) {
    // Do nothing for these, they're always inlined and copied.
  } else if (auto interfaceRnd = dynamic_cast<InterfaceReferend*>(sourceRnd)) {
    if (sourceMT->ownership == Ownership::OWN) {
      // We can't discard owns, they must be destructured.
      assert(false); // impl
    } else if (sourceMT->ownership == Ownership::BORROW) {
      switch (globalState->opt->regionOverride) {
        case RegionOverride::FAST:
          // Do nothing, unsafe mode has no strong rc for constraint refs
          break;
        case RegionOverride::ASSIST:
        case RegionOverride::NAIVE_RC: {
          auto rcLE = adjustStrongRc(from, globalState, functionState, builder, sourceRef, sourceMT, -1);
          if (globalState->opt->regionOverride == RegionOverride::NAIVE_RC) {
            buildIf(
                functionState, builder, isZeroLE(builder, rcLE),
                [globalState, functionState, blockState, sourceRef, sourceMT](
                    LLVMBuilderRef thenBuilder) {
                  auto sourceWrapperPtrLE =
                      WrapperPtrLE(
                          sourceMT,
                          checkValidReference(FL(), globalState, functionState, thenBuilder, sourceMT, sourceRef));
                  freeConcrete(FL(), globalState, functionState, blockState, thenBuilder,
                      sourceWrapperPtrLE, sourceMT);
                });
          }
          break;
        }
        case RegionOverride::RESILIENT_V0:
        case RegionOverride::RESILIENT_V1:
        case RegionOverride::RESILIENT_V2: {
          discardWeakRef(from, globalState, functionState, builder, sourceMT, sourceRef);
          break;
        }
        default:
          assert(false);
      }
    } else if (sourceMT->ownership == Ownership::WEAK) {
      discardWeakRef(from, globalState, functionState, builder, sourceMT, sourceRef);
    } else if (sourceMT->ownership == Ownership::SHARE) {
      if (sourceMT->location == Location::INLINE) {
        assert(false); // impl
      } else {
        auto rcLE = adjustStrongRc(from, globalState, functionState, builder, sourceRef, sourceMT, -1);
        buildIf(
            functionState,
            builder,
            isZeroLE(builder, rcLE),
            [globalState, functionState, sourceRef, interfaceRnd, sourceMT](LLVMBuilderRef thenBuilder) {
              auto immDestructor = globalState->program->getImmDestructor(sourceMT->referend);

              auto interfaceM = globalState->program->getInterface(interfaceRnd->fullName);
              int indexInEdge = -1;
              for (int i = 0; i < interfaceM->methods.size(); i++) {
                if (interfaceM->methods[i]->prototype == immDestructor) {
                  indexInEdge = i;
                }
              }
              assert(indexInEdge >= 0);

              std::vector<Ref> argExprsL = { sourceRef };
              buildInterfaceCall(globalState, functionState, thenBuilder, immDestructor, argExprsL, 0, indexInEdge);
            });
      }
    } else assert(false);
  } else if (dynamic_cast<StructReferend*>(sourceRnd) ||
      dynamic_cast<KnownSizeArrayT*>(sourceRnd) ||
      dynamic_cast<UnknownSizeArrayT*>(sourceRnd)) {
    if (sourceMT->ownership == Ownership::OWN) {
      // We can't discard owns, they must be destructured.
      assert(false);
    } else if (sourceMT->ownership == Ownership::BORROW) {

      switch (globalState->opt->regionOverride) {
        case RegionOverride::FAST:
          // Do nothing, unsafe mode has no strong rc for constraint refs
          break;
        case RegionOverride::ASSIST:
        case RegionOverride::NAIVE_RC: {
          buildFlare(from, globalState, functionState, builder, "Decr strong concrete borrow!");
          auto rcLE = adjustStrongRc(from, globalState, functionState, builder, sourceRef, sourceMT, -1);
          if (globalState->opt->regionOverride == RegionOverride::NAIVE_RC) {
            buildIf(
                functionState, builder, isZeroLE(builder, rcLE),
                [globalState, functionState, blockState, sourceRef, sourceMT](
                    LLVMBuilderRef thenBuilder) {
                  auto sourceWrapperPtrLE =
                      WrapperPtrLE(
                          sourceMT,
                          checkValidReference(FL(), globalState, functionState, thenBuilder, sourceMT, sourceRef));
                  freeConcrete(FL(), globalState, functionState, blockState, thenBuilder,
                      sourceWrapperPtrLE, sourceMT);
                });
          }
          break;
        }
        case RegionOverride::RESILIENT_V0:
        case RegionOverride::RESILIENT_V1:
        case RegionOverride::RESILIENT_V2: {
          discardWeakRef(from, globalState, functionState, builder, sourceMT, sourceRef);
          break;
        }
        default:
          assert(false);
      }

    } else if (sourceMT->ownership == Ownership::WEAK) {
      discardWeakRef(from, globalState, functionState, builder, sourceMT, sourceRef);
    } else if (sourceMT->ownership == Ownership::SHARE) {
      if (sourceMT->location == Location::INLINE) {
        // Do nothing, we can just let inline structs disappear
      } else {
        auto rcLE = adjustStrongRc(from, globalState, functionState, builder, sourceRef, sourceMT, -1);
        buildIf(
            functionState,
            builder,
            isZeroLE(builder, rcLE),
            [from, globalState, functionState, sourceRef, sourceMT](LLVMBuilderRef thenBuilder) {
              auto immDestructor = globalState->program->getImmDestructor(sourceMT->referend);
              auto funcL = globalState->getFunction(immDestructor->name);

              auto sourceLE =
                  checkValidReference(
                      FL(), globalState, functionState, thenBuilder, sourceMT, sourceRef);
              std::vector<LLVMValueRef> argExprsL = { sourceLE };
              return LLVMBuildCall(thenBuilder, funcL, argExprsL.data(), argExprsL.size(), "");
            });
      }
    } else assert(false);
  } else if (dynamic_cast<Str*>(sourceRnd)) {
    assert(sourceMT->ownership == Ownership::SHARE);
    auto rcLE = adjustStrongRc(from, globalState, functionState, builder, sourceRef, sourceMT, -1);
    buildIf(
        functionState,
        builder,
        isZeroLE(builder, rcLE),
        [from, globalState, functionState, blockState, sourceRef, sourceMT](LLVMBuilderRef thenBuilder) {
          buildFlare(from, globalState, functionState, thenBuilder, "Freeing shared str!");
          auto sourceWrapperPtrLE =
              WrapperPtrLE(
                  sourceMT,
                  checkValidReference(FL(), globalState, functionState, thenBuilder, sourceMT, sourceRef));
          freeConcrete(
              from, globalState, functionState, blockState, thenBuilder, sourceWrapperPtrLE, sourceMT);
        });
  } else {
    std::cerr << "Unimplemented type in discard: "
        << typeid(*sourceMT->referend).name() << std::endl;
    assert(false);
  }
}
