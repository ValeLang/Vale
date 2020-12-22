#include <stdint.h>

#include "Result.h"

extern ResultRef makeResult(int64_t a, int64_t b);

ResultRef runExtCommand() {
  return makeResult(42, 73);
}
