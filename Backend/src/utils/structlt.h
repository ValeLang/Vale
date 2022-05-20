#ifndef UTILS_STRUCTLT_H
#define UTILS_STRUCTLT_H

#include <llvm-c/Core.h>
#include <cassert>
#include <string>
#include <vector>
#include <array>

template<int NumMembers, typename EnumType>
struct StructLT {
public:
  StructLT(LLVMContextRef context, const std::string& name, std::array<LLVMTypeRef, NumMembers> membersLT_) :
      membersLT(std::move(membersLT_)) {
    for (int i = 0; i < membersLT.size(); i++) {
      assert(LLVMTypeIsSized(membersLT[i]));
    }
    structLT = LLVMStructCreateNamed(context, name.c_str()),
        LLVMStructSetBody(structLT, membersLT.data(), membersLT.size(), false);
    assert(LLVMTypeIsSized(structLT));
  }

  // TODO: Rename to loadMemberPtr
  LLVMValueRef getMemberPtr(LLVMBuilderRef builder, LLVMValueRef ptrLE, EnumType member) const {
    assert(LLVMTypeOf(ptrLE) == LLVMPointerType(structLT, 0));
    return LLVMBuildStructGEP(builder, ptrLE, member, "memberPtr");
  }

  // TODO: Rename to loadMember
  LLVMValueRef getMember(LLVMBuilderRef builder, LLVMValueRef ptrLE, EnumType member, const std::string& name = "member") const {
    return LLVMBuildLoad(builder, getMemberPtr(builder, ptrLE, member), name.c_str());
  }

  LLVMValueRef extractMember(LLVMBuilderRef builder, LLVMValueRef structLE, EnumType member) const {
    assert(LLVMTypeOf(structLE) == structLT);
    return LLVMBuildExtractValue(builder, structLE, (unsigned int)member, "structval");
  }

  LLVMValueRef implode(LLVMBuilderRef builder, const std::vector<LLVMValueRef>& values) const {
    assert(values.size() == membersLT.size());
    LLVMValueRef result = LLVMGetUndef(structLT);
    for (int i = 0; i < values.size(); i++) {
      assert(LLVMTypeOf(values[i]) == membersLT[i]);
      result = LLVMBuildInsertValue(builder, result, values[i], i, "structval");
    }
    return result;
  }

  std::array<LLVMValueRef, NumMembers> explode(LLVMBuilderRef builder, LLVMValueRef structLE) const {
    assert(LLVMTypeOf(structLE) == structLT);
    std::array<LLVMValueRef, NumMembers> result;
    for (int i = 0; i < NumMembers; i++) {
      result[i] = LLVMBuildExtractValue(builder, structLE, i, "structval");
    }
    return result;
  }

  auto explodeTup(LLVMBuilderRef builder, LLVMValueRef structLE) const {
    return std::tuple_cat(explode(builder, structLE));
  }

  LLVMTypeRef getStructLT() const { return structLT; }
  const std::array<LLVMTypeRef, NumMembers>& getMembersLT() const { return membersLT; }

private:
  std::array<LLVMTypeRef, NumMembers> membersLT;
  LLVMTypeRef structLT;
};

template<int NumMembers, typename EnumType>
struct StructBuilderLT {
public:
  StructBuilderLT(StructLT<NumMembers, EnumType>* structLT_) :
    structLT(structLT_) {
    resultLE = LLVMGetUndef(structLT->getStructLT());
    std::fill(std::begin(initialized), std::end(initialized), false);
  }

  void insertMember(LLVMBuilderRef builder, EnumType member, LLVMValueRef memberLE) {
    assert(resultLE);
    assert(!initialized[(int)member]);
    initialized[(int)member] = true;
    resultLE = LLVMBuildInsertValue(builder, resultLE, memberLE, (unsigned int)member, "structval");
  }

