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
    Success = 0,
    BadOpts = 1,    // Invalid compiler options
    LlvmSetupFailed = 2,    // Failure to set up LLVM
    VerifyFailed = 3,    // LLVM didn't like the AST we gave it.
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
