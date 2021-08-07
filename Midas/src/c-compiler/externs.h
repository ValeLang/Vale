#ifndef EXTERNS_H_
#define EXTERNS_H_

class Externs {
public:
  LLVMValueRef malloc = nullptr;
  LLVMValueRef free = nullptr;
  LLVMValueRef assert = nullptr;
  LLVMValueRef exit = nullptr;
  LLVMValueRef assertI64Eq = nullptr;
  LLVMValueRef printCStr = nullptr;
  LLVMValueRef getch = nullptr;
  LLVMValueRef printInt = nullptr;
  LLVMValueRef strlen = nullptr;
  LLVMValueRef memset = nullptr;
  LLVMValueRef strncpy = nullptr;
  LLVMValueRef memcpy = nullptr;

  LLVMValueRef initTwinPages = nullptr;
  LLVMValueRef censusContains = nullptr;
  LLVMValueRef censusAdd = nullptr;
  LLVMValueRef censusRemove = nullptr;

  Externs(LLVMModuleRef mod, LLVMContextRef context);
};

bool includeSizeParam(GlobalState* globalState, Prototype* prototype, int paramIndex);

#endif
