#include <stdint.h>
#include <string.h>

#include "Result.h"

extern ResultRef makeResult(ValeStr* a, ValeInt b);

ResultRef runExtCommand() {
  return makeResult(ValeStrFrom("hello"), 37);
}
