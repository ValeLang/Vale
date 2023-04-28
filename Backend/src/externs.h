#ifndef EXTERNS_H_
#define EXTERNS_H_

class Externs {
public:
  FuncPtrLE malloc;
  FuncPtrLE free;
  FuncPtrLE assert;
  FuncPtrLE exit;
  FuncPtrLE perror;
  FuncPtrLE assertI64Eq;
  FuncPtrLE printCStr;
  FuncPtrLE printCStrToStderr;
  FuncPtrLE getch;
  FuncPtrLE printInt;
  FuncPtrLE printIntToStderr;
  FuncPtrLE strlen;
  FuncPtrLE memset;
  FuncPtrLE strncpy;
  FuncPtrLE strncmp;
  FuncPtrLE memcpy;


  FuncPtrLE fopen;
  FuncPtrLE fclose;
  FuncPtrLE fread;
  FuncPtrLE fwrite;

//  FuncPtrLE initTwinPages;
  FuncPtrLE censusContains;
  FuncPtrLE censusAdd;
  FuncPtrLE censusRemove;

  // https://llvm.org/docs/LangRef.html#llvm-read-register-llvm-read-volatile-register-and-llvm-write-register-intrinsics
  FuncPtrLE readRegisterI64Intrinsic;
  FuncPtrLE writeRegisterI64Intrinsinc;
  //https://releases.llvm.org/8.0.0/docs/ExceptionHandling.html#llvm-eh-sjlj-setjmp
  FuncPtrLE setjmpIntrinsic;
  FuncPtrLE longjmpIntrinsic;
  // https://llvm.org/docs/LangRef.html#llvm-stacksave-intrinsic
  FuncPtrLE stacksaveIntrinsic;
  FuncPtrLE stackrestoreIntrinsic;

  FuncPtrLE strHasherCallLF;
  FuncPtrLE strEquatorCallLF;
  FuncPtrLE int256HasherCallLF;
  FuncPtrLE int256EquatorCallLF;

  Externs(LLVMModuleRef mod, LLVMContextRef context);
};

bool includeSizeParam(GlobalState* globalState, Prototype* prototype, int paramIndex);

#endif
