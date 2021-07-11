#ifndef BOUNDARY_H_
#define BOUNDARY_H_

#include "globalstate.h"
#include "boundary.h"

Ref receiveHostObjectIntoVale(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* hostRefMT,
    Reference* valeRefMT,
    LLVMValueRef hostRefLE);

LLVMValueRef sendValeObjectIntoHost(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Reference* valeRefMT,
    Reference* hostRefMT,
    Ref valeRef);

#endif
