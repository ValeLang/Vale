#include "structlt.h"
#include "globalstate.h"
#include "function/expressions/shared/shared.h"

LLVMValueRef buildCompressStructInner(
    GlobalState* globalState,
    const std::vector<LLVMTypeRef>& membersLT,
    LLVMBuilderRef builder,
    const std::vector<LLVMValueRef>& membersLE) {
  int totalBits = 0;
  for (auto memberLT : membersLT) {
    assert(LLVMGetTypeKind(memberLT) == LLVMIntegerTypeKind);
    totalBits += LLVMSizeOfTypeInBits(globalState->dataLayout, memberLT);
  }
  auto bigIntLT = LLVMIntTypeInContext(globalState->context, totalBits);
  assert(LLVMSizeOfTypeInBits(globalState->dataLayout, bigIntLT) == totalBits);

  LLVMValueRef resultLE = LLVMConstInt(bigIntLT, 0, false);
  for (int i = 0; i < membersLT.size(); i++) {
    buildPrint(globalState, builder, "Compressing member ");
    buildPrint(globalState, builder, i);
    buildPrint(globalState, builder, "\n");
    auto memberLT = membersLT[i];
    auto memberSmallLE = membersLE[i];
    auto memberBigLE = LLVMBuildZExt(builder, memberSmallLE, bigIntLT, "");
    auto bitsNeeded = LLVMSizeOfTypeInBits(globalState->dataLayout, memberLT);
    buildPrint(globalState, builder, "memberSmallLE: ");
    buildPrint(globalState, builder, memberSmallLE);
    buildPrint(globalState, builder, " bits: ");
    buildPrint(globalState, builder, bitsNeeded);
    buildPrint(globalState, builder, "\n");
    resultLE = LLVMBuildShl(builder, resultLE, LLVMConstInt(bigIntLT, bitsNeeded, false), "");
    buildPrint(globalState, builder, "Shifted result left by bits\n");
    resultLE = LLVMBuildOr(builder, resultLE, memberBigLE, "");
    buildPrint(globalState, builder, "Or'd memberBigLE into result\n");
  }
  return resultLE;
}


std::vector<LLVMValueRef> buildDecompressStructInner(
    GlobalState* globalState,
    const std::vector<LLVMTypeRef>& membersLT,
    LLVMBuilderRef builder,
    LLVMValueRef bigIntLE) {
  int totalBits = 0;
  for (auto memberLT : membersLT) {
    assert(LLVMGetTypeKind(memberLT) == LLVMIntegerTypeKind);
    totalBits += LLVMSizeOfTypeInBits(globalState->dataLayout, memberLT);
  }
  auto bigIntLT = LLVMIntTypeInContext(globalState->context, totalBits);
  assert(LLVMSizeOfTypeInBits(globalState->dataLayout, bigIntLT) == totalBits);
  assert(bigIntLT == LLVMTypeOf(bigIntLE));

  std::vector<LLVMValueRef> membersLE;

  int bitsSoFar = 0;
  for (int i = 0; i < membersLT.size(); i++) {
    buildPrint(globalState, builder, "Decompressing member ");
    buildPrint(globalState, builder, i);
    buildPrint(globalState, builder, "\n");
    auto memberLT = membersLT[i];

    auto memberBits = LLVMSizeOfTypeInBits(globalState->dataLayout, memberLT);
    buildPrint(globalState, builder, "Member bits: ");
    buildPrint(globalState, builder, memberBits);
    buildPrint(globalState, builder, "\n");
    int maskBeginBit = totalBits - bitsSoFar - memberBits;
    buildPrint(globalState, builder, "Mask begin bit: ");
    buildPrint(globalState, builder, maskBeginBit);
    buildPrint(globalState, builder, "\n");
    auto bigIntShiftedLE = LLVMBuildLShr(builder, bigIntLE, LLVMConstInt(bigIntLT, maskBeginBit, false), "");
    buildPrint(globalState, builder, "Shifted result right by maskBeginBit\n");
    auto onesLE = LLVMConstNot(LLVMConstInt(memberLT, 0, false));
    buildPrint(globalState, builder, "Ones: ");
    buildPrint(globalState, builder, onesLE);
    buildPrint(globalState, builder, "\n");
    auto bigIntMaskedLE = LLVMBuildAnd(builder, bigIntShiftedLE, onesLE, "");
    buildPrint(globalState, builder, "Anded by ones\n");
    auto memberLE = LLVMBuildTrunc(builder, bigIntMaskedLE, memberLT, "");

    buildPrint(globalState, builder, "Member: ");
    buildPrint(globalState, builder, memberLE);
    buildPrint(globalState, builder, "\n");

    membersLE.push_back(memberLE);

    bitsSoFar += memberBits;
  }
  return membersLE;
}
