#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <setjmp.h>

// void *get_sp(void) {
//     void *sp;
//     __asm__ __volatile__(
// #ifdef __x86_64__
//         "movq %%rsp,%0"
// #elif __i386__
//         "movl %%esp,%0"
// #elif __arm__
//         // sp is an alias for r13
//         "mov %%sp,%0"
// #endif
//         : "=r" (sp)
//         : /* no input */
//     );
//     return sp;
// }

_Thread_local void* current_coroutine_args = NULL;

size_t rotate_left(size_t x, int bits) {
  return (x << bits) | (x >> ((sizeof(size_t) * 8) - bits));
}
size_t rotate_right(size_t x, int bits) {
  return (x >> bits) | (x << ((sizeof(size_t) * 8) - bits));
}

typedef struct SubFuncArgs {
  size_t yield_destination_scrambled;
} SubFuncArgs;

void subFunc() {
  int y = 20;
  printf("y address after switch:  %p\n", &y);

  jmp_buf* yield_destination = (jmp_buf*)(rotate_right(((SubFuncArgs*)current_coroutine_args)->yield_destination_scrambled, 17) ^ 0x1337133713371337);

  printf("Jumping back to old stack! buf at: %p\n", yield_destination);

  longjmp(*yield_destination, 1);

  // Should be impossible to get here
  fprintf(stderr, "Unreachable!\n");
  exit(1);
}

int main() {
  printf("jump buf size: %lu\n", sizeof(jmp_buf));

  int x = 10;
  printf("x address before switch: %p\n", &x);

  char* newStack = (char*)malloc(8 * 1024 * 1024);
  char* coroutine_stack_frame_top = newStack + 8 * 1024 * 1024;
  printf("allocated new stack:     %p to %p\n", newStack, coroutine_stack_frame_top);
  

  jmp_buf yield_destination;


  // current_coroutine_entry = subFunc;
  size_t yield_destination_scrambled =
      rotate_left(((size_t)(&yield_destination) ^ 0x1337133713371337), 17);
  SubFuncArgs args = { yield_destination_scrambled };
  current_coroutine_args = &args;

  printf("About to jump to %p, buf at: %p\n", coroutine_stack_frame_top, &yield_destination);

  register void* old_stack_pointer = NULL;
  __asm__ __volatile__(
    #ifdef __x86_64__
      "movq %%rsp,%[rs]"
    #elif __i386__
      "movl %%esp,%[rs]"
    #elif __arm__
      // sp is an alias for r13
      "mov %%sp,%[rs]"
    #endif
      : [rs] "=r" (old_stack_pointer)
      : /* no input */
  );

  if (setjmp(yield_destination) == 0) {
    // setjmp returns zero when we first just call it to save the location.
    // Later on, someone might jump back into that if-condition (just after the setjmp call)
    // but they'll supply something other than zero, so they wont get inside this block.

    register char* top = coroutine_stack_frame_top;
    register void(*funcToCall)() = subFunc;

    asm volatile(
      #ifdef __x86_64__
        //"mov "
        "mov %[rs], %%rsp \n"
      #elif __i386__
        "mov %[rs], %%esp \n"
      #elif __arm__
        // sp is an alias for r13
        "mov %[rs], %%sp \n"
      #endif
        "call *%[bz] \n"
      : [ rs ] "+r" (top), [ bz ] "+r" (funcToCall) ::
    );
    // Accessing any locals after this point seems to be a big mistake,
    // because with -fomit-frame-pointer on, it will calculate those locals'
    // addresses by offsetting from the stack pointer... which we just messed with.
    // So, don't access any locals here.
    // Don't call any functions, because they could be inlined, and access locals.
    // In fact, don't do anything here.
    // Don't even look over here.
    // Put it all in the assembly above.

    // Unreachable, all coroutines know to longjmp back.
    return 1;
  } else {
    // Someone jumped to the saved location!
    printf("got the longjmp!\n");
  }

  printf("done with program!\n");
}

// This technique comes from Stephen Brennan, at
// https://brennan.io/2020/05/24/userspace-cooperative-multitasking/
// I emailed him to ask a little more about it, part of the convo is below.
//
// Evan:
//
//   I'm wondering, do you have any leads or tips on the best way to implement
//   stack switching like this for a production language? If all else fails,
//   I'll probably dive into golang's codebase and see how much I can harvest,
//   but I'm hoping you might have some ideas.
//
// Stephen:
//
//   Yes, thankfully there is a solution. You should look into the
//   "ucontext.h" API, which I briefly mentioned at the end of the article.
//   See getcontext(3), setcontext(3), makecontext(3), and swapcontext(3).
//   This is a platform-agnostic way for you to store or create execution
//   contexts -- including stacks. It's not a standard C API, but it's in
//   glibc, and there's an implementation of it for non-glibc systems, with
//   support for a variety of architectures:
//   
//   https://github.com/kaniini/libucontext
//   
//   I use the ucontext API for a much nicer version of my scheduler from
//   that blog post:
//   
//   https://git.sr.ht/~brenns10/sc-lwt
//   
//   And in turn, I use that "sc-lwt" library (along with libucontext) as the
//   lightweight threading backend for a chatbot I like to mess around with.
//   So it works pretty well.

