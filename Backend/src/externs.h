#ifndef EXTERNS_H_
#define EXTERNS_H_

class Externs {
public:
  LLVMValueRef malloc = nullptr;
  LLVMValueRef free = nullptr;
  LLVMValueRef assert = nullptr;
  LLVMValueRef exit = nullptr;
  LLVMValueRef perror = nullptr;
  LLVMValueRef assertI64Eq = nullptr;
  LLVMValueRef printCStr = nullptr;
  LLVMValueRef getch = nullptr;
  LLVMValueRef printInt = nullptr;
  LLVMValueRef strlen = nullptr;
  LLVMValueRef memset = nullptr;
  LLVMValueRef strncpy = nullptr;
  LLVMValueRef strncmp = nullptr;
  LLVMValueRef memcpy = nullptr;


  LLVMValueRef fopen = nullptr;
  LLVMValueRef fclose = nullptr;
  LLVMValueRef fread = nullptr;
  LLVMValueRef fwrite = nullptr;

//  LLVMValueRef initTwinPages = nullptr;
  LLVMValueRef censusContains = nullptr;
  LLVMValueRef censusAdd = nullptr;
  LLVMValueRef censusRemove = nullptr;

  // https://llvm.org/docs/LangRef.html#llvm-read-register-llvm-read-volatile-register-and-llvm-write-register-intrinsics
  LLVMValueRef readRegisterI64Intrinsic = nullptr;
  LLVMValueRef writeRegisterI64Intrinsinc = nullptr;
  //https://releases.llvm.org/8.0.0/docs/ExceptionHandling.html#llvm-eh-sjlj-setjmp
  LLVMValueRef setjmpIntrinsic = nullptr;
  LLVMValueRef longjmpIntrinsic = nullptr;
  // https://llvm.org/docs/LangRef.html#llvm-stacksave-intrinsic
  LLVMValueRef stacksaveIntrinsic = nullptr;
  LLVMValueRef stackrestoreIntrinsic = nullptr;

  LLVMValueRef strHasherCallLF = nullptr;
  LLVMValueRef strEquatorCallLF = nullptr;
  LLVMValueRef int256HasherCallLF = nullptr;
  LLVMValueRef int256EquatorCallLF = nullptr;

  Externs(LLVMModuleRef mod, LLVMContextRef context);
};

bool includeSizeParam(GlobalState* globalState, Prototype* prototype, int paramIndex);

#endif
