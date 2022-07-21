
# Overview

Three main concurrency mechanisms are used today:

 * OS threads, which is easy but use a lot of memory and incur context-switching cost.
 * async/await, which is lightweight but can cause complexity because of its infectiousness. (Like in C# and Rust)
 * Coroutines, which are easy and lightweight but require GC. (Like in Go and JVM's Loom)

Our goal is to prototype something that has the best of all worlds: lightweight, easy, low memory usage.

This mechanism is called "Vale Virtual Threads" and the full explanation can be found at [VirtualThreads.md](VirtualThreads.md).


This is just a prototype, and we'll be simulating it with an existing native language that supports async, such as Rust or Zig.

This won't involve modifying any compilers. If it works, this will later be added to the Vale compiler (not as part of this project). Long-term, this mechanism could very well be added to other languages.



# Steps

This project would be divided into six steps:

 1. Implement a sample program using async/await and regular OS threads, in Rust or Zig
 1. Calculate stack size
 1. Implement a sample program simulating Vale virtual threads
 1. Benchmark
 1. Write paper!

**Total estimate:**

 * **63-96 hours** for the entire project if stack-size plan A goes well.
 * If we need to use stack-size plan B, add 30-45 hours, total 93-141 hours.
 * If we need to use stack-size plan C, add 15-90 hours, total 108-231 hours.
 * Optionally add 9-90 hours to use cooperative yielding to compete with async/await.

Each step has estimates below.

If we want to go further, we can also add a pooling allocator to bring memory usage down and possibly outperform async/await.

## Step 1: Implement sample program with async/await and regular OS threads

We'll implement a basic concurrent program, which will recursively search a very large directory in the file system, looking for anything which contains `zork`.

It will behave differently depending on the type of file:

 * If it's a .txt file, will do a raw string search for "zork".
 * If it's a .json file, will search all the string values for "zork".
 * If it's a directory, we'll recursively search all its children.

These different behaviors will be run via dynamic dispatch (in other words, virtual calls, runtime polymorphism), because this prototype is meant to show how Vale virtual threads can work even in the presence of dynamic dispatch.

It will use async/await at first.

Then, we'll make another version of the program that uses OS threads and blocking, instead of async/await.

**Estimate:** 6 hours, perhaps 12.


## Step 2: Calculating Stack Size

To make this work, we need to know how much stack space a function will use.

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


Below are some options for determining these sizes.


### Plan A: Use LLC's -stack-size-section

This is the easiest option, we should aim for this.

One can use the `clang -S -emit-llvm myCCode.c` flag to turn myCCode.c file into myCCode.ll, which is the text form of the LLVM intermediate representation.

`llc` then turns that into an actual binary. Usually, `clang` itself invokes `llc`. The `-S -emit-llvm` flags instruct `clang` to *not* run `llc`, and instead give us the .ll file.

`llc` has a flag [-stack-size-section](https://llvm.org/docs/CommandGuide/llc.html) which will output a mapping of function symbol to the stack size. It only [works for ELF](https://reviews.llvm.org/D39788) but that should be sufficient for our prototype.

Someone recently used this flag to [implement a stack usage analysis tool](https://blog.japaric.io/stack-analysis/) that tries to figure out if a program could encounter a stack overflow.

We'll parse that output, and then use it to generate a .c file that defines those constants. We'll use this in one of the later steps.

**Estimate:** 6 hours, perhaps 18 if something goes terribly wrong.

### Plan B: Custom LLVM MachineFunctionPass

[This email thread](https://lists.llvm.org/pipermail/llvm-dev/2013-September/065333.html) says:

> If you want something that includes stack-spill slots and the like, then you'd need to write a MachineFunctionPass and examine the generated machine instructions.  Alternatively, there might be a way in a MachineFunctionPass to get a pointer to a MachineFrame object and to query its size.

A compiler can normally [add a FunctionPass](https://github.com/ValeLang/Vale/pull/517/files#diff-50f983f79bbc423ae8cc3e5056f7bd277a9f2d2ffc7112c9feec3eab87b02cf4), but [apparently not a MachineFunctionPass](https://lists.llvm.org/pipermail/llvm-dev/2015-November/092033.html). It seems we [must modify the LLVM source itself](https://discourse.llvm.org/t/can-a-normal-compiler-include-a-machinefunctionpass/63911/3)). [Kharghoshal](https://www.kharghoshal.xyz/blog/writing-machinefunctionpass) says that we can "hack LLVM’s source to get llc to run your MachineFunctionPass when you invoke it for the architecture you’re working on." and gives some steps on how to do so.

[This email thread](https://lists.llvm.org/pipermail/llvm-dev/2015-November/092030.html) also explains how we can add a MachineFunctionPass.

> In LLVM 3.3, there was a file in the X86 backend that had code to 
schedule all the MachineFunctionPass'es  when the X86 code generator was 
used.  That was in lib/Target/X86/X86TargetMachine.cpp.  You can 
probably find a similar file for LLVM 3.7 for the MIPS backend.

> So, to summarize, you'll add your source file to the MIPS backend, add a 
line somewhere to run your pass when the MIPS code generator is used, 
and then recompile llvm/lib and llvm/tools/llc.

The only question remaining is whether we really can get the stack sizes from a MachineFunctionPass. [MachineFrameInfo.getStackSize](https://llvm.org/doxygen/classllvm_1_1MachineFrameInfo.html#a14c39a24bf6ebbe339ae8a453c7fdd11) looks promising.

**Estimate:** 30 hours, perhaps 45.


### Plan C: Analyze x86 Assembly

We could compile Vale code to a static library, and then open it up and inspect the contained x86 assembly, and see what constants are subtracted to the %rbp register, to get the maximum stack size.


**Estimate:** Not sure. 15 hours if consulting with someone experienced with x86, otherwise 90 hours.


## Step 3: Implement a sample program simulating Vale virtual threads

We'll make some adjustments to the sample program.

 1. If in Rust, stop using trait objects and instead do the dynamic dispatch manually, using an array of function pointers (a very common tactic in C).
 2. Make wrapper functions for all the override functions, like described in [VirtualThreads.md](VirtualThreads.md). Each wrapper function should malloc a new chunk of stack, according to the stack space we figured out in step 3.
 3. Pass a `int64_t isConcurrent` as the first argument of every function. In main, pass in a 1 for this. This should make it call our wrapper functions.
 4. When creating the thread, use [pthread_attr_setstacksize](https://man7.org/linux/man-pages/man3/pthread_attr_setstacksize.3.html), [pthread_attr_setstack](https://docs.oracle.com/cd/E19120-01/open.solaris/816-5137/attrib-95722/index.html), malloc, and PTHREAD_STACK_MIN, to initialize a stack with the correct size.


**Estimate:** 18 hours to understand and implement, perhaps 33.


## Step 4: Benchmark

Run our program and see how it compares to async/await and OS threads.

Prediction: Should be almost as good as async/await, and much better than OS threads.


**Estimate:** 3 hours.


## Step 5: Write paper!

Write a glorious paper!

**Estimate:** 30 hours.


## (Optional) Step 6: Implement sample program with cooperative yielding

Now, we'll use the technique described in [User-space Cooperative Multitasking](https://brennan.io/2020/05/24/userspace-cooperative-multitasking/) to implement cooperative user-space threads.

The hard part (stack switching) is actually already provided in [yielding.c](yielding.c). We'd just need to make it work for the sample program.

**Estimate:** 9 hours in theory, but possibly up to 90 if there are mysterious bugs.

This program allocates 8mb for the stack space. It could be much better if we knew exactly how much to allocate. Luckily, that's what the next step figures out!



