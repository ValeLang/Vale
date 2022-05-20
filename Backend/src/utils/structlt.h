#ifndef UTILS_STRUCTLT_H
#define UTILS_STRUCTLT_H

#include <llvm-c/Core.h>
#include <cassert>
#include <string>
#include <vector>

template<typename EnumType>
struct StructLT {
public:
  StructLT(LLVMContextRef context, const std::string& name, std::vector<LLVMTypeRef> membersLT_) :
      membersLT(std::move(membersLT_)) {
    for (int i = 0; i < membersLT.size(); i++) {
      assert(LLVMTypeIsSized(membersLT[i]));
    }
    structLT = LLVMStructCreateNamed(context, name.c_str()),
        LLVMStructSetBody(structLT, membersLT.data(), membersLT.size(), false);
    assert(LLVMTypeIsSized(structLT));
  }

  LLVMValueRef getMemberPtr(LLVMBuilderRef builder, LLVMValueRef ptrLE, EnumType member) {
    assert(LLVMTypeOf(ptrLE) == LLVMPointerType(structLT, 0));
    return LLVMBuildStructGEP(builder, ptrLE, member, "memberPtr");
  }

  LLVMValueRef getMember(LLVMBuilderRef builder, LLVMValueRef ptrLE, EnumType member, const std::string& name = "member") {
    return LLVMBuildLoad(builder, getMemberPtr(builder, ptrLE, member), name.c_str());
  }

  LLVMTypeRef getStructLT() { return structLT; }

private:
  std::vector<LLVMTypeRef> membersLT;
  LLVMTypeRef structLT;
};

#endif //UTILS_STRUCTLT_H
