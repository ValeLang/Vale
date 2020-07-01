#include <iostream>

#include "translatetype.h"

#include "function/expression.h"
#include "function/expressions/shared/shared.h"

LLVMValueRef translateConstruct(
    GlobalState* globalState,
    LLVMBuilderRef builder,
    StructReferend* structReferend,
    std::vector<LLVMValueRef> membersLE) {

  auto structL = globalState->getStruct(structReferend->fullName);
  auto structM = globalState->program->getStruct(structReferend->fullName);

  switch (structM->mutability) {
    case Mutability::MUTABLE: {
      LLVMValueRef sizeLE =
          LLVMConstInt(
              LLVMInt64Type(),
              LLVMABISizeOfType(globalState->dataLayout, structL),
              false);
      auto structValueBeingInitializedLE =
          LLVMBuildCall(builder, globalState->malloc, &sizeLE, 1, "");
      structValueBeingInitializedLE =
          LLVMBuildBitCast(
              builder,
              structValueBeingInitializedLE,
              LLVMPointerType(structL, 0),
              "newstruct");

      {
        LLVMValueRef newControlBlockLE = LLVMGetUndef(globalState->controlBlockStructL);
        newControlBlockLE =
            LLVMBuildInsertValue(
                builder,
                newControlBlockLE,
                // Start at 1, 0 would mean it's dead.
                LLVMConstInt(LLVMInt64Type(), 1, false),
                0,
                "__crc");
        auto controlBlockPtrLE =
            LLVMBuildStructGEP(
                builder,
                structValueBeingInitializedLE,
                0, // Control block is always the 0th member.
                CONTROL_BLOCK_STRUCT_NAME "_memberPtr");
        LLVMBuildStore(
            builder,
            newControlBlockLE,
            controlBlockPtrLE);
      }

      for (int i = 0; i < membersLE.size(); i++) {
        auto memberName = structM->members[i]->name;
        auto ptrLE =
            LLVMBuildStructGEP(
                builder,
                structValueBeingInitializedLE,
                i + 1, // +1 because the 0th field is the ref counts.
                memberName.c_str());
        LLVMBuildStore(builder, membersLE[i], ptrLE);
      }

      flareRc(globalState, builder, 13370001, structValueBeingInitializedLE);

      return structValueBeingInitializedLE;
    }
    case Mutability::IMMUTABLE: {
      bool inliine = true;//newStruct->resultType->location == INLINE; TODO

      if (inliine) {
        // We always start with an undef, and then fill in its fields one at a
        // time.
        LLVMValueRef structValueBeingInitialized = LLVMGetUndef(structL);
        for (int i = 0; i < membersLE.size(); i++) {
          auto memberName = structM->members[i]->name;
          // Every time we fill in a field, it actually makes a new entire
          // struct value, and gives us a LLVMValueRef for the new value.
          // So, `structValueBeingInitialized` contains the latest one.
          structValueBeingInitialized =
              LLVMBuildInsertValue(
                  builder,
                  structValueBeingInitialized,
                  membersLE[i],
                  i,
                  memberName.c_str());
        }
        return structValueBeingInitialized;
      } else {
        // TODO: implement non-inlined immutable structs
        assert(false);
        return nullptr;
      }
    }
    default:
      assert(false);
      return nullptr;
  }
}
