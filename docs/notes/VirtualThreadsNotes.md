
# Investigating Calculating Stack Size

To make this work, we need to be able to calculate how much stack space a function will use.

Generally, a function's stack frame will have some stack space for:

 * A pointer to the calling function's code ("return address")
 * Space for each parameter.
 * Space for each local, unless the optimizer takes it out.
 * Space for any temporary locals added by LLVM's stack spilling (if there aren't enough registers on the target machine)
 * Space for any temporary locals that LLVM's optimizer might create.

For each function, we need to add:

 * All of the above
 * The maximum stack space needed for anyone we call, except for recursive and virtual calls.

For example, for this program:

```
func triple(x int) int {
  return x * 3;
}

func factorial(z int) {
  if z < 2 {
    return 1;
  }
  else {
    return z * factorial(z - 1);
  }
}

interface IShip {
  func launch(self &IShip);
}
struct Spaceship { }
impl IShip for Spaceship;
func launch(self &Spaceship) {
  println("Launching!");
}

exported func main() {
  x = 2;
  z = triple(x); // Normal call
  f = factorial(x); // Recursive call
  if f < 10 {
    ship = ^Spaceship(); // Heap allocate a Spaceship
    ship.launch(); // Virtual call
  }
}
```

The stack space requirements for each function are:

 * `triple` requires 16 bytes:
    * 8 bytes for the return address.
    * 8 bytes for the parameter `x`
 * `main` requires `56` bytes:
    * `40` bytes for `main` itself:
       * 8 bytes for the return address.
       * 32 bytes for the variables (8 for `x`, 8 for `z`, 8 for `f`, 8 for `ship`)
    * `16` bytes to fit the call to `triple`.
    * No space needed for the call to `factorial` because it's a recursive call.
    * No space needed for the call to `launch` because it's a virtual call.
 * `factorial` requires 16 bytes:
    * 8 bytes for the return address.
    * 8 bytes for the parameter `z`.
    * No space needed for the call to `factorial` because it's a recursive call.
 * `launch` requires 80 bytes:
    * 8 bytes for the return address.
    * 72 bytes to fit the call to `println`.


So we need something that will tell us this information:
```
triple: 16
main: 56
factorial: 16
launch: 80
```


[This email thread](https://lists.llvm.org/pipermail/llvm-dev/2013-September/065333.html) says:

> If you want something that includes stack-spill slots and the like, then you'd need to write a MachineFunctionPass and examine the generated machine instructions.  Alternatively, there might be a way in a MachineFunctionPass to get a pointer to a MachineFrame object and to query its size.

A compiler itself (or anyone using the LLVM library) [can add a FunctionPass](https://github.com/ValeLang/Vale/pull/517/files#diff-50f983f79bbc423ae8cc3e5056f7bd277a9f2d2ffc7112c9feec3eab87b02cf4), but [apparently not a MachineFunctionPass](https://lists.llvm.org/pipermail/llvm-dev/2015-November/092033.html). [Kharghoshal](https://www.kharghoshal.xyz/blog/writing-machinefunctionpass) says that we can "hack LLVM’s source to get llc to run your MachineFunctionPass when you invoke it for the architecture you’re working on."

I think this means we'll need to fork LLVM so we can make an `llc` binary that includes our MachineFunctionPass. It sounds like this is a pretty common thing to do, and that page explains how.

[This email thread](https://lists.llvm.org/pipermail/llvm-dev/2015-November/092030.html) also explains how we can add a MachineFunctionPass.

> In LLVM 3.3, there was a file in the X86 backend that had code to 
schedule all the MachineFunctionPass'es  when the X86 code generator was 
used.  That was in lib/Target/X86/X86TargetMachine.cpp.  You can 
probably find a similar file for LLVM 3.7 for the MIPS backend.

> So, to summarize, you'll add your source file to the MIPS backend, add a 
line somewhere to run your pass when the MIPS code generator is used, 
and then recompile llvm/lib and llvm/tools/llc.

The only question remaining is whether we really can get the stack sizes from a MachineFunctionPass. [MachineFrameInfo.getStackSize](https://llvm.org/doxygen/classllvm_1_1MachineFrameInfo.html#a14c39a24bf6ebbe339ae8a453c7fdd11) looks promising.






# Other Notes

ask schnatsel: if golang is 2k, and doesnt shrink/release memory, is google's approach basically the same?

could we plug a user allocator into the thing that allocates stacks for a future? or parallel or something.

speaking of which, perhaps future.advance should take an allocator. maybe through the context system? or maybe a thread local?

if we're running a single future, can just be a stack. if running multiple, should be a normal heap with a free list probably. default can just be malloc.


we can do something where if we send a message to a green thread thats waiting on one, we can just execute it directly, no need to go through the scheduler. we'll call it "fast channels" or "channel calling" or "channel skipping"
