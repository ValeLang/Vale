#ifndef EXTERNS_H_
#define EXTERNS_H_

class Externs {
public:
  RawFuncPtrLE malloc;
  RawFuncPtrLE free;
  RawFuncPtrLE assert;
  RawFuncPtrLE exit;
  RawFuncPtrLE perror;
  RawFuncPtrLE assertI64Eq;
  RawFuncPtrLE printCStr;
  RawFuncPtrLE printCStrToStderr;
  RawFuncPtrLE getch;
  RawFuncPtrLE printInt;
  RawFuncPtrLE printIntToStderr;
  RawFuncPtrLE strlen;
  RawFuncPtrLE memset;
  RawFuncPtrLE strncpy;
  RawFuncPtrLE strncmp;
  RawFuncPtrLE memcpy;


  RawFuncPtrLE fopen;
  RawFuncPtrLE fclose;
  RawFuncPtrLE fread;
  RawFuncPtrLE fwrite;

//  RawFuncPtrLE initTwinPages;
  RawFuncPtrLE censusContains;
  RawFuncPtrLE censusAdd;
  RawFuncPtrLE censusRemove;

  // https://llvm.org/docs/LangRef.html#llvm-read-register-llvm-read-volatile-register-and-llvm-write-register-intrinsics
  RawFuncPtrLE readRegisterI64Intrinsic;
  RawFuncPtrLE writeRegisterI64Intrinsinc;
  //https://releases.llvm.org/8.0.0/docs/ExceptionHandling.html#llvm-eh-sjlj-setjmp
  RawFuncPtrLE setjmpIntrinsic;
  RawFuncPtrLE longjmpIntrinsic;
  // https://llvm.org/docs/LangRef.html#llvm-stacksave-intrinsic
  RawFuncPtrLE stacksaveIntrinsic;
  RawFuncPtrLE stackrestoreIntrinsic;

  RawFuncPtrLE strHasherCallLF;
  RawFuncPtrLE strEquatorCallLF;
  RawFuncPtrLE int256HasherCallLF;
  RawFuncPtrLE int256EquatorCallLF;

  Externs(LLVMModuleRef mod, LLVMContextRef context);
};

bool includeSizeParam(GlobalState* globalState, Prototype* prototype, int paramIndex);

#endif
