#include <stdint.h>
#include "stdlib/IsWindows.h"

int8_t stdlib_IsWindows(void) {
#ifdef _WIN32
  return 1;
#else
  return 0;
#endif
}
