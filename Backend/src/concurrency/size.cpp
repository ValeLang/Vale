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

bool ValeConcurrencyStackMeasuringPass::runOnFunction(Function &F) {
  errs() << "ValeConcurrencyStackMeasuringPass: ";
  errs().write_escaped(F.getName()) << '\n';
  return false;
}

ValeConcurrencyStackMeasuringPass::ValeConcurrencyStackMeasuringPass()
: llvm::FunctionPass(ID) {
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

// The above is just for a regular FunctionPass, but other things like the optimizer might mess with
// the generated code and throw our stack size measurements off. We have to make a MachineFunctionPass
// presumably like the one described in https://www.kharghoshal.xyz/blog/writing-machinefunctionpass.

// It might be easiest to fork LLVM itself to do this. Maybe we can dynamically link a library in,
// like something did with -load?

// It'll probably be a good idea to put this behind a flag, so we can either spawn threads or use this
// weird stack thing. And perhaps yet another flag that's halfway between, kind of like the google
// approach.

// Other relevant links:
// - https://internals.rust-lang.org/t/how-to-let-rustc-compile-functions-with-segmented-stack/16380
// - https://lists.llvm.org/pipermail/llvm-dev/2013-September/065333.html
//   > If you want something that includes stack-spill slots and the like, then
//   > you'd need to write a MachineFunction Pass and examine the generated
//   > machine instructions.  Alternatively, there might be a way in a
//   > MachineFunctionPass to get a pointer to a MachineFrame object and to
//   > query its size.
// - https://lists.llvm.org/pipermail/llvm-dev/2015-November/092030.html