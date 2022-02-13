
#include <time.h>
#include "stdlib/UnixTimestamp.h"

extern int64_t stdlib_UnixTimestamp() {
  return (unsigned long)time(NULL);
}
