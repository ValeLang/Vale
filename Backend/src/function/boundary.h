#ifndef BOUNDARY_H_
#define BOUNDARY_H_

#include "../globalstate.h"
#include "boundary.h"

Ref receiveHostObjectIntoVale(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref hostRegionInstance,
    Ref valeRegionInstance,
    Reference* hostRefMT,
    Reference* valeRefMT,
    LLVMValueRef hostRefLE);

// Returns the object and the size.
std::pair<LLVMValueRef, LLVMValueRef> sendValeObjectIntoHost(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref valeRegionInstanceRef,
    Ref hostRegionInstanceRef,
    Reference* valeRefMT,
    Reference* hostRefMT,
    Ref valeRef);

#endif
