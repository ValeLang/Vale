#ifndef UTILS_STRUCTLT_H
#define UTILS_STRUCTLT_H

#include <llvm-c/Core.h>
#include <llvm/IR/DataLayout.h>
#include <llvm-c/Target.h>
#include <cassert>
#include <string>
#include <vector>
#include <array>
#include <region/common/migration.h>

class GlobalState;

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
    auto resultLE = LLVMBuildStructGEP2(builder, structLT, ptrLE, member, "memberPtr");
    assert(LLVMTypeOf(resultLE) == LLVMPointerType(membersLT[member], 0));
    return resultLE;
  }

  // TODO: Rename to loadMember
  LLVMValueRef getMember(LLVMBuilderRef builder, LLVMValueRef ptrLE, EnumType member, const std::string& name = "member") const {
    return LLVMBuildLoad2(
        builder, membersLT[member], getMemberPtr(builder, ptrLE, member), name.c_str());
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

LLVMValueRef buildCompressStructInner(
    GlobalState* globalState,
    const std::vector<LLVMTypeRef>& membersLT,
    LLVMBuilderRef builder,
    const std::vector<LLVMValueRef>& membersLE);

template<int NumMembers, typename EnumType>
inline LLVMValueRef buildCompressStruct(
    GlobalState* globalState,
    const StructLT<NumMembers, EnumType>& structLT,
    LLVMBuilderRef builder,
    LLVMValueRef structLE) {
  const auto& membersArrLT = structLT.getMembersLT();
  auto membersLT = std::vector<LLVMTypeRef>(membersArrLT.begin(), membersArrLT.end());
  auto membersArrLE = structLT.explode(builder, structLE);
  auto membersLE = std::vector<LLVMValueRef>(membersArrLE.begin(), membersArrLE.end());
  return buildCompressStructInner(globalState, membersLT, builder, membersLE);
}

std::vector<LLVMValueRef> buildDecompressStructInner(
    GlobalState* globalState,
    const std::vector<LLVMTypeRef>& membersLT,
    LLVMBuilderRef builder,
    LLVMValueRef bigIntLE);

template<int NumMembers, typename EnumType>
inline LLVMValueRef buildDecompressStruct(
    GlobalState* globalState,
    const StructLT<NumMembers, EnumType>& structLT,
    LLVMBuilderRef builder,
    LLVMValueRef bigIntLE) {
  auto membersLT = std::vector<LLVMTypeRef>(structLT.getMembersLT().begin(), structLT.getMembersLT().end());
  auto membersLE = buildDecompressStructInner(globalState, membersLT, builder, bigIntLE);
  return structLT.implode(builder, membersLE);
}

#endif //UTILS_STRUCTLT_H
