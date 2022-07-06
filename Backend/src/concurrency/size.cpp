#include "concurrency/size.h"

#include <llvm/IR/PassManager.h>
#include "llvm/Pass.h"
#include "llvm/Support/raw_ostream.h"
#include "llvm/IR/LegacyPassManager.h"
#include "llvm-c/Core.h"
#include "llvm/IR/Module.h"
#include "llvm/PassRegistry.h"
#include "llvm/Passes/PassBuilder.h"


using namespace llvm;

namespace llvm {
  void initializeValeConcurrencyStackMeasuringPassPass(PassRegistry &Registry);
}

char ValeConcurrencyStackMeasuringPass::ID = 0;

llvm::StringRef ValeConcurrencyStackMeasuringPass::getPassName() const {
  return "ValeConcurrencyStackMeasuringPass";
}

bool ValeConcurrencyStackMeasuringPass::runOnMachineFunction(MachineFunction &F) {
  errs() << "ValeConcurrencyStackMeasuringPass: ";
  errs().write_escaped(F.getName()) << '\n';
  return false;
}

ValeConcurrencyStackMeasuringPass::ValeConcurrencyStackMeasuringPass()
: llvm::MachineFunctionPass(ID) {
}

INITIALIZE_PASS(
    ValeConcurrencyStackMeasuringPass,
    "ValeConcurrencyStackMeasuringPass",
    " This pass print the function name, if enable-name-printer option is mentioned with -O2",
    false /* Only looks at CFG */,
    true /* Analysis Pass */)

namespace llvm {
void AddStackSizePass(LLVMModuleRef moduleRef, LLVMPassManagerRef passManagerRef) {
  initializeValeConcurrencyStackMeasuringPassPass(*PassRegistry::getPassRegistry());

  // https://stackoverflow.com/questions/69501538/calling-a-llvm-pass-outside-of-a-pass
  auto passManager = unwrap<llvm::legacy::FunctionPassManager>(passManagerRef);
  passManager->add(new ValeConcurrencyStackMeasuringPass());
}
}

//INITIALIZE_PASS_BEGIN(ValeConcurrencyStackMeasuringPass, "ValeConcurrencyStackMeasuringPass", " This pass print the function name, if enable-name-printer option is mentioned with -O2",
//    false /* Only looks at CFG */,
//    false /* Analysis Pass */)
//// Add pass dependencies here:
//INITIALIZE_PASS_DEPENDENCY(PromoteLegacyPass)
//INITIALIZE_PASS_END(ValeConcurrencyStackMeasuringPass, "ValeConcurrencyStackMeasuringPass", " This pass print the function name, if enable-name-printer option is mentioned with -O2",
//    false /* Only looks at CFG */, false /* Analysis Pass */)

//static RegisterPass<ValeConcurrencyStackMeasuringPass> X(
//    "ValeConcurrencyStackMeasuringPass",
//    "ValeConcurrencyStackMeasuringPass World Pass",
//    false /* Only looks at CFG */,
//    false /* Analysis Pass */);
//
//static llvm::RegisterStandardPasses Y(
//    llvm::PassManagerBuilder::EP_OptimizerLast,
//    [](const llvm::PassManagerBuilder &Builder, llvm::legacy::PassManagerBase &PM) {
//      PM.add(new ValeConcurrencyStackMeasuringPass());
//    });
