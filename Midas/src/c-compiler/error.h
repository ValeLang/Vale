/** Error handling
 * @file
 *
 * This source file is part of the Cone Programming Language C compiler
 * See Copyright Notice in conec.h
*/

#ifndef error_h
#define error_h

#include <iostream>

// Exit error codes
enum class ExitCode : int {
    // Terminating errors
    Success = 0,
//    ExitError,    // Program fails to compile due to caught errors
//    ExitNF,        // Could not find specified source files
//    ExitMem,    // Out of memory
    BadOpts,    // Invalid compiler options
    LlvmSetupFailed,    // Failure to set up LLVM
    VerifyFailed,    // LLVM didn't like the AST we gave it.
};


template<typename... T>
inline void errorExit(ExitCode exitCode, T&&... thingsToPrint);

template<typename First, typename... Rest>
inline void errorExit(ExitCode exitCode, First&& first, Rest&&... rest) {
  std::cerr << std::forward<First>(first);
  errorExit(exitCode, std::forward<Rest>(rest)...);
}

template<>
inline void errorExit<>(ExitCode exitCode) {
  std::cerr << std::endl;
  exit((int)exitCode);
}

#endif
