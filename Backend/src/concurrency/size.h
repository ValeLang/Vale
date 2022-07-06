#ifndef CONCURRENCY_SIZE_H_
#define CONCURRENCY_SIZE_H_

#include "llvm/Pass.h"
#include "llvm/IR/Function.h"
#include "llvm/CodeGen/MachineFunctionPass.h"
#include "llvm/Support/raw_ostream.h"

namespace llvm {

struct ValeConcurrencyStackMeasuringPass : public llvm::FunctionPass {
  static char ID;
  ValeConcurrencyStackMeasuringPass();

  llvm::StringRef getPassName() const override;

  bool runOnFunction(llvm::Function &F) override;
}; // end of struct ValeConcurrencyStackMeasuringPass

void AddStackSizePass(LLVMModuleRef moduleRef, LLVMPassManagerRef passManager);

}

#endif
