#include <stdint.h>
#include <string.h>

#include "Result.h"

typedef struct ValeStr {
  uint64_t length;
  char chars[0];
} ValeStr;
extern ValeStr* ValeStrFrom(char* source);

extern ResultRef makeResult(ValeStr* a, int64_t b);

ResultRef runExtCommand() {
  return makeResult(ValeStrFrom("hello"), 37);
}
