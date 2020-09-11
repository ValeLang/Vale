#include <iostream>
#include <utils/counters.h>

#include "translatetype.h"

#include "shared.h"
#include "members.h"
#include "utils/branch.h"
#include "region/common/controlblock.h"
#include "weaks.h"
#include "region/common/heap.h"


void discardOwningRef(
    AreaAndFileAndLine from,
    GlobalState* globalState,
    FunctionState* functionState,
    BlockState* blockState,
    LLVMBuilderRef builder,
    Reference* sourceMT,
    Ref sourceRef) {
  auto exprWrapperPtrLE =
      functionState->defaultRegion->makeWrapperPtr(
          sourceMT,
          checkValidReference(FL(), globalState, functionState, builder, sourceMT, sourceRef));

  switch (globalState->opt->regionOverride) {
    case RegionOverride::ASSIST: {
      adjustStrongRc(
          AFL("Destroy decrementing the owning ref"),
          globalState, functionState, builder, sourceRef, sourceMT, -1);
      // No need to check the RC, we know we're freeing right now.

      // Free it!
      auto controlBlockPtrLE = getConcreteControlBlockPtr(globalState, builder, exprWrapperPtrLE);
      deallocate(AFL("discardOwningRef"), globalState, functionState, blockState, builder,
          controlBlockPtrLE,
          sourceMT,
      );
      break;
    }
    case RegionOverride::NAIVE_RC: {
      auto rcLE =
          adjustStrongRc(
              AFL("Destroy decrementing the owning ref"),
              globalState, functionState, builder, sourceRef, sourceMT, -1);
      buildIf(
          functionState, builder, isZeroLE(builder, rcLE),
          [globalState, functionState, blockState, sourceRef, exprWrapperPtrLE, sourceMT](
              LLVMBuilderRef thenBuilder) {
            if (dynamic_cast<InterfaceReferend*>(sourceMT->referend)) {
              auto sourceInterfacePtrLE =
                  InterfaceFatPtrLE(globalState,
                      sourceMT,
                      checkValidReference(FL(), globalState, functionState, thenBuilder, sourceMT, sourceRef));
              auto controlBlockPtrLE = getControlBlockPtr(globalState, thenBuilder, sourceInterfacePtrLE);
              deallocate(FL(), globalState, functionState, blockState, thenBuilder,
                  controlBlockPtrLE, sourceMT);
            } else if (dynamic_cast<StructReferend*>(sourceMT->referend) ||
                dynamic_cast<KnownSizeArrayT*>(sourceMT->referend) ||
                dynamic_cast<UnknownSizeArrayT*>(sourceMT->referend)) {
              auto sourceWrapperPtrLE =
                  functionState->defaultRegion->makeWrapperPtr(
                      sourceMT,
                      checkValidReference(FL(), globalState, functionState, thenBuilder, sourceMT,
                          sourceRef));
              auto controlBlockPtrLE = getConcreteControlBlockPtr(globalState, thenBuilder, sourceWrapperPtrLE);
              deallocate(FL(), globalState, functionState, blockState, thenBuilder,
                  controlBlockPtrLE, sourceMT);
            } else {
              assert(false);
            }
          });
      break;
    }
    case RegionOverride::FAST: {
      // Do nothing

      auto controlBlockPtrLE = getConcreteControlBlockPtr(globalState, builder, exprWrapperPtrLE);
      // Free it!
      deallocate(AFL("discardOwningRef"), globalState, functionState, blockState, builder,
          controlBlockPtrLE, sourceMT);
      break;
    }
    case RegionOverride::RESILIENT_V0: {
      // Mutables in resilient mode dont have strong RC, and also, they dont adjust
      // weak RC for owning refs

      auto controlBlockPtrLE = getConcreteControlBlockPtr(globalState, builder, exprWrapperPtrLE);
      // Free it!
      deallocate(AFL("discardOwningRef"), globalState, functionState, blockState, builder,
          controlBlockPtrLE, sourceMT);
      break;
    }
    case RegionOverride::RESILIENT_V1:
    case RegionOverride::RESILIENT_V2: {
      // Mutables in resilient v1+2 dont have strong RC, and also, they dont adjust
      // weak RC for owning refs

      auto controlBlockPtrLE = getConcreteControlBlockPtr(globalState, builder, exprWrapperPtrLE);
      // Free it!
      deallocate(AFL("discardOwningRef"), globalState, functionState, blockState, builder,
          controlBlockPtrLE, sourceMT);
      break;
    }
    default: {
      assert(false);
      break;
    }
  }
}