  LLVMValueRef build() {
    for (int i = 0; i < NumMembers; i++) {
      assert(initialized[i]);
    }
    auto z = resultLE;
    resultLE = nullptr;
    return z;
  }

private:
  std::array<bool, NumMembers> initialized;
  LLVMValueRef resultLE;
  StructLT<NumMembers, EnumType>* structLT;
};

template<int NumMembers, typename EnumType>
inline LLVMValueRef buildCompressStruct(
    LLVMContextRef context,
    LLVMTargetDataRef dataLayout,
    const StructLT<NumMembers, EnumType>& structLT,
    LLVMValueRef structLE,
    LLVMBuilderRef builder) {
  auto int64LT = LLVMInt64TypeInContext(context);
  int totalBits = 0;
  for (auto memberLT : structLT.getMembersLT()) {
    assert(LLVMGetTypeKind(memberLT) == LLVMIntegerTypeKind);
    totalBits += LLVMSizeOfTypeInBits(dataLayout, memberLT);
  }
  auto bigIntLT = LLVMIntTypeInContext(context, totalBits);
  assert(LLVMSizeOfTypeInBits(dataLayout, bigIntLT) == totalBits);

  auto membersLE = structLT.explode(builder, structLE);

  LLVMValueRef resultLE = LLVMConstInt(bigIntLT, 0, false);
  for (int i = 0; i < structLT.getMembersLT().size(); i++) {
    auto memberLT = structLT.getMembersLT()[i];
    auto memberSmallLE = membersLE[i];
    auto memberBigLE = LLVMBuildZExt(builder, memberSmallLE, bigIntLT, "");
    auto bitsNeeded = LLVMSizeOfTypeInBits(dataLayout, memberLT);
    resultLE = LLVMBuildShl(builder, resultLE, LLVMConstInt(int64LT, bitsNeeded, false), "");
    resultLE = LLVMBuildOr(builder, resultLE, memberBigLE, "");
  }
  return resultLE;
}

template<int NumMembers, typename EnumType>
inline LLVMValueRef buildDecompressStruct(
    LLVMContextRef context,
    LLVMTargetDataRef dataLayout,
    const StructLT<NumMembers, EnumType>& structLT,
    LLVMValueRef bigIntLE,
    LLVMBuilderRef builder) {
  auto int1LT = LLVMInt1TypeInContext(context);
  auto int64LT = LLVMInt64TypeInContext(context);
  int totalBits = 0;
  for (auto memberLT : structLT.getMembersLT()) {
    assert(LLVMGetTypeKind(memberLT) == LLVMIntegerTypeKind);
    totalBits += LLVMSizeOfTypeInBits(dataLayout, memberLT);
  }
  auto bigIntLT = LLVMIntTypeInContext(context, totalBits);
  assert(LLVMSizeOfTypeInBits(dataLayout, bigIntLT) == totalBits);
  assert(bigIntLT == LLVMTypeOf(bigIntLE));

  std::vector<LLVMValueRef> membersLE;

  int bitsSoFar = 0;
  for (int i = 0; i < structLT.getMembersLT().size(); i++) {
    auto memberLT = structLT.getMembersLT()[i];

    auto memberBits = LLVMSizeOfTypeInBits(dataLayout, memberLT);
    int maskBeginBit = totalBits - bitsSoFar - memberBits;
    auto maskOnesLE = LLVMBuildSExt(builder, LLVMConstInt(int1LT, 1, true), memberLT, "");
    auto maskLE = LLVMBuildShl(builder, maskOnesLE, LLVMConstInt(int64LT, maskBeginBit, false), "");
    auto isolatedMemberLE = LLVMBuildAnd(builder, bigIntLE, maskLE, "");
    auto memberLE = LLVMBuildLShr(builder, isolatedMemberLE, LLVMConstInt(int64LT, maskBeginBit, false), "");
    membersLE.push_back(memberLE);
  }
  return structLT.implode(builder, membersLE);
}

#endif //UTILS_STRUCTLT_H
