#include <stdint.h>
#include "stdlib/IsWindows.h"
#include "stdlib/System.h"

int8_t stdlib_IsWindows(void) {
#ifdef _WIN32
  return 1;
#else
  return 0;
#endif
}

int8_t stdlib_System(char *command) {
  if (system(command)) {
    return 1;
  }
  else {
    return 0;
  }
}
