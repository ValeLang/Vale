#include "../globalstate.h"
#include "expressions/expressions.h"
#include "boundary.h"
#include "../region/iregion.h"

Ref receiveHostObjectIntoVale(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref hostRegionInstance,
    Ref valeRegionInstance,
    Reference* hostRefMT,
    Reference* valeRefMT,
    LLVMValueRef hostRefLE) {
  // - For example, in:
  //     fn fly(ship 'hgm Spaceship) extern;
  //   when we call it with an object from 'hgm, we're not moving/copying
  //   it between regions, but we do need to encrypt it. So, we'll call
  //   encryptAndSendFamiliarReference.
  // - For example, in:
  //     fn fly(ship 'hgm Spaceship) export { ... }
  //   when the outside world calls it with an object from 'hgm, we're not
  //   moving/copying between regions, but we do need to decrypt it. So,
  //   we'll call receiveAndDecryptFamiliarReference.
  // - For example, in:
  //     fn fly(pattern Pattern) extern;
  //   regardless of whether Pattern is a val or inst, we'll be moving/
  //   copying between regions, so we'll call
  //   receiveUnencryptedAlienReference. HOWEVER, we don't yet support
  //   moving instances between regions, so this is only for vals for now.
  if (hostRefMT->ownership == Ownership::SHARE) {
    buildFlare(FL(), globalState, functionState, builder);
    auto hostRef =
        wrap(globalState->getRegion(hostRefMT), hostRefMT, hostRefLE);
    auto objRefAndSizeRef =
        globalState->getRegion(valeRefMT)
            ->receiveUnencryptedAlienReference(
                functionState, builder, hostRegionInstance, valeRegionInstance, hostRefMT, valeRefMT, hostRef);
    // Vale doesn't really care about the size of the thing, only the object.
    return objRefAndSizeRef.first;
  } else {
    if (dynamic_cast<InterfaceKind*>(hostRefMT->kind)) {
      // Interface external handles should be 32 bytes
      assert(LLVMABISizeOfType(globalState->dataLayout, LLVMTypeOf(hostRefLE)) == 32);
    } else {
      // Struct and array external handles should be 24 bytes
      assert(LLVMABISizeOfType(globalState->dataLayout, LLVMTypeOf(hostRefLE)) == 24);
    }
    return globalState->getRegion(valeRefMT)
        ->receiveAndDecryptFamiliarReference(functionState, builder, hostRefMT, hostRefLE);
  }
}

std::pair<LLVMValueRef, LLVMValueRef> sendValeObjectIntoHost(
    GlobalState* globalState,
    FunctionState* functionState,
    LLVMBuilderRef builder,
    Ref valeRegionInstanceRef,
    Ref hostRegionInstanceRef,
    Reference* valeRefMT,
    Reference* hostRefMT,
    Ref valeRef) {
  // - For example, in:
  //     fn fly(ship 'hgm Spaceship) extern;
  //   when we call it with an object from 'hgm, we're not moving/copying
  //   it between regions, but we do need to encrypt it. So, we'll call
  //   encryptAndSendFamiliarReference.
  // - For example, in:
  //     fn fly(ship 'hgm Spaceship) export { ... }
  //   when the outside world calls it with an object from 'hgm, we're not
  //   moving/copying between regions, but we do need to decrypt it. So,
  //   we'll call receiveAndDecryptFamiliarReference.
  // - For example, in:
  //     fn fly(pattern Pattern) extern;
  //   regardless of whether Pattern is a val or inst, we'll be moving/
  //   copying between regions, so we'll call
  //   receiveUnencryptedAlienReference. HOWEVER, we don't yet support
  //   moving instances between regions, so this is only for vals for now.
  if (valeRefMT->ownership == Ownership::SHARE) {
    auto [hostArgRef, sizeRef] =
        globalState->getRegion(hostRefMT)
            ->receiveUnencryptedAlienReference(
                functionState, builder, valeRegionInstanceRef, hostRegionInstanceRef, valeRefMT, hostRefMT, valeRef);
    globalState->getRegion(valeRefMT)
        ->dealias(FL(), functionState, builder, valeRefMT, valeRef);
    auto hostArgLE =
        globalState->getRegion(hostRefMT)
            ->checkValidReference(FL(), functionState, builder, hostRefMT, hostArgRef);
    auto sizeLE =
        globalState->getRegion(hostRefMT)
            ->checkValidReference(FL(), functionState, builder, globalState->metalCache->i32Ref, sizeRef);
    return std::make_pair(hostArgLE, sizeLE);
  } else {
    auto encryptedValeRefLE =
        globalState->getRegion(valeRefMT)
            ->encryptAndSendFamiliarReference(functionState, builder, valeRefMT, valeRef);

//    auto encryptedValeRefLE =
//        globalState->getRegion(valeRefMT)
//            ->checkValidReference(FL(), functionState, builder, valeRefMT, encryptedValeRef);

    int expectedSizeLE = 0;
    if (dynamic_cast<InterfaceKind*>(hostRefMT->kind)) {
      // Interface external handles should be 32 bytes
      expectedSizeLE = 32;
    } else {
      // Struct and array external handles should be 24 bytes
      expectedSizeLE = 24;
    }
    assert(LLVMABISizeOfType(globalState->dataLayout, LLVMTypeOf(encryptedValeRefLE)) == expectedSizeLE);
    auto sizeLE = constI32LE(globalState, expectedSizeLE);

    return std::make_pair(encryptedValeRefLE, sizeLE);
  }
}
