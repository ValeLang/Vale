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
  StructLT(LLVMContextRef context, const std::string& name, std::array<LLVMTypeRef, NumMembers> membersLT_, bool packed) :
      membersLT(std::move(membersLT_)) {
    for (int i = 0; i < membersLT.size(); i++) {
      assert(LLVMTypeIsSized(membersLT[i]));
    }
    structLT = LLVMStructCreateNamed(context, name.c_str()),
        LLVMStructSetBody(structLT, membersLT.data(), membersLT.size(), packed);
    assert(LLVMTypeIsSized(structLT));
  }

  // TODO: Rename to loadMemberPtr
  LLVMValueRef getMemberPtr(LLVMBuilderRef builder, LLVMValueRef ptrLE, EnumType member) {
    assert(LLVMTypeOf(ptrLE) == LLVMPointerType(structLT, 0));
    return LLVMBuildStructGEP(builder, ptrLE, member, "memberPtr");
  }

  // TODO: Rename to loadMember
  LLVMValueRef getMember(LLVMBuilderRef builder, LLVMValueRef ptrLE, EnumType member, const std::string& name = "member") {
    return LLVMBuildLoad(builder, getMemberPtr(builder, ptrLE, member), name.c_str());
  }

  LLVMValueRef extractMember(LLVMBuilderRef builder, LLVMValueRef structLE, EnumType member) {
    assert(LLVMTypeOf(structLE) == structLT);
    return LLVMBuildExtractValue(builder, structLE, (unsigned int)member, "structval");
  }

  LLVMValueRef implode(LLVMBuilderRef builder, const std::vector<LLVMValueRef>& values) {
    assert(values.size() == membersLT.size());
    LLVMValueRef result = LLVMGetUndef(structLT);
    for (int i = 0; i < values.size(); i++) {
      assert(LLVMTypeOf(values[i]) == membersLT[i]);
      result = LLVMBuildInsertValue(builder, result, values[i], i, "structval");
    }
    return result;
  }

  std::array<LLVMValueRef, NumMembers> explode(LLVMBuilderRef builder, LLVMValueRef structLE) {
    assert(LLVMTypeOf(structLE) == structLT);
    std::array<LLVMValueRef, NumMembers> result;
    for (int i = 0; i < NumMembers; i++) {
      result[i] = LLVMBuildExtractValue(builder, structLE, i, "structval");
    }
    return result;
  }

  auto explodeTup(LLVMBuilderRef builder, LLVMValueRef structLE) {
    return std::tuple_cat(explode(builder, structLE));
  }

  LLVMTypeRef getStructLT() { return structLT; }

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

#endif //UTILS_STRUCTLT_H
